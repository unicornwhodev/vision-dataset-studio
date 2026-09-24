#!/usr/bin/env python3
"""Run the existing core business tests against an exact minified Release pair.

Uses the synthetic training/tokenizer fixtures already staged on the QA device.
Missing fixtures, skipped tests and incomplete instrumentation remain failures.
No Debug APK, data reset, test substitution or external publication is used.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from run_device_qualification import APP_ID, EXCLUDED, parse_instrumentation
from art_environment import art_crashes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--test-sha256', required=True)
    parser.add_argument('--expected-page-size', choices=['4096', '16384'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--keep-app-visible', action='store_true', help='Foreground QA on OEM devices; never background qualification.')
    parser.add_argument('--prevent-test-process-freezing', action='store_true')
    parser.add_argument('--timeout', type=int, default=900)
    args = parser.parse_args()
    if os.environ.get('VDS_ALLOW_TEST_INSTALL') != '1':
        parser.error('Select an authorized QA device and VDS_ALLOW_TEST_INSTALL=1.')
    args.output.mkdir(parents=True, exist_ok=False)
    adb = ['adb', '-s', args.serial]
    state = dict(outcome='running', suite='minified_release_core', production_qualified=False,
                 started_at=datetime.now(timezone.utc).isoformat(), main_sha256=args.sha256,
                 test_sha256=args.test_sha256, uses_existing_synthetic_fixtures=True,
                 app_kept_visible=args.keep_app_visible,
                 test_process_freezing_prevented=args.prevent_test_process_freezing,
                 excluded_classes=list(EXCLUDED))

    def save():
        (args.output / 'status.json').write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')

    def run(name, *parts):
        result = subprocess.run([*adb, *parts], capture_output=True, timeout=120)
        (args.output / name).write_bytes(result.stdout + result.stderr)
        if result.returncode:
            raise RuntimeError(name + ' failed; see retained output')
        return result.stdout.decode('utf-8', errors='replace')

    save()
    try:
        for apk, sha in ((args.apk, args.sha256), (args.test_apk, args.test_sha256)):
            if hashlib.sha256(apk.read_bytes()).hexdigest() != sha:
                raise RuntimeError('APK hash differs from the selected evidence')
        state['page_size'] = int(run('page-size.txt', 'shell', 'getconf', 'PAGE_SIZE').strip())
        state['abi'] = run('abi.txt', 'shell', 'getprop', 'ro.product.cpu.abi').strip()
        state['android_api'] = run('android-api.txt', 'shell', 'getprop', 'ro.build.version.sdk').strip()
        state['build_fingerprint'] = run('build-fingerprint.txt', 'shell', 'getprop', 'ro.build.fingerprint').strip()
        state['art_crashes_before'] = art_crashes(run('crash-before.txt', 'logcat', '-d', '-b', 'crash'))
        if state['art_crashes_before']:
            raise RuntimeError('ART already crashed in this boot; retain the evidence and use a healthy QA environment.')
        if state['page_size'] != int(args.expected_page_size):
            raise RuntimeError('Unexpected page size')
        for label, apk in (('main', args.apk), ('test', args.test_apk)):
            if not re.search(r'^Success\s*$', run('install-' + label + '.txt', 'install', '--no-streaming', '-r', str(apk)), re.M):
                raise RuntimeError('APK update not confirmed')
        if 'DEBUGGABLE' in run('package.txt', 'shell', 'dumpsys', 'package', APP_ID):
            raise RuntimeError('Main app must be a non-debuggable Release')
        options = ['-e', 'notClass', ','.join(APP_ID + '.' + c for c in EXCLUDED),
                   '-e', 'audit_download_models', 'true', '-e', 'inferenceOnlyAudit', 'true']
        log_path = args.output / 'instrumentation.txt'
        with log_path.open('wb') as log:
            process = subprocess.Popen([*adb, 'shell', 'am', 'instrument', '-w', '-r', *options,
                                        APP_ID + '.test/androidx.test.runner.AndroidJUnitRunner'], stdout=log, stderr=subprocess.STDOUT)
            deadline, seen, foreground_test = time.monotonic() + args.timeout, set(), None
            try:
                while process.poll() is None:
                    if time.monotonic() >= deadline:
                        raise RuntimeError('Instrumentation exceeded its time limit')
                    if args.prevent_test_process_freezing:
                        # pidof returns 1 during the brief interval before the
                        # instrumentation process exists. That is not an ADB failure.
                        probe = subprocess.run([*adb, 'shell', 'pidof', APP_ID], capture_output=True, timeout=10)
                        if probe.returncode not in (0, 1):
                            raise RuntimeError('Could not inspect the test process')
                        pid = probe.stdout.decode().strip()
                        if pid.isdigit() and pid not in seen:
                            if 'Unfreezing process' not in run('unfreeze-' + pid + '.txt', 'shell', 'am', 'unfreeze', '--sticky', pid):
                                raise RuntimeError('Could not confirm the test-process exemption')
                            seen.add(pid)
                    if args.keep_app_visible:
                        progress = log_path.read_text(encoding='utf-8', errors='replace')
                        classes = re.findall(r'INSTRUMENTATION_STATUS: class=(.*)', progress)
                        tests = re.findall(r'INSTRUMENTATION_STATUS: test=(.*)', progress)
                        active = (classes[-1], tests[-1]) if classes and tests else None
                        ui = ('EnglishLocaleComposeTest', 'FunctionalUiAuditTest', 'NativePhotoInferenceUiTest', 'SegmentationCanvasTest', 'StudioComposeV4Test')
                        if active and active != foreground_test and active[0].split('.')[-1] not in ui:
                            run('foreground-' + str(len(seen)) + '-' + str(time.monotonic_ns()) + '.txt',
                                'shell', 'am', 'start', '-f', '0x20000000', '-n', APP_ID + '/.MainActivity')
                            foreground_test = active
                    time.sleep(.5)
            finally:
                if process.poll() is None:
                    run('stop-after-interruption.txt', 'shell', 'am', 'force-stop', APP_ID)
                    process.kill()
        state['tests'] = parse_instrumentation(log_path.read_text(encoding='utf-8', errors='replace'))
        if not state['tests']['complete'] or state['tests']['passed'] != 40:
            raise RuntimeError('The expected 40 core tests did not all pass')
        state['art_crashes_after'] = art_crashes(run('crash-after.txt', 'logcat', '-d', '-b', 'crash'))
        if state['art_crashes_after']:
            raise RuntimeError('ART crashed during qualification; passing app tests cannot qualify this environment.')
        state['outcome'] = 'release_core_suite_passed'
        print('Actual minified Release core: 40/40 passed', flush=True)
    except Exception as error:
        state.update(outcome='failed', error=str(error))
        raise
    finally:
        try:
            if args.prevent_test_process_freezing:
                run('end-test-process-exemption.txt', 'shell', 'am', 'force-stop', APP_ID)
            run('crash-buffer.txt', 'logcat', '-d', '-b', 'crash')
        except Exception as error:
            state['final_capture_error'] = str(error)
        finally:
            partial = args.output / 'instrumentation.txt'
            if partial.exists() and 'tests' not in state:
                state['tests'] = parse_instrumentation(partial.read_text(encoding='utf-8', errors='replace'))
            state['finished_at'] = datetime.now(timezone.utc).isoformat()
            save()


if __name__ == '__main__':
    main()

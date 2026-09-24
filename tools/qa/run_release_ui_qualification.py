#!/usr/bin/env python3
"""Run the independent UI suite against an exact, non-debuggable Release APK.

Never substitutes the Debug core suite for Release UI coverage. Installs only
updates, preserving data; signature conflicts remain failures.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
from run_device_qualification import APP_ID, parse_instrumentation
from art_environment import art_crashes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--qa-apk', type=Path, required=True)
    parser.add_argument('--qa-sha256', required=True)
    parser.add_argument('--expected-page-size', choices=['4096', '16384'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--qa-foreground-service', action='store_true',
                        help='Keep only the independent UI driver alive on OEM devices; never qualifies background work.')
    parser.add_argument('--prevent-test-process-freezing', action='store_true',
                        help='API 34+: exemption for the current test process lifetimes only; recorded explicitly.')
    args = parser.parse_args()
    if os.environ.get('VDS_ALLOW_TEST_INSTALL') != '1':
        parser.error('Select the authorized QA device and set VDS_ALLOW_TEST_INSTALL=1.')
    args.output.mkdir(parents=True, exist_ok=False)
    adb = ['adb', '-s', args.serial]
    state = dict(outcome='running', suite='independent_release_ui',
                 started_at=datetime.now(timezone.utc).isoformat(),
                 main_sha256=args.sha256, qa_sha256=args.qa_sha256,
                 production_qualified=False, debug_core_tests_included=False,
                 qa_foreground_service=args.qa_foreground_service,
                 test_process_freezing_prevented=args.prevent_test_process_freezing)

    def run(name, *parts):
        # Keep partial instrumentation output even if adb stalls or times out.
        with (args.output / name).open('wb') as log:
            if name == 'instrumentation.txt':
                process = subprocess.Popen([*adb, *parts], stdout=log, stderr=subprocess.STDOUT)
                deadline, seen = time.monotonic() + 300, set()
                try:
                    while process.poll() is None:
                        if time.monotonic() >= deadline:
                            raise RuntimeError('Release UI instrumentation exceeded 300 seconds')
                        for package in (APP_ID, APP_ID + '.releaseqa'):
                            pid = subprocess.run([*adb, 'shell', 'pidof', package], capture_output=True,
                                                 text=True, timeout=10).stdout.strip()
                            if not pid.isdigit() or (package, pid) in seen:
                                continue
                            if args.prevent_test_process_freezing:
                                answer = run('unfreeze-' + pid + '.txt', 'shell', 'am', 'unfreeze', '--sticky', pid)
                                if 'Unfreezing process' not in answer:
                                    raise RuntimeError('Test process freezing exemption was not confirmed')
                            if args.qa_foreground_service and package.endswith('.releaseqa'):
                                answer = run('qa-service-' + pid + '.txt', 'shell', 'am', 'start-foreground-service',
                                             '-n', package + '/.UiDriverService')
                                if 'Error' in answer:
                                    raise RuntimeError('Independent UI driver service failed')
                            seen.add((package, pid))
                        time.sleep(.5)
                    result = subprocess.CompletedProcess([*adb, *parts], process.returncode)
                finally:
                    # End only these test-process exemptions; retain data and APKs.
                    for package in (APP_ID + '.releaseqa', APP_ID):
                        subprocess.run([*adb, 'shell', 'am', 'force-stop', package], capture_output=True, timeout=15)
                    if process.poll() is None:
                        process.kill()
            else:
                result = subprocess.run([*adb, *parts], stdout=log, stderr=subprocess.STDOUT, timeout=300)
        output = (args.output / name).read_text(encoding='utf-8', errors='replace')
        if result.returncode:
            raise RuntimeError(name + ' failed')
        return output

    try:
        for apk, expected in ((args.apk, args.sha256), (args.qa_apk, args.qa_sha256)):
            if hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
                raise RuntimeError('APK bytes differ from the selected receipt: ' + str(apk))
        state['page_size'] = run('page-size.txt', 'shell', 'getconf', 'PAGE_SIZE').strip()
        if state['page_size'] != args.expected_page_size:
            raise RuntimeError('Unexpected device page size')
        state['abi'] = run('abi.txt', 'shell', 'getprop', 'ro.product.cpu.abi').strip()
        state['emulator'] = run('emulator.txt', 'shell', 'getprop', 'ro.kernel.qemu').strip() == '1'
        state['build_fingerprint'] = run('build-fingerprint.txt', 'shell', 'getprop', 'ro.build.fingerprint').strip()
        state['art_crashes_before'] = art_crashes(run('crash-before.txt', 'logcat', '-d', '-b', 'crash'))
        if state['art_crashes_before']:
            raise RuntimeError('ART already crashed in this boot; retain the evidence and use a healthy QA environment.')
        for name, apk in (('main', args.apk), ('ui', args.qa_apk)):
            if 'Success' not in run('install-' + name + '.txt', 'install', '--no-streaming', '-r', str(apk)):
                raise RuntimeError('APK update failed: ' + name)
        package = run('package.txt', 'shell', 'dumpsys', 'package', APP_ID)
        if 'DEBUGGABLE' in package:
            raise RuntimeError('Release must not be debuggable')
        log = run('instrumentation.txt', 'shell', 'am', 'instrument', '-w', '-r',
                  APP_ID + '.releaseqa/androidx.test.runner.AndroidJUnitRunner')
        state['tests'] = parse_instrumentation(log)
        if not state['tests']['complete'] or state['tests']['passed'] != 4:
            raise RuntimeError('Release UI suite has failures, skips or incomplete results')
        state['art_crashes_after'] = art_crashes(run('crash-after.txt', 'logcat', '-d', '-b', 'crash'))
        if state['art_crashes_after']:
            raise RuntimeError('ART crashed during qualification; passing UI tests cannot qualify this environment.')
        state['outcome'] = 'release_ui_suite_passed'
        print(json.dumps(state['tests'], indent=2))
    except Exception as error:
        state.update(outcome='failed', error=str(error))
        raise
    finally:
        partial = args.output / 'instrumentation.txt'
        if partial.exists() and 'tests' not in state:
            state['tests'] = parse_instrumentation(partial.read_text(encoding='utf-8', errors='replace'))
        state['finished_at'] = datetime.now(timezone.utc).isoformat()
        (args.output / 'status.json').write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()

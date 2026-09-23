#!/usr/bin/env python3
"""Run the core Android suite on a dedicated device, preserving data and evidence.

Real HF publication, converted/private model suites and physical-device qualification
are separate opt-in activities. This runner never reports them as passed.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid
import time
import xml.etree.ElementTree as ET

from resolve_apks import APP_ID, ROOT, resolve

EXCLUDED = ('HfModelRuntimeTest', 'ConvertedModelQualificationTest',
            'NativePhotoInferenceUiTest', 'HfLivePublicationTest', 'InstalledDataPreservationTest')


def parse_instrumentation(text: str) -> dict:
    """Require terminal results for every discovered test; assumptions are skips."""
    records, current = [], {}
    for line in text.splitlines():
        match = re.match(r'INSTRUMENTATION_STATUS: (class|test|numtests)=(.*)', line)
        if match:
            current[match[1]] = match[2]
        match = re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line)
        if match:
            code = int(match[1])
            if code != 1 and 'test' in current:
                records.append({**current, 'code': code})
            current = {}
    declared = {int(n) for n in re.findall(r'INSTRUMENTATION_STATUS: numtests=(\d+)', text)}
    identities = [(r.get('class'), r['test']) for r in records]
    count = next(iter(declared)) if len(declared) == 1 else None
    passed = sum(r['code'] == 0 for r in records)
    skipped = sum(r['code'] in (-3, -4) for r in records)
    failed = len(records) - passed - skipped
    junit = re.search(r'OK \((\d+) tests?\)', text)
    complete = bool(junit and count and int(junit[1]) == count == passed
                    and not failed and not skipped and len(set(identities)) == count
                    and not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=', text))
    aborted = bool(re.search(r'INSTRUMENTATION_FAILED|shortMsg=', text)) or (count is not None and len(records) < count)
    return dict(declared=count, passed=passed, skipped=skipped, failed=failed, aborted=aborted,
                complete=complete, tests=records)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
    parser.add_argument('--output', type=Path, default=os.environ.get('VDS_EVIDENCE_DIR'))
    parser.add_argument('--training-fixture', type=Path, required=True)
    parser.add_argument('--tokenizer-fixture', type=Path, required=True)
    parser.add_argument('--expected-page-size', choices=['4096', '16384'])
    args = parser.parse_args()
    if not args.serial or os.environ.get('VDS_ALLOW_TEST_INSTALL') != '1':
        parser.error('Select a dedicated ANDROID_SERIAL and set VDS_ALLOW_TEST_INSTALL=1.')
    for folder, name in ((args.training_fixture, 'trainable-vision-fixture.tflite'),
                         (args.tokenizer_fixture, 'tokenizer-reference.json')):
        if not (folder / name).is_file():
            parser.error(f'Missing fixture: {folder / name}')
    out = args.output or ROOT / 'test-results' / ('device-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8])
    out.mkdir(parents=True, exist_ok=False)
    adb = ['adb', '-s', args.serial]
    state = dict(schema=1, serial=args.serial, application_id=APP_ID,
                 started_at=datetime.now(timezone.utc).isoformat(), outcome='running',
                 suite='core', excluded_classes=list(EXCLUDED),
                 production_qualified=False, physical_arm_qualified=False,
                 live_hf_qualified=False, commands={})

    def save():
        (out / 'status.json').write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')

    def run(name, command, *, binary=False, check=True):
        print(f'Running {name}', flush=True)
        with (out / name).open('wb') as log:
            result = subprocess.run(command, stdout=log, stderr=subprocess.PIPE if binary else subprocess.STDOUT)
        state['commands'][name] = result.returncode
        save()
        if check and result.returncode:
            raise RuntimeError(f'{name} failed (exit {result.returncode}).')
        return (out / name).read_text(encoding='utf-8', errors='replace') if not binary else None

    save()
    exit_code = 1
    try:
        app, tests = resolve(ROOT / 'dist/android')
        build = json.loads((app.parent / 'status.json').read_text(encoding='utf-8'))
        state['build'] = build
        if not build.get('all_build_checks_passed'):
            raise RuntimeError('The APKs exist, but their build checks are not qualified.')
        run('device-state.txt', [*adb, 'get-state'])
        for key, prop in [('abi', 'ro.product.cpu.abi'), ('api', 'ro.build.version.sdk'),
                          ('model', 'ro.product.model'), ('emulator', 'ro.kernel.qemu')]:
            state[key] = run(f'device-{key}.txt', [*adb, 'shell', 'getprop', prop]).strip()
        state['page_size'] = run('page-size.txt', [*adb, 'shell', 'getconf', 'PAGE_SIZE']).strip()
        state['expected_page_size'] = args.expected_page_size
        if args.expected_page_size and state['page_size'] != args.expected_page_size:
            raise RuntimeError('Device page size does not match the requested qualification target.')
        # Preserve existing data. A signing conflict deliberately fails; never uninstall.
        for label, apk in [('main', app), ('test', tests)]:
            output = run(f'install-{label}.txt', [*adb, 'install', '--no-streaming', '-r', str(apk)])
            if not re.search(r'^Success\s*$', output, re.M):
                raise RuntimeError(f'{label} installation was not confirmed.')
        package = run('package.txt', [*adb, 'shell', 'dumpsys', 'package', APP_ID])
        package_abi = re.search(r'primaryCpuAbi=(\S+)', package)
        state['package_abi'] = package_abi[1] if package_abi else None
        for name, source in [('training-fixture', args.training_fixture), ('hf-runtime-fixture', args.tokenizer_fixture)]:
            run(f'stage-{name}.txt', [sys.executable, str(ROOT / 'tools/qa/stage_android_fixture.py'),
                                    '--serial', args.serial, '--name', name, '--source', str(source)])
        options = ['-e', 'notClass', ','.join(APP_ID + '.' + c for c in EXCLUDED),
                   '-e', 'audit_download_models', 'true', '-e', 'inferenceOnlyAudit', 'true']
        output = run('instrumentation.txt', [*adb, 'shell', 'am', 'instrument', '-w', '-r',
                     *options, APP_ID + '.test/androidx.test.runner.AndroidJUnitRunner'])
        state['instrumentation'] = parse_instrumentation(output)
        if not state['instrumentation']['complete']:
            raise RuntimeError('Core suite has failures, skipped tests or incomplete results.')
        training = run('training-evidence.json', [*adb, 'exec-out', 'run-as', APP_ID, 'cat',
                       'files/training-fixture/android-training-evidence.json'])
        state['training'] = json.loads(training)
        if str(state['training'].get('page_size')) != state['page_size']:
            raise RuntimeError('Training evidence does not match the measured device page size.')
        workflow = run('workflow-training-evidence.json', [*adb, 'exec-out', 'run-as', APP_ID, 'cat',
                       'files/training-fixture/android-workflow-evidence.json'])
        state['training_continuation'] = json.loads(workflow)
        continuation = state['training_continuation']
        if not (continuation.get('continued_without_activation') and continuation.get('missing_checkpoint_refused')
                and continuation.get('original_sha256_before')
                and continuation['original_sha256_before'] == continuation.get('original_sha256_after')
                and continuation.get('first_final_weights')
                and continuation['first_final_weights'] == continuation.get('second_initial_weights')
                and continuation.get('second_final_weights') != continuation.get('second_initial_weights')):
            raise RuntimeError('Original preservation or learned model continuation evidence is incomplete.')
        run('start.txt', [*adb, 'shell', 'am', 'start', '-W', '-n', APP_ID + '/' + APP_ID + '.MainActivity'])
        # am start -W can finish before the splash screen has left the window.
        for index in range(10):
            run(f'ui-dump-{index}.txt', [*adb, 'shell', 'uiautomator', 'dump', '/sdcard/vds-qa-window.xml'])
            tree = run('start-ui.xml', [*adb, 'shell', 'cat', '/sdcard/vds-qa-window.xml'])
            nodes = ET.fromstring(tree).iter('node')
            if any(n.get('package') == APP_ID and n.get('text') == 'Studio' for n in nodes):
                state['startup_ui_ready'] = True
                break
            time.sleep(0.5)
        else:
            raise RuntimeError('The main application UI did not appear after launch.')
        run('start.png', [*adb, 'exec-out', 'screencap', '-p'], binary=True)
        run('start-meminfo.txt', [*adb, 'shell', 'dumpsys', 'meminfo', APP_ID])
        run('start-gfxinfo.txt', [*adb, 'shell', 'dumpsys', 'gfxinfo', APP_ID])
        state['outcome'] = 'core_suite_passed'
        exit_code = 0
    except Exception as exc:
        state.update(outcome='failed', error=str(exc))
        print(f'FAILED: {exc}', file=sys.stderr)
    finally:
        try:
            run('crash-buffer.txt', [*adb, 'logcat', '-d', '-b', 'crash'], check=False)
        except OSError as exc:
            state['crash_capture_error'] = str(exc)
        state.update(exit_code=exit_code, finished_at=datetime.now(timezone.utc).isoformat())
        save()
        print(f'Evidence: {out}')
    return exit_code


if __name__ == '__main__':
    raise SystemExit(main())

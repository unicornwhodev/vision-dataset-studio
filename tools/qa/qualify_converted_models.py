#!/usr/bin/env python3
"""Stage pinned conversions and run their optimizer only inside a dedicated Android device.

No model repository, credential or weight is embedded in the application or this script.
The model root contains <group>/models/<case> and <group>/verified-inventory.json.
"""
from __future__ import annotations

import argparse
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
import time

PACKAGE = 'com.unicornwhodev.visiondatasetstudio'
RUNNER = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'
TEST = PACKAGE + '.ConvertedModelQualificationTest#selectedConversionInfersTrainsAndRestores'


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model-root', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--case', action='append', default=[], help='group/model directory, repeatable')
    parser.add_argument('--shard-index', type=int, default=0)
    parser.add_argument('--shard-count', type=int, default=1)
    parser.add_argument('--timeout-seconds', type=int, default=1200)
    parser.add_argument('--threads', type=int, default=2, choices=range(1, 9))
    parser.add_argument('--build-receipt', type=Path, default=Path('dist/android/latest.json'))
    args = parser.parse_args()
    if not 0 <= args.shard_index < args.shard_count:
        parser.error('shard-index must be in [0, shard-count)')
    if os.environ.get('VDS_ALLOW_TEST_INSTALL') != '1':
        parser.error('Set VDS_ALLOW_TEST_INSTALL=1 for a dedicated test device')
    args.evidence.mkdir(parents=True, exist_ok=True)
    build = json.loads(args.build_receipt.read_text(encoding='utf-8'))
    if not build.get('all_build_checks_passed'):
        parser.error('Use the verified APKs from a build with all checks passed')
    (args.evidence / 'build-receipt.json').write_text(json.dumps(build, indent=2) + '\n')
    adb = ['adb', '-s', args.serial]
    cases = []
    for inventory in sorted(args.model_root.glob('*/verified-inventory.json')):
        group = inventory.parent.name
        for row in json.loads(inventory.read_text(encoding='utf-8')):
            if not row['verified']:
                raise ValueError('Unverified model inventory')
            path = inventory.parent / 'models' / row['id']
            if args.case and f'{group}/{row["id"]}' not in args.case:
                continue
            size = sum(f.stat().st_size for f in path.glob('*.tflite'))
            cases.append((size, group, row, path))
    if not cases or args.case and len(cases) != len(set(args.case)):
        parser.error('A requested conversion is missing from the verified inventory')
    # Spread large conversions across independent Android instances on the same host.
    cases = sorted(cases, key=lambda c: c[0], reverse=True)[args.shard_index::args.shard_count]
    results = []
    for _, group, row, path in sorted(cases, key=lambda c: c[0]):
        if (args.evidence / 'STOP_AFTER_CURRENT').exists():
            print('Stopped between conversions; completed evidence retained.', flush=True)
            return 2
        selected = f'{group}-{row["id"]}'
        if not re.fullmatch(r'[A-Za-z0-9_.-]+', selected):
            raise ValueError('Invalid case identifier')
        out = args.evidence / selected
        out.mkdir(exist_ok=True)
        remote = f'files/conversion-qualification/{selected}'
        print(json.dumps({'case': selected, 'phase': 'stage'}), flush=True)
        subprocess.run([*adb, 'shell', 'run-as', PACKAGE, 'mkdir', '-p', remote], check=True)
        subprocess.run([*adb, 'shell', 'run-as', PACKAGE, 'rm', '-f', remote + '/android-result.json'], check=True)
        uid = int(subprocess.check_output([*adb, 'shell', 'run-as', PACKAGE, 'id', '-u'], text=True))
        gid = int(subprocess.check_output([*adb, 'shell', 'run-as', PACKAGE, 'id', '-g'], text=True))
        def app_owner(info: tarfile.TarInfo) -> tarfile.TarInfo:
            # API 28 toybox tar unconditionally restores ownership. Keep the
            # app's actual UID/GID rather than trying to chown its files to root.
            info.uid = uid; info.gid = gid; info.uname = ''; info.gname = ''; info.mode = 0o700 if info.isdir() else 0o600
            return info
        # shell v2 drains stdin and waits for tar's exit status; legacy exec-in can
        # close a large transfer early when the writer reaches EOF.
        process = subprocess.Popen([*adb, 'shell', '-T', 'run-as', PACKAGE, 'tar', '-x', '-C', remote], stdin=subprocess.PIPE)
        assert process.stdin is not None
        with tarfile.open(fileobj=process.stdin, mode='w|') as archive:
            for file in sorted(path.rglob('*')):
                if (file.is_file() or file.is_dir()) and not file.is_symlink():
                    archive.add(file, arcname=file.relative_to(path).as_posix(), recursive=False, filter=app_owner)
            for name, value in {'case-id.txt': row['id'], 'revision.txt': row['revision']}.items():
                data = (value + '\n').encode()
                info = tarfile.TarInfo(name); info.size = len(data); info.mode = 0o600
                archive.addfile(app_owner(info), io.BytesIO(data))
        process.stdin.close()
        if process.wait() != 0:
            raise RuntimeError('Android staging failed')
        started = time.monotonic()
        timed_out = False
        print(json.dumps({'case': selected, 'phase': 'android_test'}), flush=True)
        with (out / 'instrumentation.txt').open('w') as log:
            try:
                result = subprocess.run([*adb, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', TEST,
                                         '-e', 'modelCase', selected, '-e', 'modelThreads', str(args.threads), RUNNER], stdout=log, stderr=subprocess.STDOUT,
                                        timeout=args.timeout_seconds)
                code = result.returncode
            except subprocess.TimeoutExpired:
                timed_out = True; code = 124
                subprocess.run([*adb, 'shell', 'am', 'force-stop', PACKAGE], check=True)
        raw = subprocess.run([*adb, 'exec-out', 'run-as', PACKAGE, 'cat', remote + '/android-result.json'],
                             capture_output=True, text=True)
        try:
            android = json.loads(raw.stdout)
        except ValueError:
            android = {'success': False, 'phase': 'no_android_receipt'}
        text = (out / 'instrumentation.txt').read_text(encoding='utf-8')
        passed = (code == 0 and not timed_out and android.get('success') is True
                  and bool(re.search(r'OK \(1 test\)', text))
                  and not re.search(r'INSTRUMENTATION_STATUS_CODE: -[1234]|FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=', text))
        report = {'case': selected, 'revision': row['revision'], 'build_id': build['run_id'], 'serial': args.serial, 'passed': passed, 'timed_out': timed_out,
                  'controller_seconds': round(time.monotonic() - started, 3), 'android': android}
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        with (out / 'crash-log.txt').open('w') as log:
            subprocess.run([*adb, 'logcat', '-d', '-b', 'crash'], stdout=log, stderr=subprocess.STDOUT)
        results.append(report)
        (args.evidence / 'summary.json').write_text(json.dumps(results, indent=2) + '\n')
        print(json.dumps(report), flush=True)
    return 0 if all(r['passed'] for r in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())

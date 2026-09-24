#!/usr/bin/env python3
"""Build the real minified Release and its mapped Android tests in one attempt.

No signing, installation, publication or successful test result is implied.
The source manifest and both artifact hashes remain attached to the attempt.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from build_android import atomic_json, digest, source_manifest, verify_local_flex
from check_graphics_runtime import verify_graphics
from check_litert_runtime import verify_litert


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], required=True)
    args = parser.parse_args()
    run = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8]
    out = ROOT / 'dist/release-tests/runs' / run
    out.mkdir(parents=True, exist_ok=False)
    state = dict(outcome='running', abi=args.abi, tests_executed=False,
                 production_qualified=False, recipe_sha256=digest(Path(__file__)))
    atomic_json(out / 'status.json', state)
    try:
        state['flex_runtime'] = verify_local_flex(ROOT)
        state['graphics_runtime'] = verify_graphics()
        state['litert_runtime'] = verify_litert()
        before = source_manifest(ROOT)
        atomic_json(out / 'source-manifest.json', before)
        command = [sys.executable, str(ROOT / 'tools/gradle_bootstrap.py'),
                   ':app:assembleRelease', ':app:assembleReleaseAndroidTest',
                   '-PvdsTestBuildType=release', '-PvdsReleaseAbis=' + args.abi,
                   '--console=plain', '--stacktrace']
        with (out / 'build.log').open('wb') as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        state['gradle_exit_code'] = result.returncode
        if result.returncode:
            raise RuntimeError('Release test assembly failed; see the retained build log.')
        if source_manifest(ROOT) != before:
            raise RuntimeError('Compiled sources changed during the build.')
        state['artifacts'] = {}
        for kind, path in {
            'app': 'app/build/outputs/apk/release/app-release-unsigned.apk',
            'tests': 'app/build/outputs/apk/androidTest/release/app-release-androidTest.apk'
        }.items():
            target = out / (kind + '.apk')
            shutil.copyfile(ROOT / path, target)
            state['artifacts'][kind] = dict(file=target.name, sha256=digest(target), bytes=target.stat().st_size)
            if kind == 'app':
                state['packaged_litert'] = verify_litert(target, [args.abi])
        for variant in ('release', 'releaseAndroidTest'):
            shutil.copyfile(ROOT / f'app/build/outputs/mapping/{variant}/mapping.txt', out / (variant + '-mapping.txt'))
        state['outcome'] = 'release_test_apks_built'
    except Exception as error:
        state.update(outcome='failed', error=str(error))
        raise
    finally:
        state['finished_at'] = datetime.now(timezone.utc).isoformat()
        atomic_json(out / 'status.json', state)
        print('Evidence: ' + str(out), flush=True)


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Build an unsigned, optimized candidate with immutable per-attempt evidence.

No signing key is selected and no publication or device qualification is implied.
"""
import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import zipfile
from build_android import ROOT, APP_ID, atomic_json, digest, sdk_dir, sdk_tool, source_manifest, verify_badging, weight_inventory, verify_local_flex
from qa.check_flex_runtime import MACHINES, verify_flex


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64', 'armeabi-v7a', 'x86'], default='arm64-v8a')
    args = parser.parse_args()
    run = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:12]
    out = ROOT / 'dist/distribution/runs' / run
    out.mkdir(parents=True, exist_ok=False)
    state = dict(run_id=run, abi=args.abi, application_id=APP_ID, outcome='running',
                 signed=False, device_qualified=False, production_qualified=False)
    atomic_json(out / 'status.json', state)
    code = 1
    try:
        state['flex_runtime'] = verify_local_flex(ROOT)
        before = source_manifest(ROOT)
        atomic_json(out / 'source-manifest.json', before)
        # Keep old candidates in dist/distribution; avoid unused incremental ZIP
        # payloads when replacing a native library in the next candidate.
        (ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk').unlink(missing_ok=True)
        command = [sys.executable, str(ROOT / 'tools/gradle_bootstrap.py'), ':app:assembleRelease',
                   '-PvdsReleaseAbis=' + args.abi, '--console=plain', '--stacktrace']
        with (out / 'build.log').open('wb') as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        state['gradle_exit_code'] = result.returncode
        if result.returncode:
            raise RuntimeError('Release assembly failed; no previous APK promoted.')
        if source_manifest(ROOT) != before:
            raise RuntimeError('Compiled sources changed during assembly.')
        source = ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
        tools = sdk_dir(ROOT, os.environ) / 'build-tools/36.0.0'
        badging = subprocess.check_output([str(sdk_tool(tools, 'aapt')), 'dump', 'badging', str(source)], text=True, encoding='utf-8')
        (out / 'identity.txt').write_text(badging, encoding='utf-8')
        verify_badging(badging, APP_ID)
        if args.abi in MACHINES:
            alignment = verify_flex(source, [args.abi])
            if alignment['libraries'][args.abi]['sha256'] != state['flex_runtime']['libraries'][args.abi]['sha256']:
                raise RuntimeError('Packaged Flex differs from its native build receipt; refresh the Gradle dependency cache.')
            atomic_json(out / 'flex-16k.json', alignment)
        inventory = weight_inventory(source)
        if inventory['weight_files']:
            raise RuntimeError('Model weights are forbidden in distribution APKs.')
        with zipfile.ZipFile(source) as archive:
            abis = sorted({name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')})
        if abis != [args.abi]:
            raise RuntimeError('Packaged ABIs do not match the requested target.')
        target = out / f'vision-dataset-studio-{args.abi}-unsigned.apk'
        shutil.copyfile(source, target)
        shutil.copyfile(ROOT / 'app/build/outputs/mapping/release/mapping.txt', out / 'mapping.txt')
        state.update(outcome='unsigned_candidate_built', artifact=dict(file=target.name, sha256=digest(target), bytes=target.stat().st_size), contents=inventory)
        code = 0
    except Exception as exc:
        state.update(outcome='failed', error=str(exc))
        print(str(exc), file=sys.stderr)
    finally:
        state.update(exit_code=code, finished_at=datetime.now(timezone.utc).isoformat())
        atomic_json(out / 'status.json', state)
        print(f'Evidence: {out}')
    return code


if __name__ == '__main__':
    raise SystemExit(main())

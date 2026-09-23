#!/usr/bin/env python3
"""Package the recorded pod build without inventing a GitHub CI receipt."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

from build_android import weight_inventory

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'test-results/batch-production'
TESTED_SOURCE = '680a2b78876b1d670671421fd3ef97b8268e22c0'


def git(*args: str) -> bytes:
    return subprocess.check_output(['git', *args], cwd=ROOT)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-dir', type=Path, required=True)
    args = parser.parse_args()
    folder = args.build_dir.resolve()
    if git('status', '--porcelain').strip():
        raise RuntimeError('Commit the reviewed changes before packaging.')
    commit = git('rev-parse', 'HEAD').decode().strip()
    manifest = json.loads((EVIDENCE / 'source-manifest.json').read_text(encoding='utf-8'))
    for name, expected in manifest.items():
        if hashlib.sha256(git('show', f'{TESTED_SOURCE}:{name}')).hexdigest() != expected:
            raise RuntimeError(f'Tested source manifest mismatch: {name}')
    compiled_paths = ['app', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
                      'gradle', 'tools/build_android.py', 'tools/gradle_bootstrap.py']
    if git('diff', '--name-only', TESTED_SOURCE, commit, '--', *compiled_paths).strip():
        raise RuntimeError('Android sources or build settings changed after the tested pod build.')
    receipt = json.loads((folder / 'status.json').read_text(encoding='utf-8'))
    if receipt != json.loads((EVIDENCE / 'final-build-8/status.json').read_text(encoding='utf-8')):
        raise RuntimeError('Build receipt differs from committed qualification evidence.')
    if receipt.get('outcome') != 'build_checks_passed' or not receipt.get('all_build_checks_passed'):
        raise RuntimeError('The pod build did not pass.')
    for name, expected in [('android-batch-8.log', 'OK (14 tests)'), ('android-ui-8.log', 'OK (2 tests)')]:
        if expected not in (EVIDENCE / name).read_text(encoding='utf-8'):
            raise RuntimeError(f'Missing executed Android evidence: {name}')
    apks = []
    for key in ('app', 'tests'):
        info = receipt['artifacts'][key]
        name = info['file']
        if Path(name).name != name:
            raise RuntimeError('Unsafe APK name')
        path = folder / name
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != info['sha256'] or path.stat().st_size != info['bytes']:
            raise RuntimeError(f'APK bytes do not match receipt: {key}')
        apks.append(path)
    if weight_inventory(apks[0])['weight_files']:
        raise RuntimeError('Bundled model weights detected.')
    package = {
        'kind': 'pod-tested-debug-qualification', 'stable_release': False,
        'release_source_commit': commit, 'tested_android_source_commit': TESTED_SOURCE,
        'android_source_tree_unchanged': True, 'source_manifest_files_verified': len(manifest),
        'build_run': receipt['run_id'], 'artifacts': receipt['artifacts'],
        'jvm_tests_passed': 26, 'android_tests_passed': 16,
        'ci_executed': False, 'ci_blocker': 'GitHub Actions account billing lock',
        'oci_package_is_runnable_image': False,
    }
    out = ROOT / 'dist/pod-release'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'PACKAGE.json').write_text(json.dumps(package, indent=2) + '\n')
    dest = out / 'vision-dataset-studio-4.2.0-rc2-qualification.zip'
    with zipfile.ZipFile(dest, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=3) as z:
        z.writestr('PACKAGE.json', json.dumps(package, indent=2) + '\n')
        z.writestr('BUILD_STATUS.json', json.dumps(receipt, indent=2) + '\n')
        for apk in apks:
            z.write(apk, apk.name)
        for name in git('ls-files', '-z').decode().split('\0'):
            if name and (name.startswith(('docs/', 'test-results/batch-production/')) or name in {
                'README.md', 'README.en.md', 'LICENSE', 'NOTICE', 'LICENSING_STATUS.md',
                'KNOWN_LIMITATIONS.md', 'QUALIFICATION_STATUS.json', 'TEST_REPORT.md'}):
                z.write(ROOT / name, name)
    with dest.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    (out / 'SHA256SUMS').write_text(
        f"{receipt['artifacts']['app']['sha256']}  vision-dataset-studio.apk\n"
        f'{digest}  {dest.name}\n')
    print(dest)
    print('SHA-256:', digest)


if __name__ == '__main__':
    main()

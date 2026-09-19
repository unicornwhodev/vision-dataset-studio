#!/usr/bin/env python3
"""Package verified debug APKs for qualification; never publish or sign releases."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile
from qa.resolve_apks import resolve

ROOT = Path(__file__).resolve().parents[1]

def main() -> None:
    base = ROOT / 'dist/android'
    receipt = json.loads((base / 'latest.json').read_text())
    if not receipt.get('all_build_checks_passed') or receipt.get('outcome') != 'build_checks_passed':
        raise RuntimeError('Packaging blocked: the latest Android build checks did not all pass.')
    apks = resolve(base)
    version = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text())[1]
    if not re.fullmatch(r'[0-9A-Za-z.+-]+', version):
        raise RuntimeError('Unsafe version name')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    dirty = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()
    if dirty:
        raise RuntimeError('Packaging blocked: commit the reviewed source and generated schemas first.')
    if receipt.get('github_sha') != commit:
        raise RuntimeError('Packaging blocked: a successful CI receipt for this exact source commit is required.')
    out = ROOT / 'dist/packages'
    out.mkdir(parents=True, exist_ok=True)
    dest = out / f'vision-dataset-studio-{version}-qualification.zip'
    manifest = {
        'version': version, 'source_commit': commit, 'build_run': receipt['run_id'],
        'kind': 'debug-qualification', 'stable_release': False,
        'artifacts': receipt['artifacts'],
        'scope': 'Android build checks passed; device and product qualification are reported separately.',
    }
    with zipfile.ZipFile(dest, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr('PACKAGE.json', json.dumps(manifest, indent=2) + '\n')
        z.writestr('BUILD_STATUS.json', json.dumps(receipt, indent=2) + '\n')
        for apk in apks:
            z.write(apk, apk.name)
            z.write(apk.with_suffix('.apk.sha256'), apk.with_suffix('.apk.sha256').name)
        for name in ['README.md', 'LICENSE', 'NOTICE', 'LICENSING_STATUS.md', 'KNOWN_LIMITATIONS.md',
                     'docs/ROADMAP.md', 'docs/ANDROID_QUALIFICATION.md']:
            z.write(ROOT / name, name)
    digest = hashlib.file_digest(dest.open('rb'), 'sha256').hexdigest()
    dest.with_suffix('.zip.sha256').write_text(f'{digest}  {dest.name}\n')
    print(dest)
    print('SHA-256:', digest)

if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        raise SystemExit(str(exc))

#!/usr/bin/env python3
"""Prepare public qualification assets from a verified build of the CI commit."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

from qa.resolve_apks import resolve

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    receipt = json.loads((ROOT / 'dist/android/latest.json').read_text(encoding='utf-8'))
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if receipt.get('github_sha') != commit or os.environ.get('GITHUB_SHA') != commit:
        raise RuntimeError('Release assets require a CI build of this exact commit.')
    if receipt.get('outcome') != 'build_checks_passed' or not receipt.get('all_build_checks_passed'):
        raise RuntimeError('Release assets require passing build checks.')
    app, _tests = resolve(ROOT / 'dist/android')
    output = ROOT / 'dist/release'
    output.mkdir(parents=True, exist_ok=True)
    dest = output / 'vision-dataset-studio.apk'
    shutil.copyfile(app, dest)
    packages = list((ROOT / 'dist/packages').glob('*-qualification.zip'))
    if len(packages) != 1:
        raise RuntimeError('Expected one verified qualification package.')
    entries = [dest, packages[0]]
    checksums = ''.join(f'{hashlib.file_digest(p.open("rb"), "sha256").hexdigest()}  {p.name}\n' for p in entries)
    (output / 'SHA256SUMS').write_text(checksums)
    tag = os.environ['QUALIFICATION_TAG']
    notes = f'''## Français

Prérelease de qualification, **pas une version stable complète**.
Installer `vision-dataset-studio.apk`. Le ZIP contient les APK application/tests, les reçus et la documentation de qualification.

- Sources : `{commit}` ; build CI : `{receipt['run_id']}`.
- L'image `ghcr.io/unicornwhodev/vision-dataset-studio-build:{tag}` a réellement compilé ces APK et exécuté les contrôles JVM/lint. Son digest figure dans les assets.
- Aucun poids de modèle embarqué. Production par lots, correction humaine, export vérifié, apprentissage Android facultatif avant nettoyage.
- Les preuves Android précédentes couvrent 16 tests API 28 ; elles ne constituent pas une recette instrumentée de ce nouveau build CI. Voir `TEST_REPORT.md`.
- À terminer : conversions HF entraînables de production, masques/RTMDet, agent/workflows généraux, qualification téléphone ARM et HF distant.
- APK Debug : signature de qualification, pas une clé de distribution pérenne. Ne pas désinstaller une application contenant des données pour contourner un conflit de signature.

## English

Qualification prerelease, **not a complete stable release**.
Install `vision-dataset-studio.apk`. The ZIP contains application/test APKs, receipts and qualification documentation.

- Source: `{commit}`; CI build: `{receipt['run_id']}`.
- `ghcr.io/unicornwhodev/vision-dataset-studio-build:{tag}` actually compiled these APKs and ran JVM/lint checks. Its digest is attached.
- No bundled model weights. Batch production, human correction, verified export and optional Android training before cleanup.
- Earlier Android evidence covers 16 API 28 tests; it is not an instrumented run of this new CI build. See `TEST_REPORT.md`.
- Pending: production trainable HF conversions, masks/RTMDet, general agent/workflows, ARM phone and remote HF qualification.
- Debug APK: qualification signature, not a durable distribution key. Do not uninstall an app containing user data to bypass a signature conflict.
'''
    (output / 'RELEASE_NOTES.md').write_text(notes)
    print('Prepared qualification assets for', commit)


if __name__ == '__main__':
    main()

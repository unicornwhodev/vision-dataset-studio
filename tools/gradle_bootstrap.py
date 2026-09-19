#!/usr/bin/env python3
"""Download/check the official pinned Gradle distribution, then execute it.
This is an explicit bootstrap, not a substitute or forged official wrapper JAR.
SDK and JDK are prerequisites. No HF token or application data is used.
"""
from __future__ import annotations
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

VERSION = '9.3.1'
# Official binary distribution digest: https://gradle.org/release-checksums/ (2026-09-18)
DISTRIBUTION_SHA256 = 'b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06'
ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'vds-bootstrap'
DIST = CACHE / f'gradle-{VERSION}'
EXE = DIST / 'bin' / ('gradle.bat' if os.name == 'nt' else 'gradle')

def fetch(url: str, dest: Path) -> None:
    with urllib.request.urlopen(url, timeout=60) as source, dest.open('wb') as out:
        shutil.copyfileobj(source, out, 1024 * 1024)

def main() -> int:
    if not EXE.is_file():
        CACHE.mkdir(parents=True, exist_ok=True)
        base = f'https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip'
        with tempfile.TemporaryDirectory(prefix='download-', dir=CACHE) as temp:
            zip_path = Path(temp) / 'gradle.zip'
            expected = DISTRIBUTION_SHA256
            print(f'Downloading official Gradle {VERSION}; this requires network access.', flush=True)
            fetch(base, zip_path)
            with zip_path.open('rb') as file:
                actual = hashlib.file_digest(file, 'sha256').hexdigest()
            if actual != expected:
                raise RuntimeError('Gradle checksum mismatch; archive rejected.')
            staging = Path(temp) / 'unpacked'
            staging.mkdir()
            with zipfile.ZipFile(zip_path) as archive:
                for entry in archive.infolist():
                    path = (staging / entry.filename).resolve()
                    if not path.is_relative_to(staging.resolve()) or not entry.filename.startswith(f'gradle-{VERSION}/'):
                        raise RuntimeError('Unexpected distribution path; archive rejected.')
                archive.extractall(staging)
            if DIST.exists():
                raise RuntimeError('Incomplete Gradle cache exists; inspect it before retrying. No merge performed.')
            shutil.move(str(staging / f'gradle-{VERSION}'), str(DIST))
        if os.name != 'nt':
            EXE.chmod(0o755)
    return subprocess.call([str(EXE), *sys.argv[1:]], cwd=ROOT)

if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f'Gradle bootstrap failed: {exc}', file=sys.stderr)
        raise SystemExit(1)

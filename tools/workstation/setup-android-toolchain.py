#!/usr/bin/env python3
"""Install checksummed Android build tools under persistent /workspace."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

BASE = Path('/workspace/toolchains')
DOWNLOADS = BASE / 'downloads'
DOWNLOADS.mkdir(parents=True, exist_ok=True)

def get_json(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'VisionDatasetStudio-Setup'})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)

def fetch(url, filename, expected):
    path = DOWNLOADS / filename
    if not path.exists():
        part = path.with_suffix(path.suffix + '.part')
        subprocess.run(['curl', '-fL', '--retry', '3', '--silent', '--show-error', url, '-o', str(part)], check=True)
        part.rename(path)
    actual = hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()
    if actual != expected:
        raise RuntimeError(f'Checksum mismatch: {filename}')
    print(f'Verified {filename}: {actual}', flush=True)
    return path

def unzip(path, dest):
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path) as z:
        for entry in z.infolist():
            target = dest / entry.filename
            if not target.resolve().is_relative_to(dest.resolve()):
                raise ValueError('Archive path escaped destination')
        z.extractall(dest)
        for entry in z.infolist():
            if entry.external_attr >> 16 & 0o111 and not entry.is_dir():
                (dest / entry.filename).chmod(0o755)

manifest = {}
jdk = BASE / 'jdk-21'
if not jdk.exists():
    assets = get_json('https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse')
    item = assets[0]['binary']['package']
    archive = fetch(item['link'], item['name'], item['checksum'])
    staging = BASE / 'jdk-unpack'
    staging.mkdir(exist_ok=True)
    with tarfile.open(archive) as t:
        t.extractall(staging, filter='data')
    extracted = list(staging.iterdir())
    assert len(extracted) == 1
    extracted[0].rename(jdk)
    staging.rmdir()
    manifest['jdk'] = {'version': assets[0]['version']['semver'], 'url': item['link'], 'sha256': item['checksum']}

sdk = BASE / 'android-sdk'
cli = sdk / 'cmdline-tools/latest'
if not cli.exists():
    url = 'https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip'
    checksum = '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
    archive = fetch(url, 'commandlinetools-linux-15859902_latest.zip', checksum)
    staging = BASE / 'android-cli-unpack'
    unzip(archive, staging)
    cli.parent.mkdir(parents=True, exist_ok=True)
    (staging / 'cmdline-tools').rename(cli)
    staging.rmdir()
    manifest['android_cli'] = {'url': url, 'sha256': checksum}

kotlin = BASE / 'kotlin-2.2.10'
if not kotlin.exists():
    release = get_json('https://api.github.com/repos/JetBrains/kotlin/releases/tags/v2.2.10')
    item = next(a for a in release['assets'] if a['name'] == 'kotlin-compiler-2.2.10.zip')
    checksum = item.get('digest', '').removeprefix('sha256:')
    if len(checksum) != 64:
        checksum_asset = next(a for a in release['assets'] if a['name'] == item['name'] + '.sha256')
        with urllib.request.urlopen(checksum_asset['browser_download_url'], timeout=60) as r:
            checksum = r.read().decode().strip().split()[0]
    archive = fetch(item['browser_download_url'], item['name'], checksum)
    staging = BASE / 'kotlin-unpack'
    unzip(archive, staging)
    (staging / 'kotlinc').rename(kotlin)
    staging.rmdir()
    manifest['kotlin'] = {'version': '2.2.10', 'sha256': checksum}

existing = BASE / 'android-toolchain-manifest.json'
previous = json.loads(existing.read_text()) if existing.exists() else {}
existing.write_text(json.dumps(previous | manifest, indent=2) + '\n')
print('Persistent JDK, Android command-line tools and Kotlin installed.', flush=True)

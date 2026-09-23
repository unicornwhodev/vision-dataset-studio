#!/usr/bin/env python3
"""Resolve and hash-check the current build receipt before installing any APK."""
from __future__ import annotations
import hashlib, json, re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
APP_ID = 'com.unicornwhodev.visiondatasetstudio'

def resolve(base: Path) -> tuple[Path, Path]:
    base = base.resolve()
    latest = json.loads((base / 'latest.json').read_text(encoding='utf-8'))
    if latest.get('application_id') != APP_ID:
        raise ValueError('Wrong application identity in receipt.')
    if not latest.get('apk_built') or not latest.get('test_apk_built'):
        raise ValueError('The latest attempt did not produce both APKs.')
    run = latest.get('run', '')
    if not re.fullmatch(r'runs/[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}', run):
        raise ValueError('Invalid run path in receipt.')
    folder = (base / run).resolve()
    if not folder.is_relative_to(base): raise ValueError('Run path outside evidence folder.')
    stored = json.loads((folder / 'status.json').read_text(encoding='utf-8'))
    if any(stored.get(k) != v for k, v in latest.items() if k != 'run'):
        raise ValueError('Latest pointer and saved run status differ.')
    result = []
    for key in ['app', 'tests']:
        item = stored['artifacts'][key]
        name = item['file']
        if Path(name).name != name or not name.endswith('.apk'): raise ValueError('Unsafe APK name.')
        path = (folder / name).resolve()
        if not path.is_relative_to(folder) or not path.is_file(): raise ValueError('APK absent or outside run.')
        with path.open('rb') as file: actual = hashlib.file_digest(file, 'sha256').hexdigest()
        if actual != item['sha256'] or path.stat().st_size != item['bytes']:
            raise ValueError('APK bytes differ from verified build receipt.')
        result.append(path)
    return result[0], result[1]

if __name__ == '__main__':
    try:
        for path in resolve(ROOT / 'dist/android'): print(path)
    except Exception as exc:
        print(f'Installation blocked: {exc}', file=sys.stderr); raise SystemExit(2)

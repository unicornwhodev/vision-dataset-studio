#!/usr/bin/env python3
"""Download immutable HF model fixtures outside the APK and verify every manifest entry.

Uses the existing Hugging Face login. Never accepts or prints credentials on the command line.
This script does not execute model inference, converter source code or an optimizer.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--repo',required=True)
p.add_argument('--revision',required=True,help='Immutable 40-character HF commit SHA')
p.add_argument('--group',required=True)
p.add_argument('--root',type=Path,required=True)
a=p.parse_args()
if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*',a.repo):p.error('Invalid model repository')
if not re.fullmatch(r'[0-9a-f]{40}',a.revision):p.error('Pin an immutable commit SHA')
if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*',a.group):p.error('Invalid group')
root=a.root/a.group
root.mkdir(parents=True,exist_ok=True)
from huggingface_hub import HfApi, snapshot_download
paths=HfApi().list_repo_files(a.repo,repo_type='model',revision=a.revision)
snapshot_download(a.repo,repo_type='model',revision=a.revision,allow_patterns=['models/**'],local_dir=str(root),max_workers=4)
inventory=[]
for name in sorted(paths):
    if not re.fullmatch(r'models/[^/]+/artifact_manifest\.json',name):continue
    manifest=root/name
    entries=json.loads(manifest.read_text(encoding='utf-8'))
    if not isinstance(entries,dict) or not entries:raise ValueError('Empty or invalid artifact manifest')
    for name,meta in entries.items():
        if not isinstance(meta,dict) or not isinstance(meta.get('bytes'),int) or not re.fullmatch(r'[0-9a-f]{64}',str(meta.get('sha256',''))):
            raise ValueError('Invalid manifest entry')
        file=manifest.parent/name
        if not file.resolve().is_relative_to(manifest.parent.resolve()):raise ValueError('Unsafe manifest path')
        if not file.is_file() or file.stat().st_size!=meta['bytes']:raise ValueError(f'Missing or truncated artifact: {name}')
        with file.open('rb') as stream:actual=hashlib.file_digest(stream,'sha256').hexdigest()
        if actual!=meta['sha256']:raise ValueError(f'Artifact hash mismatch: {name}')
    if not list(manifest.parent.glob('*.tflite')):raise ValueError('No LiteRT graph')
    inventory.append(dict(id=manifest.parent.name,revision=a.revision,verified=True))
if not inventory:raise ValueError('No manifested model folders found')
(root/'verified-inventory.json').write_text(json.dumps(inventory,indent=2)+'\n')
print(f'Verified {len(inventory)} model folders at the pinned revision.')

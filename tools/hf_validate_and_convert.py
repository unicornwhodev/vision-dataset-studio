#!/usr/bin/env python3
"""Validate V2 exports and convert their canonical annotations to COCO.
No dataset download, file deletion or inferred dimensions. Python 3.11+; standard library only.
Pointing, caption, tags, grounding and VQA remain in the canonical JSONL; COCO is a box-only projection.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import re
from pathlib import Path
import sys
import tarfile
from typing import Any


def compute_sha256(path: str | Path) -> str:
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def child(root: Path, relative: str) -> Path:
    result = (root / relative).resolve()
    if Path(relative).is_absolute() or not result.is_relative_to(root.resolve()):
        raise ValueError(f'Unsafe export path: {relative}')
    return result


def batch_files(root: Path, filename: str) -> list[Path]:
    # Both historical exports and project-scoped Android exports are canonical V2.
    return sorted(p for p in root.glob(f'batches/*/{filename}')
                  if re.fullmatch(r'(?:p-\d+-)?batch-\d+', p.parent.name)
                  and p.resolve().is_relative_to(root.resolve()))


def read_records(root: Path) -> list[tuple[Path, dict[str, Any]]]:
    paths = batch_files(root, 'annotations.jsonl')
    if not paths:
        raise ValueError('No V2 batch annotations found. Pass the extracted archive root.')
    records, seen = [], set()
    for path in paths:
        with path.open(encoding='utf-8') as source:
            for index, line in enumerate(source, 1):
                if not line.strip():
                    continue
                record = json.loads(line)
                sid = record.get('sample_id')
                if not isinstance(sid, str) or not sid or sid in seen:
                    raise ValueError(f'Missing/duplicate sample ID at {path}:{index}')
                seen.add(sid)
                if record.get('review_status') != 'VALIDATED':
                    raise ValueError(f'{sid}: not explicitly validated')
                media = record['media']
                if not all(isinstance(media.get(k), int) and media[k] > 0 for k in ('width', 'height')):
                    raise ValueError(f'{sid}: missing dimensions; refusing to invent them')
                image = child(path.parent / 'images', media['filename'])
                if not image.is_file() or compute_sha256(image) != media['sha256']:
                    raise ValueError(f'{sid}: missing image or SHA-256 mismatch')
                annotations = record.get('annotations', {})
                for box in annotations.get('boxes', []):
                    values = [box[k] for k in ('xmin', 'ymin', 'xmax', 'ymax')]
                    if not all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) and 0 <= v <= 1 for v in values):
                        raise ValueError(f'{sid}: invalid normalized box')
                    if values[0] >= values[2] or values[1] >= values[3] or not box.get('label') or not box.get('isHumanVerified'):
                        raise ValueError(f'{sid}: degenerate, unlabeled or unreviewed box')
                for point in annotations.get('points', []):
                    if not all(isinstance(point.get(k), (int,float)) and not isinstance(point[k], bool) and math.isfinite(point[k]) and 0 <= point[k] <= 1 for k in ('x','y')):
                        raise ValueError(f'{sid}: invalid normalized point')
                    if not point.get('isHumanVerified'):
                        raise ValueError(f'{sid}: unreviewed point')
                records.append((path.parent, record))
    return records


def validate_batch(batch_dir: str | Path) -> bool:
    root = Path(batch_dir).resolve()
    manifests = batch_files(root, 'manifest.json')
    if not manifests:
        raise ValueError('No V2 manifest found. Legacy exports require their legacy validator.')
    records = read_records(root)
    for path in manifests:
        manifest = json.loads(path.read_text(encoding='utf-8'))
        if manifest.get('schema_version') != 2 or not manifest.get('files'):
            raise ValueError(f'Unsupported or empty manifest: {path}')
        for entry in manifest['files']:
            file = child(root, entry['path'])
            if not file.is_file() or file.stat().st_size != entry['size'] or compute_sha256(file) != entry['sha256']:
                raise ValueError(f'Checksum/length mismatch: {entry["path"]}')
        actual = sum(1 for directory, _ in records if directory == path.parent)
        if actual != manifest['sample_count']:
            raise ValueError('Manifest sample count mismatch')
    for path in root.glob('data/*/*.tar'):
        with tarfile.open(path, 'r') as archive:
            members = archive.getmembers()
            names = [m.name for m in members]
            if len(names) != len(set(names)):
                raise ValueError(f'Duplicate TAR entries in {path}')
            for entry in members:
                child(root, entry.name)
                if not entry.isfile():
                    raise ValueError('Links and non-file TAR entries are not allowed')
    return True


def convert_to_coco(batch_dir: str | Path, output_path: str | Path) -> None:
    root = Path(batch_dir).resolve()
    validate_batch(root)
    records = read_records(root)
    labels = sorted({b['label'] for _, r in records for b in r.get('annotations', {}).get('boxes', [])})
    categories = {label: i+1 for i, label in enumerate(labels)}
    images, boxes = [], []
    for index, (directory, record) in enumerate(records, 1):
        media = record['media']; w, h = media['width'], media['height']
        images.append({'id': index, 'file_name': (directory / 'images' / media['filename']).relative_to(root).as_posix(), 'width': w, 'height': h})
        for box in record.get('annotations', {}).get('boxes', []):
            x,y,bw,bh = box['xmin']*w, box['ymin']*h, (box['xmax']-box['xmin'])*w, (box['ymax']-box['ymin'])*h
            boxes.append({'id': len(boxes)+1, 'image_id': index, 'category_id': categories[box['label']], 'bbox': [x,y,bw,bh], 'area': bw*bh, 'iscrowd': 0})
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'images': images, 'annotations': boxes, 'categories': [{'id': i, 'name': n} for n,i in categories.items()]}, ensure_ascii=False, indent=2), encoding='utf-8')


def main() -> int:
    parser = argparse.ArgumentParser(description='Validate a V2 export; optionally project reviewed boxes to COCO.')
    parser.add_argument('--batch-dir', required=True, help='Root of the extracted V2 archive, containing batches/.')
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--to-coco')
    args = parser.parse_args()
    try:
        validate_batch(args.batch_dir)
        print('PASS manifest, image integrity, reviewed geometry and archive structure.')
        if args.to_coco:
            convert_to_coco(args.batch_dir, args.to_coco)
            print('COCO projection written; the canonical records have not been changed.')
        print('Not checked: copyright, source commit identity, semantic annotation correctness, train/val leakage or model quality.')
        return 0
    except (ValueError, KeyError, OSError, TypeError, tarfile.TarError) as exc:
        print(f'FAIL {exc}', file=sys.stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())

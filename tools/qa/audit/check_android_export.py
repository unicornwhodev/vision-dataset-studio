#!/usr/bin/env python3
"""Audit the exact synthetic ZIP from FeatureImplementationAuditTest.

This is deliberately separate from the shipped validator: capture its failure,
then inspect the actual export independently without renaming/fixing the input.
Exit 1 if the shipped validator rejects the archive, even when integrity passes.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("zip", type=Path)
    parser.add_argument("--result", type=Path, required=True)
    args = parser.parse_args()
    result = {"archive_sha256": hashlib.sha256(args.zip.read_bytes()).hexdigest()}
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(args.zip) as archive:
        root = Path(tmp)
        names = archive.namelist()
        assert len(names) == len(set(names)) == 11
        assert archive.testzip() is None
        for name in names:
            assert (root / name).resolve().is_relative_to(root.resolve())
        archive.extractall(root)
        validator = Path(__file__).resolve().parents[2] / "hf_validate_and_convert.py"
        process = subprocess.run([sys.executable, str(validator), "--batch-dir", str(root), "--validate"], capture_output=True, text=True)
        result["shipped_validator"] = {"passed": process.returncode == 0, "exit_code": process.returncode,
                                       "output": (process.stdout + process.stderr).strip()}
        manifest_path, = root.glob("batches/*/manifest.json")
        manifest = json.loads(manifest_path.read_text())
        assert manifest["schema_version"] == 2 and manifest["sample_count"] == 1
        expected = set()
        for item in manifest["files"]:
            path = (root / item["path"]).resolve()
            assert path.is_relative_to(root.resolve())
            raw = path.read_bytes()
            assert len(raw) == item["size"]
            assert hashlib.sha256(raw).hexdigest() == item["sha256"]
            expected.add(item["path"])
        assert expected == set(names) - {manifest_path.relative_to(root).as_posix()}
        batch = manifest_path.parent
        canonical = json.loads((batch / "annotations.jsonl").read_text())
        assert canonical["sample_id"] == "audit-export" and canonical["review_status"] == "VALIDATED"
        media = canonical["media"]
        pixels = (batch / "images" / media["filename"]).read_bytes()
        assert media["width"] == 160 and media["height"] == 120
        assert hashlib.sha256(pixels).hexdigest() == media["sha256"]
        box, = canonical["annotations"]["boxes"]
        assert box["isHumanVerified"] and box["label"] == "object"
        coco = json.loads((batch / "coco.json").read_text())
        assert (batch / coco["images"][0]["file_name"]).read_bytes() == pixels
        actual = coco["annotations"][0]["bbox"]
        assert all(abs(a - b) < .001 for a, b in zip(actual, [16, 24, 96, 72]))
        yolo = list(map(float, (batch / "labels/audit-export.txt").read_text().split()))
        assert len(yolo) == 5 and all(abs(a-b) < .0001 for a,b in zip(yolo, [0, .4, .5, .6, .6]))
        for filename in ["vqa.jsonl", "captions-tags.jsonl"]:
            rows = [json.loads(line) for line in (batch / filename).read_text().splitlines()]
            assert len(rows) == 1
        tar_path, = root.glob("data/train/*.tar")
        with tarfile.open(tar_path) as tar:
            members = tar.getmembers()
            assert len(members) == len({m.name for m in members})
            assert all(m.isfile() and (root/m.name).resolve().is_relative_to(root.resolve()) for m in members)
            png, = [m for m in members if m.name.endswith(".png")]
            assert tar.extractfile(png).read() == pixels
            record, = [m for m in members if m.name.endswith(".json")]
            assert json.load(tar.extractfile(record)) == canonical
        result["independent_integrity_and_fixture_projection"] = {"passed": True, "zip_entries": len(names),
            "manifest_files": len(expected), "canonical_image_hash": media["sha256"],
            "coco_pixels": True, "yolo_normalized": True, "webdataset_matches_canonical": True}
    args.result.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["shipped_validator"]["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

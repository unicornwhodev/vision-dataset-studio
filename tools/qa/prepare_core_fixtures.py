#!/usr/bin/env python3
"""Prepare public pinned tokenizer references and an untrained training QA graph.

Run in a venv with requirements-core.txt. No private access or host training.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
REVISION = '36026262693de56b2cf45a6337a405297bfcfff6'
REPO = 'Charlbi/Lite_rt_prepared_for_android_dataset_builder'
TOKENIZERS = {
    'tinyclip': '6d9109cc838977f3ca94a379eec36aecc7c807e1785cd729660ca2fc0171fb35',
    'florence2': '9b466914d9e7f9a39936c9bbe2ac28c86cd8b35c83a905699b01f83178f27c51',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/fixtures')
    args = parser.parse_args()
    models = args.output / 'tokenizer-models'
    for family, expected in TOKENIZERS.items():
        path = models / family / 'processor/tokenizer.json'
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.is_file():
            url = f'https://huggingface.co/{REPO}/resolve/{REVISION}/models/{family}/processor/tokenizer.json'
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read(16 * 1024 * 1024)
            if hashlib.sha256(data).hexdigest() != expected:
                raise RuntimeError(f'{family}: tokenizer checksum mismatch')
            path.write_bytes(data)
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise RuntimeError(f'{family}: existing tokenizer checksum mismatch')
    for script, arguments in [
        ('create_training_fixture.py', ['--output', str(args.output / 'training-fixture')]),
        ('create_tokenizer_reference.py', ['--models', str(models), '--output', str(args.output / 'hf-runtime-fixture')]),
    ]:
        subprocess.run([sys.executable, '-X', 'utf8', str(ROOT / 'tools/qa' / script), *arguments], check=True)
    receipt = dict(repository=REPO, revision=REVISION, tokenizers=TOKENIZERS, host_training=False)
    (args.output / 'core-fixture-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()

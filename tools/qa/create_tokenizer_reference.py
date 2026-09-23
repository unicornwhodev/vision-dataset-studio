#!/usr/bin/env python3
"""Generate independent HF tokenizers references from already pinned local artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tokenizers

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--models', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
texts = [
    'a photo of a red rectangle', 'What does the image describe?',
    "A cat, two dogs! 123", "café forêt — été", "a blue square 🔵",
    "  multiple   spaces\nand lines  ", "can't we're I'll it's",
    '<OD>car<loc_10><loc_20><loc_30><loc_40>',
]
result = {'_provenance': {'tokenizers_version': tokenizers.__version__, 'files': {}}}
for family in ('tinyclip', 'florence2'):
    source = a.models / family / 'processor/tokenizer.json'
    dest = a.output / family / 'processor/tokenizer.json'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, dest)
    tokenizer = tokenizers.Tokenizer.from_file(str(source))
    tokenizer.no_padding()
    tokenizer.no_truncation()
    result['_provenance']['files'][family] = hashlib.sha256(source.read_bytes()).hexdigest()
    result[family] = []
    for text in texts:
        ids = tokenizer.encode(text, add_special_tokens=True).ids
        assert len(ids) <= 77
        result[family].append({'text': text, 'ids': ids, 'decoded': tokenizer.decode(ids, skip_special_tokens=False)})
(a.output / 'tokenizer-reference.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Generated 16 encoding references and 8 Florence decoding references.')

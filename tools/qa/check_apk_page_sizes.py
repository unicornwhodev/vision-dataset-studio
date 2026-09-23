#!/usr/bin/env python3
"""Check 64-bit ELF LOAD/RELRO and uncompressed ZIP alignment; not a runtime test.

Rules: https://developer.android.com/guide/practices/page-sizes
Exit 2 means incompatible; rewriting ZIP alignment cannot repair ELF segments.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


def inspect_elf(data):
    if data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected a little-endian 64-bit ELF library.')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    if size < 56 or offset + size * count > len(data):
        raise ValueError('Malformed ELF program headers.')
    segments = []
    for index in range(count):
        kind, flags, pos, address, _, file_size, memory_size, alignment = struct.unpack_from('<IIQQQQQQ', data, offset + size * index)
        if kind in (1, 0x6474e552):
            aligned = (alignment >= 16384 and pos % 16384 == address % 16384) if kind == 1 else (address + memory_size) % 16384 == 0
            segments.append(dict(type='LOAD' if kind == 1 else 'GNU_RELRO', offset=pos, address=address,
                                 memory_size=memory_size, alignment=alignment, aligned_16k=aligned))
    if not any(row['type'] == 'LOAD' for row in segments):
        raise ValueError('ELF library has no LOAD segments.')
    return segments


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    rows = []
    with args.apk.open('rb') as raw, zipfile.ZipFile(args.apk) as archive:
        apk_hash = hashlib.file_digest(raw, 'sha256').hexdigest()
        for item in archive.infolist():
            if not item.filename.endswith('.so') or not item.filename.startswith(('lib/arm64-v8a/', 'lib/x86_64/')):
                continue
            data = archive.read(item)
            raw.seek(item.header_offset + 26)
            name_size, extra_size = struct.unpack('<HH', raw.read(4))
            data_offset = item.header_offset + 30 + name_size + extra_size
            segments = inspect_elf(data)
            rows.append(dict(path=item.filename, sha256=hashlib.sha256(data).hexdigest(), segments=segments,
                             elf_aligned_16k=all(row['aligned_16k'] for row in segments),
                             zip_aligned_16k=item.compress_type != zipfile.ZIP_STORED or data_offset % 16384 == 0))
    result = dict(apk_sha256=apk_hash, native_64bit_libraries=rows, runtime_tested=False,
                  statically_compatible=all(row['elf_aligned_16k'] and row['zip_aligned_16k'] for row in rows))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    failures = [row['path'] for row in rows if not row['elf_aligned_16k'] or not row['zip_aligned_16k']]
    print(f'{len(rows)} native 64-bit libraries; incompatible: {failures}')
    return 0 if result['statically_compatible'] else 2


if __name__ == '__main__':
    raise SystemExit(main())

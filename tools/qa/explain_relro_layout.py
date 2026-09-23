#!/usr/bin/env python3
"""Explain strict RELRO warnings without modifying ELF files or the strict audit.

Reports whether 16K page rounding would cover writable LOAD bytes outside RELRO.
An empty overlap is a layout observation, not device or store certification.
Android linker reference: platform/bionic/linker/linker_phdr.cpp.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


def explain(data):
    if data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected little-endian ELF64.')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    if size < 56 or offset + size * count > len(data):
        raise ValueError('Malformed program headers.')
    loads, relros, observations = [], [], []
    for index in range(count):
        kind, flags, pos, address, _, file_size, memory_size, alignment = struct.unpack_from('<IIQQQQQQ', data, offset + size * index)
        if kind == 1:
            loads.append(dict(address=address, end=address + memory_size, flags=flags, alignment=alignment))
        elif kind == 0x6474e552:
            relros.append((address, address + memory_size))
    for start, end in relros:
        if end % 16384 == 0:
            continue
        lower = start // 16384 * 16384
        upper = (end + 16383) // 16384 * 16384
        overlaps = []
        for load in loads:
            if not load['flags'] & 2:
                continue
            for lo, hi in ((load['address'], min(load['end'], start)), (max(load['address'], end), load['end'])):
                a, b = max(lo, lower), min(hi, upper)
                if b > a:
                    overlaps.append(dict(start=a, end=b))
        observations.append(dict(relro_start=start, relro_end=end, rounded_protection_start=lower,
            rounded_protection_end=upper, load_segments=loads,
            writable_bytes_outside_relro_affected_by_rounding=overlaps, strict_relro_end_alignment=False))
    return observations


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    rows = []
    with zipfile.ZipFile(args.apk) as archive:
        for name in archive.namelist():
            if name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')) and name.endswith('.so'):
                data = archive.read(name)
                for observation in explain(data):
                    rows.append(dict(library=name, sha256=hashlib.sha256(data).hexdigest(), **observation))
    with args.apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(dict(apk_sha256=digest, observations=rows, runtime_tested=False,
        scope='ELF layout explanation only; the strict audit is unchanged'), indent=2) + '\n', encoding='utf-8')
    print(f'{len(rows)} unaligned RELRO endpoints explained; no binary was modified.')


if __name__ == '__main__':
    main()

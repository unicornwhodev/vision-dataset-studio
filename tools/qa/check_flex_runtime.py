#!/usr/bin/env python3
"""Reject a missing or 4 KB Flex runtime before APK installation.

Checks the rebuilt library only. The separate whole-APK page-size audit and
instrumented training/save/restore test remain necessary qualification evidence.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

try:
    from .check_apk_page_sizes import inspect_elf
except ImportError:  # Direct command-line execution.
    from check_apk_page_sizes import inspect_elf

LIBRARY = 'libtensorflowlite_flex_jni.so'
MACHINES = {'arm64-v8a': 183, 'x86_64': 62}
LOCAL_MAVEN = Path('dist/native-flex/maven/org/tensorflow/tensorflow-lite-select-tf-ops/2.16.1-vds16k1')
AAR_NAME = 'tensorflow-lite-select-tf-ops-2.16.1-vds16k1.aar'


def verify_flex(archive_path: Path, required_abis=None) -> dict:
    prefix = 'jni' if archive_path.suffix == '.aar' else 'lib'
    rows = {}
    with zipfile.ZipFile(archive_path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate archive entries are not allowed.')
        packaged = {n.split('/')[1] for n in names if n.startswith(prefix + '/') and n.endswith('.so')}
        expected = set(required_abis) if required_abis is not None else packaged.intersection(MACHINES)
        if not expected:
            raise ValueError('No supported 64-bit Flex ABI to verify.')
        for abi in sorted(expected):
            name = f'{prefix}/{abi}/{LIBRARY}'
            if name not in names:
                raise ValueError(f'Missing Flex runtime: {name}')
            data = archive.read(name)
            segments = inspect_elf(data)
            machine = struct.unpack_from('<H', data, 18)[0]
            if machine != MACHINES[abi]:
                raise ValueError(f'{name}: ELF architecture does not match {abi}.')
            if not any(s['type'] == 'GNU_RELRO' for s in segments):
                raise ValueError(f'{name}: RELRO protection is absent.')
            if not all(s['aligned_16k'] for s in segments):
                raise ValueError(f'{name}: LOAD/RELRO segments are not 16 KB aligned; rebuild Flex.')
            rows[abi] = dict(sha256=hashlib.sha256(data).hexdigest(), bytes=len(data), segments=segments)
    return dict(scope='Flex ELF alignment only', libraries=rows, runtime_tested=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = verify_flex(args.archive, MACHINES if args.archive.suffix == '.aar' else None)
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        parser.exit(2, str(exc) + '\n')
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('Flex 16 KB ELF check passed: ' + ', '.join(result['libraries']))


if __name__ == '__main__':
    main()

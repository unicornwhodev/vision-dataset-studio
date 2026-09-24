#!/usr/bin/env python3
"""Verify the pinned classic LiteRT API/JNI receipt and strict 16 KB alignment."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
try:
    from .check_apk_page_sizes import inspect_elf
except ImportError:
    from check_apk_page_sizes import inspect_elf

ROOT = Path(__file__).resolve().parents[2]
MAVEN = ROOT / 'dist/native-litert/maven/com/unicornwhodev/thirdparty/litert-interpreter/2.2.0-vds16k2'
AAR = MAVEN / 'litert-interpreter-2.2.0-vds16k2.aar'
LIBRARY = 'libtensorflowlite_jni.so'
MACHINES = {'arm64-v8a': 183, 'x86_64': 62, 'armeabi-v7a': 40, 'x86': 3}


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def verify_litert(path=None, required_abis=None):
    if not AAR.is_file() or not (MAVEN / 'build-receipt.json').is_file():
        raise ValueError('Source-built LiteRT is missing; run tools/build_litert_runtime.py under Linux/WSL.')
    state = json.loads((MAVEN / 'build-receipt.json').read_text(encoding='utf-8'))
    pin = json.loads((ROOT / 'config/litert-source.json').read_text(encoding='utf-8'))
    if (state.get('outcome') != 'native_build_passed' or state.get('inputs') != pin
            or state.get('builder_sha256') != digest(ROOT / 'tools/build_litert_runtime.py')
            or digest(ROOT / pin['cpuinfo']['patch']) != pin['cpuinfo']['patch_sha256']
            or state.get('artifact_sha256') != digest(AAR)):
        raise ValueError('LiteRT source/build receipt mismatch.')
    expected = set(required_abis or (MACHINES if path is None else []))
    if expected - MACHINES.keys():
        raise ValueError('Unknown LiteRT ABI requested.')
    found = {}
    with zipfile.ZipFile(path or AAR) as archive:
        for abi, machine in MACHINES.items():
            entries = [p for p in (f'jni/{abi}/{LIBRARY}', f'lib/{abi}/{LIBRARY}') if p in archive.namelist()]
            if not entries:
                continue
            if len(entries) != 1:
                raise ValueError('Ambiguous LiteRT JNI entry: ' + abi)
            data = archive.read(entries[0])
            sha = hashlib.sha256(data).hexdigest()
            if (data[:4] != b'\x7fELF' or int.from_bytes(data[18:20], 'little') != machine
                    or sha != state['libraries'][abi]['sha256']):
                raise ValueError('LiteRT JNI bytes/architecture mismatch: ' + abi)
            segments = inspect_elf(data) if abi in ('arm64-v8a', 'x86_64') else None
            if segments is not None and not all(s['aligned_16k'] for s in segments):
                raise ValueError('LiteRT strict ELF alignment failure: ' + abi)
            found[abi] = dict(sha256=sha, segments=segments)
    if not found or expected - found.keys():
        raise ValueError('Required LiteRT JNI ABI is missing: ' + ','.join(sorted(expected - found.keys())))
    return dict(artifact_sha256=state['artifact_sha256'], source_commit=pin['commit'],
                libraries=found, runtime_tested=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', nargs='?', type=Path)
    parser.add_argument('--abi', action='append', choices=sorted(MACHINES))
    args = parser.parse_args()
    print(json.dumps(verify_litert(args.artifact, args.abi), indent=2))

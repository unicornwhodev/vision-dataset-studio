"""Verify source-built AndroidX JNI bytes and both 16 KB alignment constraints."""
import hashlib
import json
from pathlib import Path
import zipfile
try:
    from .check_apk_page_sizes import inspect_elf
except ImportError:
    from check_apk_page_sizes import inspect_elf

ROOT=Path(__file__).resolve().parents[2]
MAVEN=ROOT/'dist/native-graphics/maven/androidx/graphics/graphics-path/1.0.1-vds16k1'
AAR=MAVEN/'graphics-path-1.0.1-vds16k1.aar'
LIB='libandroidx.graphics.path.so'

def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def verify_graphics(path=None):
    if not AAR.is_file() or not (MAVEN/'build-receipt.json').is_file():
        raise ValueError('The aligned Graphics Path JNI is missing; run tools/build_graphics_path.py under Linux/WSL.')
    state=json.loads((MAVEN/'build-receipt.json').read_text(encoding='utf-8'))
    pin=json.loads((ROOT/'config/graphics-path-source.json').read_text(encoding='utf-8'))
    if (state.get('outcome')!='native_build_passed' or state.get('inputs')!=pin
            or state.get('builder_sha256')!=digest(ROOT/'tools/build_graphics_path.py') or state.get('artifact_sha256')!=digest(AAR)):
        raise ValueError('Graphics Path source/build receipt mismatch.')
    found={}
    with zipfile.ZipFile(path or AAR) as archive:
        for abi in ('arm64-v8a','x86_64'):
            entries=[entry for entry in (f'jni/{abi}/{LIB}',f'lib/{abi}/{LIB}') if entry in archive.namelist()]
            if not entries:continue
            data=archive.read(entries[0]);sha=hashlib.sha256(data).hexdigest();segments=inspect_elf(data)
            if sha!=state['libraries'][abi]['sha256'] or not all(s['aligned_16k'] for s in segments):
                raise ValueError('Graphics Path JNI bytes/alignment mismatch: '+abi)
            found[abi]=dict(sha256=sha,segments=segments)
    if not found or (path is None and len(found)!=2):raise ValueError('Graphics Path 64-bit JNI is missing.')
    return dict(artifact_sha256=state['artifact_sha256'],libraries=found,runtime_tested=False)

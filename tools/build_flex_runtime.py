#!/usr/bin/env python3
"""Build the full TensorFlow 2.16.1 Flex runtime for 16 KB Android pages.

Linux x86_64 (Ubuntu 22.04 tested), including an isolated WSL distribution.
Prerequisites: build-essential, python3-dev, python3-numpy, unzip, zip, curl,
git and ca-certificates. Requires an already accepted Android SDK licence.
No models, private data, SDK licence acceptance or remote publication.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/qa'))
from check_flex_runtime import MACHINES, LIBRARY, verify_flex

VERSION = '2.16.1-vds16k1'
ARTIFACT = 'tensorflow-lite-select-tf-ops'
MAVEN_PATH = Path('org/tensorflow') / ARTIFACT / VERSION
INPUTS = {
    'tensorflow-2.16.1.tar.gz': (
        'https://github.com/tensorflow/tensorflow/archive/refs/tags/v2.16.1.tar.gz',
        'c729e56efc945c6df08efe5c9f5b8b89329c7c91b8f40ad2bb3e13900bd4876d'),
    'android-ndk-r25b-linux.zip': (
        'https://dl.google.com/android/repository/android-ndk-r25b-linux.zip',
        '403ac3e3020dd0db63a848dcaba6ceb2603bf64de90949d5c4361f848e44b005'),
    'bazel-6.5.0-linux-x86_64': (
        'https://github.com/bazelbuild/bazel/releases/download/6.5.0/bazel-6.5.0-linux-x86_64',
        'a40ac69263440761199fcb8da47ad4e3f328cbe79ffbf4ecc14e5ba252857307'),
    ARTIFACT + '-2.16.1.aar': (
        'https://repo.maven.apache.org/maven2/org/tensorflow/tensorflow-lite-select-tf-ops/2.16.1/tensorflow-lite-select-tf-ops-2.16.1.aar',
        '25f8a1861f52203a55c9b7a1003e9ee5114a5a6745d77505d8e253302a6ad175'),
    ARTIFACT + '-2.16.1.pom': (
        'https://repo.maven.apache.org/maven2/org/tensorflow/tensorflow-lite-select-tf-ops/2.16.1/tensorflow-lite-select-tf-ops-2.16.1.pom',
        '7a0888ac4ababeb6046025dddc5ea962320b4de385d3904d551cba42e6b509a7'),
}


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def fetch(directory, name):
    url, expected = INPUTS[name]
    path = directory / name
    if not path.is_file():
        temporary = path.with_suffix(path.suffix + '.partial')
        print('Downloading ' + name, flush=True)
        urllib.request.urlretrieve(url, temporary)
        if digest(temporary) != expected:
            raise ValueError('Download SHA-256 mismatch: ' + name)
        temporary.replace(path)
    if digest(path) != expected:
        raise ValueError('Cached input SHA-256 mismatch: ' + name)
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, required=True, help='Dedicated Linux filesystem path for sources and Bazel cache.')
    parser.add_argument('--downloads', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/native-flex/maven')
    parser.add_argument('--android-sdk-licenses', type=Path, required=True, help='Existing SDK licenses directory; no licence is accepted by this tool.')
    parser.add_argument('--jobs', type=int, default=6)
    args = parser.parse_args()
    if sys.platform != 'linux' or platform.machine() != 'x86_64':
        parser.error('Run this builder on Linux x86_64, or use the documented WSL command.')
    if not (args.android_sdk_licenses / 'android-sdk-license').is_file():
        parser.error('An existing accepted android-sdk-license is required.')
    if args.jobs < 1:
        parser.error('--jobs must be positive.')
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    downloads = (args.downloads or work / 'downloads').resolve()
    downloads.mkdir(parents=True, exist_ok=True)
    output = args.output.resolve()
    run = output.parent / 'runs' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8])
    run.mkdir(parents=True, exist_ok=False)
    state = dict(schema=1, version=VERSION, outcome='running', inputs=INPUTS,
                 operator_selection='full upstream Flex operator set; no model pruning',
                 builder_sha256=digest(__file__), runtime_tested=False, commands={})

    def save():
        (run / 'status.json').write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')

    def command(label, argv, cwd=work):
        print(label, flush=True)
        with (run / (label + '.log')).open('wb') as log:
            result = subprocess.run([str(a) for a in argv], cwd=cwd, stdout=log, stderr=subprocess.STDOUT)
        state['commands'][label] = dict(argv=[str(a) for a in argv], exit_code=result.returncode)
        save()
        if result.returncode:
            raise RuntimeError(f'{label} failed; see {run / (label + ".log")}')

    save()
    try:
        files = {name: fetch(downloads, name) for name in INPUTS}
        source = work / 'tensorflow-2.16.1'
        ndk = work / 'android-ndk-r25b'
        # Re-extract the verified archives, overwriting inputs rather than trusting
        # an edited source tree. Bazel's content cache retains unchanged actions.
        command('extract-source', ['tar', 'xzf', files['tensorflow-2.16.1.tar.gz']])
        command('extract-ndk', ['unzip', '-oq', files['android-ndk-r25b-linux.zip']])
        bazel = work / 'bazel'
        shutil.copyfile(files['bazel-6.5.0-linux-x86_64'], bazel)
        bazel.chmod(0o755)
        config = f'''build --action_env PYTHON_BIN_PATH="/usr/bin/python3"
build --action_env PYTHON_LIB_PATH="/usr/lib/python3/dist-packages"
build --python_path="/usr/bin/python3"
build --action_env ANDROID_NDK_HOME="{ndk}"
build --action_env ANDROID_NDK_API_LEVEL="28"
build --action_env ANDROID_NDK_VERSION="25"
build --repo_env HERMETIC_PYTHON_VERSION="3.10"
build --repo_env TF_PYTHON_VERSION="3.10"
'''
        (source / '.tf_configure.bazelrc').write_text(config, encoding='utf-8')
        (run / 'tf-configure.bazelrc').write_text(config, encoding='utf-8')
        native = {}
        for abi, target in [('x86_64', 'android_x86_64'), ('arm64-v8a', 'android_arm64')]:
            command('compile-' + abi, [bazel, '--output_user_root=' + str(work / 'bazel-cache'),
                    'build', '-c', 'opt', '--config=' + target, '--config=monolithic',
                    '--linkopt=-Wl,-z,max-page-size=16384', '--linkopt=-Wl,-z,common-page-size=16384',
                    '--jobs=' + str(args.jobs), '--local_ram_resources=11000', '--show_progress_rate_limit=30',
                    '//tensorflow/lite/java:' + LIBRARY], cwd=source)
            native[abi] = run / abi / LIBRARY
            native[abi].parent.mkdir()
            # Strip debug/symbol tables exactly as the distributed upstream AAR;
            # the library's ELF segment layout is never patched after linking.
            command('strip-' + abi, [ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip',
                    '--strip-unneeded', '-o', native[abi], source / 'bazel-bin/tensorflow/lite/java' / LIBRARY])
            command('readelf-' + abi, [ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf', '-Wl', native[abi]])
        aar = run / (ARTIFACT + '-' + VERSION + '.aar')
        replacements = {f'jni/{abi}/{LIBRARY}': path for abi, path in native.items()}
        # Retain upstream Java classes, API, notices and both 32-bit ABIs.
        with zipfile.ZipFile(files[ARTIFACT + '-2.16.1.aar']) as upstream, zipfile.ZipFile(aar, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name in sorted(upstream.namelist()):
                if name.endswith('/'):
                    continue
                data = replacements[name].read_bytes() if name in replacements else upstream.read(name)
                info = zipfile.ZipInfo(name, date_time=(2024, 3, 8, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, data)
            notice = ('Modified TensorFlow Lite Select TF Ops 2.16.1 runtime.\n'
                      'ARM64 and x86_64 rebuilt from the pinned upstream source with Bazel 6.5.0 and Android NDK r25b.\n'
                      'Link flags: -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384.\n'
                      'All upstream Flex operators retained; no model-specific pruning.\n'
                      'Java classes, 32-bit native binaries and LICENSE retained from the official 2.16.1 AAR.\n'
                      'Rebuild recipe and input SHA-256 values: tools/build_flex_runtime.py in Vision Dataset Studio.\n')
            info = zipfile.ZipInfo('NOTICE.vds-16k', date_time=(2024, 3, 8, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, notice)
        alignment = verify_flex(aar, MACHINES)
        state.update(outcome='native_build_passed', alignment=alignment, artifact_sha256=digest(aar))
        save()
        # Publish locally only after both architectures passed. A failed attempt
        # never replaces the previous local Maven artifact.
        destination = output / MAVEN_PATH
        destination.mkdir(parents=True, exist_ok=True)
        temporary = destination / (aar.name + '.' + uuid.uuid4().hex + '.tmp')
        shutil.copyfile(aar, temporary)
        os.replace(temporary, destination / aar.name)
        pom = files[ARTIFACT + '-2.16.1.pom'].read_text(encoding='utf-8')
        pom = pom.replace('<version>2.16.1</version>', f'<version>{VERSION}</version>', 1)
        (destination / (aar.stem + '.pom')).write_text(pom, encoding='utf-8')
        shutil.copyfile(run / 'status.json', destination / 'build-receipt.json')
        print(f'Built and ELF-verified: {destination / aar.name}', flush=True)
        return 0
    except Exception as exc:
        state.update(outcome='failed', error=str(exc))
        save()
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())

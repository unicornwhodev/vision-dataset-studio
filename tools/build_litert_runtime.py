#!/usr/bin/env python3
"""Build the classic LiteRT Interpreter API and JNI from a pinned public tag.

Linux x86_64/WSL; no upstream AAR binaries or opaque 1.4.2 sources are reused.
Requires build-essential, python3-dev, python3-numpy, unzip, patch and existing SDK
licences. Each attempt retains its inputs, commands and failures. No publication.
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
from check_apk_page_sizes import inspect_elf

ARTIFACT = 'litert-interpreter'
GROUP = 'com.unicornwhodev.thirdparty'
LIBRARY = 'libtensorflowlite_jni.so'
ABIS = {'x86_64': ('android_x86_64', 62), 'arm64-v8a': ('android_arm64', 183),
        'x86': ('android_x86', 3), 'armeabi-v7a': ('android_arm', 40)}


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def zip_bytes(archive, name, data):
    item = zipfile.ZipInfo(name, (2026, 8, 13, 0, 0, 0))
    item.compress_type = zipfile.ZIP_DEFLATED
    item.external_attr = 0o644 << 16
    archive.writestr(item, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--downloads', type=Path)
    parser.add_argument('--android-sdk-licenses', type=Path, required=True)
    parser.add_argument('--jobs', type=int, default=6)
    args = parser.parse_args()
    if sys.platform != 'linux' or platform.machine() != 'x86_64':
        parser.error('Run on Linux x86_64 or WSL.')
    if not (args.android_sdk_licenses / 'android-sdk-license').is_file():
        parser.error('An existing accepted Android SDK licence is required.')
    if args.jobs < 1:
        parser.error('--jobs must be positive.')
    pin = json.loads((ROOT / 'config/litert-source.json').read_text())
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    downloads = (args.downloads or work / 'downloads').resolve()
    downloads.mkdir(parents=True, exist_ok=True)
    run = ROOT / 'dist/native-litert/runs' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8])
    run.mkdir(parents=True, exist_ok=False)
    state = dict(schema=1, outcome='running', runtime_tested=False, inputs=pin,
                 builder_sha256=digest(__file__), libraries={}, commands={})

    def save():
        (run / 'status.json').write_text(json.dumps(state, indent=2) + '\n')

    def command(label, argv, cwd=work):
        print(label, flush=True)
        with (run / (label + '.log')).open('wb') as log:
            result = subprocess.run(list(map(str, argv)), cwd=cwd, stdout=log, stderr=subprocess.STDOUT)
        state['commands'][label] = dict(argv=list(map(str, argv)), exit_code=result.returncode)
        save()
        if result.returncode:
            raise RuntimeError(label + ' failed; see retained log.')

    save()
    try:
        files = {}
        for name, item in pin['inputs'].items():
            path = downloads / name
            if not path.is_file():
                print('Downloading ' + name, flush=True)
                temporary = path.with_suffix(path.suffix + '.partial')
                urllib.request.urlretrieve(item['url'], temporary)
                if digest(temporary) != item['sha256']:
                    raise ValueError('Download SHA-256 mismatch: ' + name)
                temporary.replace(path)
            if digest(path) != item['sha256']:
                raise ValueError('Cached input SHA-256 mismatch: ' + name)
            files[name] = path
        # Restore verified inputs on every attempt. Bazel retains its action cache.
        command('extract-source', ['tar', 'xzf', files[pin['source_archive']], '--no-same-owner'])
        command('extract-ndk', ['unzip', '-oq', files[pin['ndk_archive']]])
        command('extract-jdk', ['tar', 'xzf', files[pin['jdk_archive']], '--no-same-owner'])
        cpuinfo_pin = pin['cpuinfo']
        command('extract-cpuinfo', ['unzip', '-oq', files[cpuinfo_pin['archive']]])
        cpuinfo = work / ('cpuinfo-' + cpuinfo_pin['commit'])
        patch = ROOT / cpuinfo_pin['patch']
        if digest(patch) != cpuinfo_pin['patch_sha256'] or digest(cpuinfo / 'src/arm/linux/init.c') != cpuinfo_pin['original_init_sha256']:
            raise ValueError('CPUinfo source/patch input mismatch.')
        command('patch-cpuinfo', ['patch', '-p1', '--batch', '--forward', '-i', patch], cwd=cpuinfo)
        if digest(cpuinfo / 'src/arm/linux/init.c') != cpuinfo_pin['patched_init_sha256']:
            raise ValueError('CPUinfo patched source mismatch.')
        source = work / ('LiteRT-' + pin['commit'])
        ndk = work / 'android-ndk-r26d'
        java = work / 'jdk-17.0.16+8/bin'
        bazel = work / 'bazel'
        shutil.copyfile(files['bazel-7.7.0-linux-x86_64'], bazel)
        bazel.chmod(0o755)
        config = f'''build --action_env PYTHON_BIN_PATH="/usr/bin/python3"
build --action_env PYTHON_LIB_PATH="/usr/lib/python3/dist-packages"
build --repo_env HERMETIC_PYTHON_VERSION="3.11"
build --action_env ANDROID_NDK_HOME="{ndk}"
build --action_env ANDROID_NDK_API_LEVEL="28"
build --action_env ANDROID_NDK_VERSION="26"
'''
        (source / '.litert_configure.bazelrc').write_text(config)
        (run / 'litert-configure.bazelrc').write_text(config)
        native = {}
        for abi, (target, machine) in ABIS.items():
            command('compile-' + abi, [bazel, '--output_user_root=' + str(work / 'bazel-cache'),
                    'build', '--enable_bzlmod=false', '-c', 'opt', '--config=' + target, '--config=monolithic',
                    '--override_repository=cpuinfo=' + str(cpuinfo),
                    '--linkopt=-Wl,-z,max-page-size=16384', '--linkopt=-Wl,-z,common-page-size=16384',
                    '--jobs=' + str(args.jobs), '--local_ram_resources=10000', '--show_progress_rate_limit=30',
                    '//tflite/java:' + LIBRARY], cwd=source)
            library = run / (abi + '.so')
            command('strip-' + abi, [ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip',
                    '--strip-unneeded', '-o', library, source / 'bazel-bin/tflite/java' / LIBRARY])
            data = library.read_bytes()
            if data[:4] != b'\x7fELF' or int.from_bytes(data[18:20], 'little') != machine:
                raise ValueError('Wrong ELF architecture: ' + abi)
            segments = inspect_elf(data) if abi in ('x86_64', 'arm64-v8a') else None
            if segments is not None and not all(s['aligned_16k'] for s in segments):
                raise ValueError('Strict ELF alignment failure: ' + abi)
            state['libraries'][abi] = dict(sha256=digest(library), bytes=len(data), segments=segments)
            native['jni/' + abi + '/' + LIBRARY] = data
            save()
        # Include the classic Delegate API omitted from the published LiteRT 2.x AAR.
        # API, wrapper and JNI come from the same public commit, not mixed releases.
        java_sources = sorted((source / 'tflite/java/src/main/java').rglob('*.java'))
        java_sources += sorted((source / 'tflite/delegates/nnapi/java/src/main/java').rglob('*.java'))
        java_sources = sorted(set(java_sources))
        classes = run / 'classes'
        classes.mkdir()
        command('javac-version', [java / 'javac', '-version'])
        command('java-api', [java / 'javac', '--release', '8', '-g:none', '-encoding', 'UTF-8', '-d', classes,
                            '-classpath', files['checker-qual-3.43.0.jar'], *java_sources])
        required = ('Interpreter', 'InterpreterApi', 'InterpreterFactory', 'Delegate', 'NativeInterpreterWrapper')
        if not all((classes / ('org/tensorflow/lite/' + name + '.class')).is_file() for name in required):
            raise ValueError('Incomplete classic Interpreter API.')
        state['java_sources'] = {p.relative_to(source).as_posix(): digest(p) for p in java_sources}
        jar = run / 'classes.jar'
        with zipfile.ZipFile(jar, 'w') as archive:
            for p in sorted(classes.rglob('*.class')):
                zip_bytes(archive, p.relative_to(classes).as_posix(), p.read_bytes())
        notice = ('LiteRT classic Interpreter, built from public tag ' + pin['tag'] + '.\n'
                  'Source commit: ' + pin['commit'] + '\n' + pin['source_url'] + '\n'
                  'Custom variant: ' + GROUP + ':' + ARTIFACT + ':' + pin['version'] + '\n'
                  'Java API and all four JNI ABIs built from this same source; no upstream AAR reused.\n'
                  'NDK r26d/API 28, Bazel 7.7.0, Temurin JDK 17.0.16+8 (Java 8 bytecode).\n'
                  'LOAD and GNU_RELRO aligned with max-page-size=16384 and common-page-size=16384.\n'
                  'LiteRT source is unmodified; CPUinfo ARM L2 counting includes the source patch\n'
                  + cpuinfo_pin['patch'] + ' (SHA-256 ' + cpuinfo_pin['patch_sha256'] + ').\n'
                  'Rebuild recipe: tools/build_litert_runtime.py; verified inputs: config/litert-source.json.\n')
        name = ARTIFACT + '-' + pin['version']
        aar = run / (name + '.aar')
        manifest = (source / 'tflite/java/AndroidManifest.xml').read_text().replace('API 21', 'API 28').replace('minSdkVersion="21"', 'minSdkVersion="28"')
        entries = dict(native, **{'classes.jar': jar.read_bytes(), 'AndroidManifest.xml': manifest.encode(),
                                  'proguard.txt': (source / 'tflite/java/proguard.flags').read_bytes(),
                                  'LICENSE': (source / 'LICENSE').read_bytes(),
                                  'LICENSE.cpuinfo': (cpuinfo / 'LICENSE').read_bytes(),
                                  'NOTICE.vds-16k': notice.encode()})
        with zipfile.ZipFile(aar, 'w') as archive:
            for entry, data in sorted(entries.items()):
                zip_bytes(archive, entry, data)
        pom = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>{GROUP}</groupId><artifactId>{ARTIFACT}</artifactId><version>{pin['version']}</version><packaging>aar</packaging>
<name>Source-built LiteRT classic Interpreter, 16 KB aligned</name><url>{pin['source_url']}</url>
<licenses><license><name>Apache License 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0</url><distribution>repo</distribution></license></licenses>
<scm><url>https://github.com/google-ai-edge/LiteRT</url><tag>{pin['commit']}</tag></scm>
</project>
'''
        (run / (name + '.pom')).write_text(pom)
        state.update(outcome='native_build_passed', artifact_sha256=digest(aar), java_classes_sha256=digest(jar))
        save()
        dest = ROOT / 'dist/native-litert/maven' / Path(GROUP.replace('.', '/')) / ARTIFACT / pin['version']
        dest.mkdir(parents=True, exist_ok=True)
        for filename in (aar.name, name + '.pom'):
            temporary = dest / (filename + '.' + uuid.uuid4().hex + '.tmp')
            shutil.copyfile(run / filename, temporary)
            os.replace(temporary, dest / filename)
        shutil.copyfile(run / 'status.json', dest / 'build-receipt.json')
        print('Built and strictly aligned: ' + str(dest / aar.name), flush=True)
    except Exception as error:
        state.update(outcome='failed', error=str(error))
        save()
        raise
    finally:
        print('Evidence: ' + str(run), flush=True)


if __name__ == '__main__':
    main()

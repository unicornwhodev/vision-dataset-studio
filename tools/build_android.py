#!/usr/bin/env python3
"""Build real debug APKs with per-attempt evidence. Never promotes an old APK.

Requires JDK >=17, Android SDK 36/build-tools 36.0.0, and Gradle 9.3.1.
No SDK licence is accepted here; install/licence acceptance is an owner action.
All results describe build qualification only, not a release qualification.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid
import zipfile
from datetime import datetime, timezone

APP_ID = 'com.unicornwhodev.visiondatasetstudio'
GRADLE_VERSION = '9.3.1'
ROOT = Path(__file__).resolve().parents[1]

def weight_inventory(apk: Path) -> dict:
    suffixes = {'.tflite', '.litert', '.onnx', '.safetensors', '.pt', '.pth', '.gguf', '.task', '.ckpt', '.h5'}
    weights = []
    native_bytes = 0
    with zipfile.ZipFile(apk) as archive:
        for info in archive.infolist():
            if info.filename.endswith('.so'):
                native_bytes += info.file_size
            suspect = Path(info.filename).suffix.lower() in suffixes
            if info.filename.startswith(('assets/', 'res/raw/')) and not info.is_dir():
                with archive.open(info) as stream:
                    header = stream.read(8)
                suspect |= header[4:8] == b'TFL3' or header[:4] == b'GGUF'
            if suspect:
                weights.append(info.filename)
    return {'weight_files': weights, 'native_library_bytes_uncompressed': native_bytes, 'apk_bytes': apk.stat().st_size}

class Blocked(RuntimeError):
    pass

def digest(path: Path) -> str:
    with path.open('rb') as src:
        return hashlib.file_digest(src, 'sha256').hexdigest()

def atomic_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    try:
        with tmp.open('w', encoding='utf-8') as out:
            json.dump(data, out, indent=2, ensure_ascii=False)
            out.write('\n'); out.flush(); os.fsync(out.fileno())
        os.replace(tmp, path)
    finally:
        tmp.unlink(missing_ok=True)

def sdk_dir(root: Path, env: dict[str, str]) -> Path:
    # Local Gradle configuration wins, consistently with Android builds.
    local = root / 'local.properties'
    if local.is_file():
        for line in local.read_text(encoding='utf-8').splitlines():
            match = re.match(r'^\s*sdk\.dir\s*=\s*(.*?)\s*$', line)
            if match:
                value = re.sub(r'\\([\\: =])', r'\1', match[1])
                return Path(value).expanduser().resolve()
    value = env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT')
    if not value:
        raise Blocked('Android SDK absent: set ANDROID_HOME or sdk.dir in local.properties.')
    return Path(value).expanduser().resolve()

def parse_java_major(text: str) -> int:
    match = re.search(r'(?:openjdk|java) version "(\d+)(?:\.(\d+))?', text)
    if not match:
        raise Blocked('Cannot verify Java version.')
    return int(match[2]) if match[1] == '1' and match[2] else int(match[1])

def verify_badging(text: str, app_id: str) -> None:
    match = re.search(r"^package: name='([^']+)'", text, re.M)
    if not match or match[1] != app_id:
        raise RuntimeError('Compiled APK application ID does not match the required identity.')

class Attempt:
    def __init__(self, root: Path):
        self.root = root
        self.base = root / 'dist/android'
        self.id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:12]
        self.out = self.base / 'runs' / self.id
        self.out.mkdir(parents=True, exist_ok=False)
        self.state = {
            'schema': 1, 'run_id': self.id, 'application_id': APP_ID,
            'started_at': datetime.now(timezone.utc).isoformat(), 'phase': 'preflight',
            'outcome': 'running', 'apk_built': False, 'test_apk_built': False,
            'artifacts': {}, 'all_build_checks_passed': False,
            'installed': False, 'instrumented_tests_executed': False,
            'production_qualified': False, 'debug_only': True,
            'github_sha': os.environ.get('GITHUB_SHA'), 'github_run_id': os.environ.get('GITHUB_RUN_ID'),
        }
        self.save()
    def save(self) -> None:
        atomic_json(self.out / 'status.json', self.state)
        # The latest pointer is changed BEFORE any prerequisite check. Older evidence is retained separately.
        atomic_json(self.base / 'latest.json', {'run': f'runs/{self.id}', **self.state})
    def command(self, name: str, args: list[str]) -> int:
        print('Running:', ' '.join(map(str, args)), flush=True)
        with (self.out / f'{name}.log').open('w', encoding='utf-8') as log:
            with subprocess.Popen(args, cwd=self.root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, encoding='utf-8', errors='replace') as process:
                assert process.stdout is not None
                for line in process.stdout:
                    print(line, end='', flush=True); log.write(line); log.flush()
                code = process.wait()
        self.state.setdefault('commands', {})[name] = code
        self.save()
        return code
    def fail_on_command(self, name: str, args: list[str]) -> None:
        code = self.command(name, args)
        if code:
            raise RuntimeError(f'{name} failed (exit {code}); inspect its log.')
    def store_apk(self, source: Path, name: str, key: str, app_id: str, tools: Path) -> None:
        if not source.is_file() or source.stat().st_size == 0:
            raise RuntimeError(f'{key}: assembly returned without an APK.')
        self.fail_on_command(key + '-signature', [str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(source)])
        self.fail_on_command(key + '-identity', [str(tools / 'aapt'), 'dump', 'badging', str(source)])
        verify_badging((self.out / (key + '-identity.log')).read_text(), app_id)
        if key == 'app':
            inventory = weight_inventory(source)
            (self.out / 'app-contents.json').write_text(json.dumps(inventory, indent=2) + '\n')
            if inventory['weight_files']:
                raise RuntimeError('Model weights must be downloaded after installation, never bundled in the APK.')
        dest = self.out / name
        shutil.copyfile(source, dest)
        value = digest(dest)
        dest.with_suffix('.apk.sha256').write_text(f'{value}  {name}\n')
        self.state['artifacts'][key] = {'file': name, 'sha256': value, 'bytes': dest.stat().st_size}
        self.state['apk_built' if key == 'app' else 'test_apk_built'] = True
        self.save()

def main(root: Path = ROOT) -> int:
    attempt = Attempt(root)
    exit_code = 1
    try:
        java = shutil.which('java')
        if not java: raise Blocked('JDK 17+ missing.')
        attempt.fail_on_command('java-version', [java, '-version'])
        if parse_java_major((attempt.out / 'java-version.log').read_text()) < 17:
            raise Blocked('JDK 17+ required.')
        sdk = sdk_dir(root, os.environ)
        tools = sdk / 'build-tools/36.0.0'
        required = [sdk / 'platforms/android-36/android.jar', tools / 'apksigner', tools / 'aapt']
        absent = [str(p) for p in required if not p.is_file()]
        if absent: raise Blocked('SDK components absent: ' + ', '.join(absent))
        override = os.environ.get('VDS_GRADLE_BIN')
        gradle = [override] if override else [sys.executable, str(root / 'tools/gradle_bootstrap.py')]
        attempt.fail_on_command('gradle-version', [*gradle, '--version'])
        version_log = (attempt.out / 'gradle-version.log').read_text()
        if not re.search(r'^Gradle ' + re.escape(GRADLE_VERSION) + r'\s*$', version_log, re.M):
            raise Blocked('Gradle 9.3.1 required, no silent substitution.')
        attempt.state['phase'] = 'assemble'; attempt.save()
        attempt.fail_on_command('assemble', [*gradle, ':app:assembleDebug', '--console=plain', '--stacktrace'])
        attempt.state['phase'] = 'verify-app'; attempt.save()
        attempt.store_apk(root / 'app/build/outputs/apk/debug/app-debug.apk',
                          'vision-dataset-studio-uwd-debug.apk', 'app', APP_ID, tools)
        attempt.state['phase'] = 'dependency-tests-lint'; attempt.save()
        checks = attempt.command('checks', [*gradle, ':app:testDebugUnitTest', ':app:lintDebug',
                   ':app:assembleDebugAndroidTest', '--continue', '--console=plain', '--stacktrace'])
        test_apk = root / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        # Explicitly invoke its task again so that an old test APK cannot be promoted after a failed task.
        attempt.state['phase'] = 'assemble-test'; attempt.save()
        test_code = attempt.command('assemble-test', [*gradle, ':app:assembleDebugAndroidTest', '--console=plain', '--stacktrace'])
        if test_code == 0:
            attempt.store_apk(test_apk, 'vision-dataset-studio-uwd-test.apk', 'tests', APP_ID + '.test', tools)
        if checks or test_code:
            raise RuntimeError('APK evidence retained, but dependency tests/lint/test APK checks did not all pass.')
        attempt.state.update(phase='complete', outcome='build_checks_passed', all_build_checks_passed=True)
        exit_code = 0
    except Blocked as exc:
        attempt.state.update(outcome='blocked', error=str(exc)); exit_code = 2
        print('BLOCKED:', exc, file=sys.stderr)
    except KeyboardInterrupt:
        attempt.state.update(outcome='interrupted', error='Build interrupted by operator.'); exit_code = 130
    except Exception as exc:
        attempt.state.update(outcome='failed', error=str(exc))
        print('FAILED:', exc, file=sys.stderr)
    finally:
        attempt.state['exit_code'] = exit_code
        attempt.state['finished_at'] = datetime.now(timezone.utc).isoformat()
        attempt.save()
        print('Evidence:', attempt.out)
        if not attempt.state['apk_built']:
            print('No APK was built or promoted by this attempt.')
    return exit_code

if __name__ == '__main__':
    raise SystemExit(main())

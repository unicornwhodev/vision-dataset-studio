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
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
sys.path.insert(0, str(Path(__file__).resolve().parent))
from qa.check_flex_runtime import AAR_NAME, LOCAL_MAVEN, MACHINES, verify_flex
from qa.check_graphics_runtime import verify_graphics
from qa.check_litert_runtime import AAR as LITERT_AAR, verify_litert
from qa.check_graphics_runtime import AAR as GRAPHICS_AAR

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

def sdk_tool(directory: Path, name: str, platform: str | None = None) -> Path:
    """Resolve the installed platform's SDK tools without accepting a different SDK."""
    platform = platform or os.name
    suffix = ('.bat' if name == 'apksigner' else '.exe') if platform == 'nt' else ''
    return directory / (name + suffix)


def source_manifest(root: Path) -> dict:
    names = subprocess.check_output(['git', 'ls-files', '-c', '-o', '--exclude-standard', '-z'], cwd=root).decode('utf-8').split('\0')
    compiled = lambda name: (name.startswith(('app/', 'gradle/', 'release-qa/')) and not name.endswith('/.gitignore')) or name in ('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'tools/build_android.py', 'tools/gradle_bootstrap.py', 'tools/build_flex_runtime.py', 'tools/build_graphics_path.py', 'config/graphics-path-source.json', 'tools/qa/check_graphics_runtime.py', 'tools/qa/check_flex_runtime.py', 'tools/qa/check_apk_page_sizes.py', 'tools/build_litert_runtime.py', 'config/litert-source.json', 'tools/qa/check_litert_runtime.py', 'third_party/patches/cpuinfo-l2-count.patch')
    native = root / LOCAL_MAVEN / AAR_NAME
    return {
        'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode().strip(),
        'files': {name: digest(root / name) for name in sorted(set(names)) if name and compiled(name)},
        'native_runtime': {'file': native.relative_to(root).as_posix(), 'sha256': digest(native)} if native.is_file() else None,
        'native_runtimes': {p.relative_to(ROOT).as_posix(): digest(root / p.relative_to(ROOT))
                            for p in (LITERT_AAR, GRAPHICS_AAR) if (root / p.relative_to(ROOT)).is_file()},
    }


def verify_local_flex(root: Path) -> dict:
    directory = root / LOCAL_MAVEN
    aar = directory / AAR_NAME
    receipt = directory / 'build-receipt.json'
    if not aar.is_file() or not receipt.is_file():
        raise Blocked('The 16 KB Flex runtime is missing. Build it with tools/build_flex_runtime.py; see docs/FLEX_16K.md.')
    state = json.loads(receipt.read_text(encoding='utf-8'))
    if state.get('outcome') != 'native_build_passed' or digest(aar) != state.get('artifact_sha256'):
        raise Blocked('Flex native build receipt does not match the local AAR.')
    if state.get('builder_sha256') != digest(root / 'tools/build_flex_runtime.py'):
        raise Blocked('Flex was produced by a different build recipe; rebuild the native runtime.')
    return verify_flex(aar, MACHINES)

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
        self.fail_on_command(key + '-signature', [str(sdk_tool(tools, 'apksigner')), 'verify', '--verbose', '--print-certs', str(source)])
        self.fail_on_command(key + '-identity', [str(sdk_tool(tools, 'aapt')), 'dump', 'badging', str(source)])
        verify_badging((self.out / (key + '-identity.log')).read_text(encoding='utf-8'), app_id)
        if key == 'app':
            atomic_json(self.out / 'litert-16k.json', verify_litert(source))
            atomic_json(self.out / 'graphics-16k.json', verify_graphics(source))
            alignment = verify_flex(source)
            expected = self.state['flex_runtime']['libraries']
            if any(row['sha256'] != expected[abi]['sha256'] for abi, row in alignment['libraries'].items()):
                raise RuntimeError('Packaged Flex differs from its native build receipt; refresh the Gradle dependency cache.')
            atomic_json(self.out / 'flex-16k.json', alignment)
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
        attempt.state['flex_runtime'] = verify_local_flex(root)
        attempt.state['graphics_runtime'] = verify_graphics()
        attempt.state['litert_runtime'] = verify_litert()
        attempt.save()
        java = shutil.which('java')
        if not java: raise Blocked('JDK 17+ missing.')
        attempt.fail_on_command('java-version', [java, '-version'])
        if parse_java_major((attempt.out / 'java-version.log').read_text(encoding='utf-8')) < 17:
            raise Blocked('JDK 17+ required.')
        sdk = sdk_dir(root, os.environ)
        tools = sdk / 'build-tools/36.0.0'
        required = [sdk / 'platforms/android-36/android.jar', sdk_tool(tools, 'apksigner'), sdk_tool(tools, 'aapt')]
        absent = [str(p) for p in required if not p.is_file()]
        if absent: raise Blocked('SDK components absent: ' + ', '.join(absent))
        override = os.environ.get('VDS_GRADLE_BIN')
        gradle = [shutil.which(override) or override] if override else [sys.executable, str(root / 'tools/gradle_bootstrap.py')]
        # A shared Windows cache may be a junction and contain unrelated init scripts.
        # Use the same isolated home for bootstrap and overridden Gradle executables.
        gradle_home = Path(os.environ.get('GRADLE_USER_HOME', root / 'dist/gradle-home')).resolve()
        gradle += ['--gradle-user-home', str(gradle_home)]
        attempt.state['gradle_user_home'] = str(gradle_home)
        attempt.fail_on_command('gradle-version', [*gradle, '--version'])
        version_log = (attempt.out / 'gradle-version.log').read_text(encoding='utf-8')
        if not re.search(r'^Gradle ' + re.escape(GRADLE_VERSION) + r'\s*$', version_log, re.M):
            raise Blocked('Gradle 9.3.1 required, no silent substitution.')
        attempt.state['phase'] = 'assemble'; attempt.save()
        source_before = source_manifest(root)
        atomic_json(attempt.out / 'source-manifest.json', source_before)
        # Incremental ZIP packaging can retain the replaced native payload as
        # unused space. Prior APKs are already retained in immutable attempt dirs;
        # regenerate only these two disposable Gradle output files.
        for relative in ('app/build/outputs/apk/debug/app-debug.apk',
                         'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
            (root / relative).unlink(missing_ok=True)
        attempt.state['fresh_apk_packaging'] = True
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
        if source_manifest(root) != source_before:
            raise RuntimeError('Compiled sources changed during the build; APKs retained without qualification.')
        reports = attempt.out / 'reports'
        reports.mkdir()
        junit = root / 'app/build/test-results/testDebugUnitTest'
        suites = [ET.parse(path).getroot() for path in junit.glob('TEST-*.xml')]
        if not suites:
            raise RuntimeError('No JVM test report was produced.')
        attempt.state['jvm_tests'] = {key: sum(int(s.get(key, 0)) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
        for path in junit.glob('TEST-*.xml'):
            shutil.copyfile(path, reports / path.name)
        for name in ('lint-results-debug.xml', 'lint-results-debug.txt', 'lint-results-debug.html'):
            shutil.copyfile(root / 'app/build/reports' / name, reports / name)
        lint = ET.parse(reports / 'lint-results-debug.xml').getroot()
        attempt.state['lint'] = {severity: sum(issue.get('severity') == severity for issue in lint.findall('issue')) for severity in ('Error', 'Warning')}
        shutil.copytree(root / 'app/schemas', reports / 'schemas')
        generated = root / 'app/build/generated/ksp/debug'
        attempt.state['ksp_generated_files'] = sum(p.is_file() for p in generated.rglob('*'))
        if not list(generated.rglob('AppDatabase_Impl.*')):
            raise RuntimeError('KSP Room implementation absent.')
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

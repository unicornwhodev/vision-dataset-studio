#!/usr/bin/env python3
"""Package a signed minified prerelease with exact-APK emulator evidence.

All build inputs must match committed Git blobs. Both ABI test pairs and the
independent UI driver must pass before packaging. Physical-device gaps remain
explicit; this command neither publishes nor rewrites historical receipts.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

from build_android import ROOT, APP_ID, digest, sdk_dir, sdk_tool, source_manifest, weight_inventory
from qa.check_flex_runtime import verify_flex
from qa.check_graphics_runtime import verify_graphics
from qa.check_litert_runtime import verify_litert
sys.path.insert(0, str(ROOT / 'tools/qa'))
from run_device_qualification import parse_instrumentation


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def artifact(folder, info):
    name = info['file']
    if Path(name).name != name or not name.endswith('.apk'):
        raise ValueError('Invalid APK receipt filename')
    path = folder / name
    if digest(path) != info['sha256'] or path.stat().st_size != info['bytes']:
        raise ValueError('APK differs from its receipt')
    return path


def apk_payload(path):
    """Signing metadata can differ; every application entry must stay identical."""
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate ZIP entries are not accepted')
        return {n: hashlib.sha256(archive.read(n)).hexdigest() for n in names
                if not re.fullmatch(r'META-INF/(?:[^/]+\.(?:RSA|DSA|EC|SF)|MANIFEST\.MF)', n)}


def verify_suite(folder, app_sha, count, *, test_sha=None, qa_sha=None):
    state = read(folder / 'status.json')
    outcome = 'release_core_suite_passed' if count == 40 else 'release_ui_suite_passed'
    if state.get('outcome') != outcome or state.get('main_sha256') != app_sha:
        raise ValueError('Suite did not pass against the selected APK')
    for field, expected in [('test_sha256', test_sha), ('qa_sha256', qa_sha)]:
        if expected is not None and state.get(field) != expected:
            raise ValueError('Suite used a different test APK')
    parsed = parse_instrumentation((folder / 'instrumentation.txt').read_text(encoding='utf-8'))
    if (parsed != state.get('tests') or not parsed['complete'] or parsed['passed'] != count
            or parsed['failed'] or parsed['skipped'] or parsed['aborted']):
        raise ValueError('Incomplete, skipped or inconsistent instrumentation')
    if state.get('art_crashes_before') != [] or state.get('art_crashes_after') != []:
        raise ValueError('Missing ART checks or observed ART crash')
    if str(state.get('page_size')) != '16384':
        raise ValueError('Expected 16 KB emulator evidence')
    return dict(passed=count, failed=0, skipped=0, apk_sha256=app_sha,
                page_size=16384, host_abi=state['abi'], physical_device=False)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for abi in ['arm', 'x86']:
        p.add_argument('--' + abi + '-build', required=True, type=Path)
        p.add_argument('--' + abi + '-signed', required=True, type=Path)
    p.add_argument('--evidence', required=True, type=Path, help='Reviewed, tracked public receipts')
    p.add_argument('--qa-apk', required=True, type=Path)
    p.add_argument('--version', required=True)
    a = p.parse_args()
    if not re.fullmatch(r'\d+\.\d+\.\d+-rc\d+', a.version):
        p.error('An explicit prerelease version is required')
    if git('status', '--porcelain').strip():
        raise ValueError('Commit the reviewed tree before packaging')
    head = git('rev-parse', 'HEAD').decode().strip()
    names = set(filter(None, git('ls-files', '-z').decode().split('\0')))
    evidence = a.evidence.resolve()
    prefix = evidence.relative_to(ROOT).as_posix() + '/'
    public_files = {n for n in names if n.startswith(prefix)}
    if not public_files or public_files != {p.relative_to(ROOT).as_posix() for p in evidence.rglob('*') if p.is_file()}:
        raise ValueError('Evidence must contain exactly the reviewed tracked files')
    current = source_manifest(ROOT)
    for name, expected in current['files'].items():
        if hashlib.sha256(git('show', head + ':' + name)).hexdigest() != expected:
            raise ValueError('Compiled source bytes differ from Git: ' + name)
    pin = read(ROOT / 'config/release-signing.json')
    sdk = sdk_dir(ROOT, os.environ) / 'build-tools/36.0.0'
    qa_sha = digest(a.qa_apk)
    apks, builds, suites = {}, {}, {}
    for label, abi in [('arm', 'arm64-v8a'), ('x86', 'x86_64')]:
        build_dir = getattr(a, label + '_build').resolve()
        signed_dir = getattr(a, label + '_signed').resolve()
        build, signed = read(build_dir / 'status.json'), read(signed_dir / 'status.json')
        manifest = read(build_dir / 'source-manifest.json')
        if build.get('outcome') != 'release_test_apks_built' or build.get('abi') != abi:
            raise ValueError('Wrong Release build or ABI')
        for field in ['files', 'native_runtime', 'native_runtimes']:
            if manifest.get(field) != current.get(field):
                raise ValueError('Current sources/runtime differ from the tested build')
        if (signed.get('outcome') != 'signed_release_test_apks'
                or signed['build_receipt_sha256'] != digest(build_dir / 'status.json')
                or signed['source_manifest_sha256'] != digest(build_dir / 'source-manifest.json')
                or signed['certificate_sha256'] != pin['certificate_sha256']):
            raise ValueError('Invalid durable signing receipt')
        apks[label] = {}
        for kind in ['app', 'tests']:
            original = artifact(build_dir, build['artifacts'][kind])
            path = artifact(signed_dir, signed['artifacts'][kind])
            if apk_payload(path) != apk_payload(original):
                raise ValueError('Signing changed the APK payload')
            signature = subprocess.check_output([str(sdk_tool(sdk, 'apksigner')), 'verify', '--print-certs', str(path)], text=True)
            if re.findall(r'certificate SHA-256 digest: ([0-9a-f]{64})', signature) != [pin['certificate_sha256']]:
                raise ValueError('APK certificate is not the durable pinned identity')
            apks[label][kind] = path
        app_sha, test_sha = digest(apks[label]['app']), digest(apks[label]['tests'])
        badging = subprocess.check_output([str(sdk_tool(sdk, 'aapt')), 'dump', 'badging', str(apks[label]['app'])], text=True, encoding='utf-8')
        if (f"name='{APP_ID}'" not in badging or f"versionName='{a.version}'" not in badging
                or 'application-debuggable' in badging or f"native-code: '{abi}'" not in badging):
            raise ValueError('Wrong application identity, version, ABI or debuggable flag')
        if weight_inventory(apks[label]['app'])['weight_files']:
            raise ValueError('Model weights are forbidden in the application')
        verify_flex(apks[label]['app'], [abi])
        verify_graphics(apks[label]['app'])
        verify_litert(apks[label]['app'], [abi])
        # Recompute the APK audit; a forged or stale JSON report cannot pass.
        audit = read(evidence / label / 'strict-alignment.json')
        temporary = ROOT / 'dist' / ('release-alignment-' + label + '.json')
        subprocess.run([sys.executable, str(ROOT / 'tools/qa/check_apk_page_sizes.py'),
                        '--apk', str(apks[label]['app']), '--output', str(temporary)], check=True)
        if audit != read(temporary) or len(audit['native_64bit_libraries']) != 4:
            raise ValueError('Strict native audit is inconsistent')
        core = verify_suite(evidence / label / 'core', app_sha, 40, test_sha=test_sha)
        ui = verify_suite(evidence / label / 'ui', app_sha, 4, qa_sha=qa_sha)
        package_text = (evidence / label / 'core/package.txt').read_text(encoding='utf-8')
        if f'primaryCpuAbi={abi}' not in package_text:
            raise ValueError('Installed package ABI differs from the selected build')
        suites[label] = dict(core=core, ui=ui, package_abi=abi, translated_arm=label == 'arm')
        builds[label] = dict(build_receipt_base_commit=manifest['source_commit'],
                            build_receipt_sha256=digest(build_dir / 'status.json'),
                            source_manifest_sha256=digest(build_dir / 'source-manifest.json'),
                            signed_artifacts=signed['artifacts'])
    host = read(evidence / 'host-checks.json')
    if host['jvm']['failed'] or host['jvm']['errors'] or host['jvm']['skipped'] or host['jvm']['passed'] != 70:
        raise ValueError('JVM checks incomplete')
    if host['lint']['errors'] or not host['python']['passed']:
        raise ValueError('Host checks failed')
    continuation = read(evidence / 'model-continuation.json')
    if (not continuation['source_model_preserved'] or not continuation['continued_without_activation']
            or not continuation['missing_checkpoint_refused']
            or continuation['original_sha256_before'] != continuation['original_sha256_after']
            or continuation['first_final_weights'] != continuation['second_initial_weights']
            or continuation['second_initial_weights'] == continuation['second_final_weights']):
        raise ValueError('Model preservation/continuation not proven')
    out = ROOT / 'dist' / ('release-' + a.version)
    out.mkdir(parents=True, exist_ok=False)
    package = dict(schema=1, kind='signed-minified-release-prerelease', version=a.version,
        product='Cadryl', application_id=APP_ID, source_commit=head, compiled_source_commit=head,
        compiled_files_verified=len(current['files']), compiled_sources_byte_exact=True,
        source_manifest=current, builds=builds, core_and_ui=suites, host_checks=host,
        stable_release=False, physical_arm_qualified=False, physical_arm_16k_qualified=False,
        strict_native_16k_audit_passed=True, ci_executed=False, runnable_container=False,
        signature=dict(certificate_sha256=pin['certificate_sha256'], durable=True,
                       compatible_with_rc4_or_rc5_debug=False),
        native_transitive_notices_review_complete=False,
        limitations=['Physical Honor interrupted on a prior candidate; this rc6 has emulator evidence only.',
                     'ARM64 ran through libndk_translation on x86_64, not a physical ARM device.',
                     'Historical ART cause unconfirmed; new environment and ART guard in place.',
                     'Model-catalogue quality, extended operation and remote CI remain incomplete.'])
    (out / 'PACKAGE.json').write_text(json.dumps(package, indent=2) + '\n', encoding='utf-8')
    shutil.copyfile(apks['arm']['app'], out / 'vision-dataset-studio.apk')
    docs = {n for n in names if n.startswith(('docs/', 'third_party/')) or n in
            ['README.md', 'README.en.md', 'LICENSE', 'NOTICE', 'KNOWN_LIMITATIONS.md', 'TEST_REPORT.md', 'LICENSING_STATUS.md', 'QUALIFICATION_STATUS.json', 'DATA_SCHEMA.md', 'CONTRIBUTING.md']}
    with zipfile.ZipFile(out / f'vision-dataset-studio-{a.version}-qualification.zip', 'w', zipfile.ZIP_DEFLATED, compresslevel=3) as z:
        z.write(out / 'PACKAGE.json', 'PACKAGE.json')
        for label, pair in apks.items():
            for kind, path in pair.items():
                z.write(path, f'apks/{label}/{kind}.apk')
        z.write(a.qa_apk, 'apks/release-ui-driver.apk')
        for name in sorted(docs | public_files):
            z.write(ROOT / name, name)
    for runtime in ['flex', 'graphics', 'litert']:
        base = ROOT / ('dist/native-' + runtime + '/maven')
        files = sorted(p for p in base.rglob('*') if p.is_file())
        # Only the active version directory is part of this source manifest.
        aar = (ROOT / current['native_runtime']['file'] if runtime == 'flex' else
               next(ROOT / n for n in current['native_runtimes'] if '/native-' + runtime + '/' in n))
        files = [p for p in files if p.parent == aar.parent]
        if {p.suffix for p in files} != {'.aar', '.pom', '.json'} or len(files) != 3:
            raise ValueError('Unexpected native Maven package files')
        archive = out / ('vision-dataset-studio-' + runtime + '-' + aar.parent.name + '.zip')
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=1) as z:
            for path in files:
                z.write(path, path.relative_to(ROOT).as_posix())
            for name in sorted(n for n in names if n.startswith(('config/', 'third_party/patches/')) or n in
                               ['LICENSE', 'NOTICE', 'third_party/NOTICES.runtime.txt', 'docs/FLEX_16K.md',
                                'docs/GRAPHICS_PATH_16K.md', 'docs/LITERT_16K_STATUS.md',
                                'tools/build_flex_runtime.py', 'tools/build_graphics_path.py', 'tools/build_litert_runtime.py']):
                z.write(ROOT / name, name)
    assets = sorted(p for p in out.iterdir() if p.is_file())
    (out / 'SHA256SUMS').write_text(''.join(digest(p) + '  ' + p.name + '\n' for p in assets), encoding='utf-8')
    print(out)


if __name__ == '__main__':
    main()

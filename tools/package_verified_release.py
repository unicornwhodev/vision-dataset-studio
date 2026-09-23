#!/usr/bin/env python3
"""Package already-built, source-matched Windows qualification artifacts.

This never signs an APK, changes build receipts, or claims a production/CI result.
Only explicitly reviewed evidence and tracked documentation enter the archives.
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

from build_android import ROOT, APP_ID, digest, sdk_dir, sdk_tool, source_manifest, verify_local_flex, weight_inventory
from qa.check_flex_runtime import LOCAL_MAVEN, AAR_NAME
sys.path.insert(0, str(ROOT / 'tools/qa'))
from qa.run_device_qualification import parse_instrumentation


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def verified_artifact(folder, info):
    name = info['file']
    if Path(name).name != name or not name.endswith('.apk'):
        raise ValueError('Unsafe APK name')
    path = folder / name
    if not path.is_file() or digest(path) != info['sha256'] or path.stat().st_size != info['bytes']:
        raise ValueError('APK differs from its build receipt')
    return path


def source_binding(path, expected, worktree, committed):
    """Keep APK inputs byte-exact; record the bootstrap script's Git LF conversion."""
    if hashlib.sha256(worktree).hexdigest() != expected:
        raise ValueError('Recorded source changed after its build: ' + path)
    committed_sha256 = hashlib.sha256(committed).hexdigest()
    if committed_sha256 == expected:
        return None
    # This Python launcher is not an Android compilation input. Git normalizes
    # its Windows CRLF checkout to LF; no other source difference is accepted.
    if path == 'tools/gradle_bootstrap.py' and worktree.replace(b'\r\n', b'\n') == committed:
        return dict(path=path, build_sha256=expected, committed_sha256=committed_sha256,
                    transformation='CRLF to LF only; host Python bootstrap, not Android source')
    raise ValueError('Tested source bytes differ from the commit: ' + path)


def verify_core(evidence, build, pages):
    state = read(evidence / 'status.json')
    if state.get('outcome') != 'core_suite_passed' or str(state.get('page_size')) != str(pages):
        raise ValueError('Core suite not qualified for the requested page size')
    if state['build']['run_id'] != build['run_id'] or state['build']['artifacts'] != build['artifacts']:
        raise ValueError('Core suite tested different APKs')
    result = parse_instrumentation((evidence / 'instrumentation.txt').read_text(encoding='utf-8'))
    if not result['complete'] or result != state['instrumentation']:
        raise ValueError('Incomplete or inconsistent instrumentation evidence')
    training = state['training_continuation']
    if not (training.get('continued_without_activation') and training.get('missing_checkpoint_refused')
            and re.fullmatch(r'[0-9a-f]{64}', training.get('original_sha256_before', ''))
            and re.fullmatch(r'[0-9a-f]{64}', training.get('first_final_weights', ''))
            and training.get('original_sha256_before') == training.get('original_sha256_after')
            and training.get('first_final_weights') == training.get('second_initial_weights')):
        raise ValueError('Model preservation/continuation not verified')
    restart = read(evidence / 'lineage-after-process-restart.json')
    if restart.get('outcome') != 'passed' or restart['hashes_before'] != restart['hashes_after']:
        raise ValueError('Persisted model files changed after restart')
    return dict(page_size=pages, passed=result['passed'], failed=result['failed'], skipped=result['skipped'],
                abi=state['package_abi'], physical_device=False, model_continuation_verified=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-dir', type=Path, required=True)
    parser.add_argument('--candidate-dir', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True, help='Reviewed, tracked public evidence directory')
    parser.add_argument('--version', required=True)
    parser.add_argument('--previous-certificate-sha256', required=True)
    parser.add_argument('--allow-incompatible-prerelease-signature', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'\d+\.\d+\.\d+-rc\d+', args.version):
        parser.error('An explicit prerelease version is required')
    if not re.fullmatch(r'[0-9a-f]{64}', args.previous_certificate_sha256):
        parser.error('Expected SHA-256 of the previously distributed signing certificate')
    if git('status', '--porcelain').strip():
        raise RuntimeError('Commit the reviewed tree before packaging')
    head = git('rev-parse', 'HEAD').decode().strip()
    build_dir = args.build_dir.resolve()
    evidence = args.evidence.resolve()
    evidence_rel = evidence.relative_to(ROOT).as_posix()
    names = [n for n in git('ls-files', '-z').decode().split('\0') if n]
    public_files = [n for n in names if n.startswith(evidence_rel + '/')]
    if not public_files or set(p.relative_to(ROOT).as_posix() for p in evidence.rglob('*') if p.is_file()) != set(public_files):
        raise RuntimeError('Public evidence must contain exactly the reviewed tracked files')
    built = read(build_dir / 'source-manifest.json')
    current = source_manifest(ROOT)
    if built['files'] != current['files'] or built['native_runtime'] != current['native_runtime']:
        raise RuntimeError('Current compiled inputs differ from the tested build')
    normalized = []
    for path, expected in built['files'].items():
        binding = source_binding(path, expected, (ROOT / path).read_bytes(), git('show', f'{head}:{path}'))
        if binding:
            normalized.append(binding)
    verify_local_flex(ROOT)
    build = read(build_dir / 'status.json')
    if build.get('outcome') != 'build_checks_passed' or not build.get('all_build_checks_passed'):
        raise RuntimeError('Build checks did not pass')
    if read(evidence / 'build/status.json') != build or read(evidence / 'build/source-manifest.json') != built:
        raise RuntimeError('Public build evidence differs from original receipts')
    apks = {key: verified_artifact(build_dir, build['artifacts'][key]) for key in ('app', 'tests')}
    if weight_inventory(apks['app'])['weight_files']:
        raise RuntimeError('Model weights must not be embedded')
    suites = [verify_core(evidence / 'api35-4k', build, 4096), verify_core(evidence / 'api35-16k', build, 16384)]
    arm = read(evidence / 'arm64-translated/status.json')
    arm_result = parse_instrumentation((evidence / 'arm64-translated/instrumentation.txt').read_text(encoding='utf-8'))
    if (arm.get('outcome') != 'passed' or arm['build']['artifacts'] != build['artifacts']
            or not arm_result['complete'] or arm_result != arm['instrumentation'] or arm.get('forced_package_abi') != 'arm64-v8a'
            or arm.get('physical_arm') is not False):
        raise RuntimeError('ARM translated evidence is inconsistent')
    candidate = read(args.candidate_dir / 'status.json')
    if candidate.get('outcome') != 'unsigned_candidate_built' or candidate.get('signed') is not False:
        raise RuntimeError('Unsigned candidate receipt invalid')
    if read(args.candidate_dir / 'source-manifest.json') != built:
        raise RuntimeError('Release candidate uses different sources')
    unsigned = verified_artifact(args.candidate_dir, candidate['artifact'])
    signer = sdk_tool(sdk_dir(ROOT, os.environ) / 'build-tools/36.0.0', 'apksigner')
    signature = subprocess.check_output([str(signer), 'verify', '--print-certs', str(apks['app'])], text=True)
    certificate = re.search(r'certificate SHA-256 digest: ([0-9a-f]{64})', signature)[1]
    compatible = certificate == args.previous_certificate_sha256
    if not compatible and not args.allow_incompatible_prerelease_signature:
        raise RuntimeError('Signing key differs from the previous release; upgrade compatibility must be explicitly addressed')
    package = dict(schema=2, kind='workstation-debug-prerelease', version=args.version, application_id=APP_ID,
        stable_release=False, source_commit=head, compiled_source_commit=head,
        build_receipt_base_commit=built['source_commit'], compiled_files_verified=len(built['files']),
        source_binding='Android sources match committed Git blobs byte for byte; any host bootstrap CRLF normalization is recorded separately. Original build receipts are unchanged.',
        byte_exact_recorded_files=len(built['files']) - len(normalized), host_script_line_endings=normalized,
        build_run=build['run_id'], artifacts=build['artifacts'], core_suites=suites,
        arm64_translated_tests=arm['instrumentation']['passed'], physical_arm_qualified=False,
        release_candidate=candidate['artifact'], release_candidate_signed=False, release_candidate_device_qualified=False,
        signature=dict(certificate_sha256=certificate, previous_certificate_sha256=args.previous_certificate_sha256,
            compatible_with_rc4=compatible, durable_production_signature=False),
        ci_executed=False, runnable_container=False, whole_apk_16k_qualified=False,
        native_transitive_notices_review_complete=False,
        native_runtime=dict(coordinate='org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1-vds16k1',
                            sha256=built['native_runtime']['sha256']))
    out = ROOT / 'dist' / ('release-' + args.version)
    out.mkdir(parents=True, exist_ok=False)
    (out / 'PACKAGE.json').write_text(json.dumps(package, indent=2) + '\n', encoding='utf-8')
    shutil.copyfile(apks['app'], out / 'vision-dataset-studio.apk')
    shutil.copyfile(unsigned, out / unsigned.name)
    docs = [n for n in names if n.startswith(('docs/', 'third_party/')) or n in
        ('README.md', 'README.en.md', 'LICENSE', 'NOTICE', 'LICENSING_STATUS.md', 'KNOWN_LIMITATIONS.md',
         'QUALIFICATION_STATUS.json', 'TEST_REPORT.md')]
    archive = out / f'vision-dataset-studio-{args.version}-qualification.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=3) as z:
        z.write(out / 'PACKAGE.json', 'PACKAGE.json')
        for apk in apks.values():
            z.write(apk, apk.name)
        for path in sorted(set(docs + public_files)):
            z.write(ROOT / path, path)
    native_zip = out / 'vision-dataset-studio-flex-2.16.1-vds16k1.zip'
    native_root = ROOT / LOCAL_MAVEN
    allowed = {AAR_NAME, 'tensorflow-lite-select-tf-ops-2.16.1-vds16k1.pom', 'build-receipt.json'}
    native_files = [p for p in native_root.iterdir() if p.is_file()]
    if {p.name for p in native_files} != allowed:
        raise RuntimeError('Unexpected or missing native runtime package file')
    with zipfile.ZipFile(native_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=1) as z:
        for file in native_files:
            z.write(file, file.relative_to(ROOT).as_posix())
        for name in ('LICENSE', 'NOTICE', 'docs/FLEX_16K.md', 'third_party/NOTICES.runtime.txt', 'tools/build_flex_runtime.py'):
            z.write(ROOT / name, name)
    assets = [out/'vision-dataset-studio.apk', out/unsigned.name, archive, native_zip, out/'PACKAGE.json']
    (out/'SHA256SUMS').write_text(''.join(digest(p)+'  '+p.name+'\n' for p in assets), encoding='utf-8')
    print(out)


if __name__ == '__main__':
    main()

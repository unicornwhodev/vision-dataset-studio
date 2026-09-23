"""Sign verified debug/test APKs with the pinned distribution key for update QA.

This preserves the original build receipt and APKs. It does not turn a debug
build into a release build, and it does not publish anything.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

from resolve_apks import ROOT, APP_ID, resolve
sys.path.insert(0, str(ROOT / 'tools'))
from build_android import digest, sdk_dir, sdk_tool, atomic_json, verify_badging


def resolve_signed(receipt_path):
    receipt_path = Path(receipt_path).resolve()
    state = json.loads(receipt_path.read_text(encoding='utf-8'))
    pin = json.loads((ROOT / 'config/release-signing.json').read_text(encoding='utf-8'))
    if state.get('outcome') != 'signed_qualification_apks' or state.get('application_id') != APP_ID:
        raise ValueError('A completed qualification signing receipt is required.')
    if state.get('certificate_sha256') != pin['certificate_sha256'] or not state['build'].get('all_build_checks_passed'):
        raise ValueError('Wrong signing certificate or unqualified source build.')
    signer = str(sdk_tool(sdk_dir(ROOT, os.environ) / 'build-tools/36.0.0', 'apksigner'))
    result = []
    for key in ('app', 'tests'):
        item = state['artifacts'][key]
        if Path(item['file']).name != item['file']:
            raise ValueError('Unsafe artifact path.')
        path = receipt_path.parent / item['file']
        if path.stat().st_size != item['bytes'] or digest(path) != item['sha256']:
            raise ValueError('Signed APK bytes changed.')
        output = subprocess.check_output([signer, 'verify', '--print-certs', str(path)], text=True, encoding='utf-8')
        if re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', output) != [pin['certificate_sha256']]:
            raise ValueError('Signed APK certificate differs from the pinned identity.')
        result.append(path)
    return result[0], result[1], state['build']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    variables = ('VDS_RELEASE_KEYSTORE', 'VDS_RELEASE_KEY_ALIAS', 'VDS_RELEASE_STORE_PASSWORD', 'VDS_RELEASE_KEY_PASSWORD')
    if any(not os.environ.get(name) for name in variables):
        parser.error('External signing variables are required.')
    key = Path(os.environ['VDS_RELEASE_KEYSTORE']).resolve()
    if not key.is_file() or key.is_relative_to(ROOT):
        parser.error('The keystore must be outside the repository.')
    app, tests = resolve(ROOT / 'dist/android')
    build = json.loads((app.parent / 'status.json').read_text(encoding='utf-8'))
    if not build.get('all_build_checks_passed'):
        parser.error('All source build checks must have passed.')
    pin = json.loads((ROOT / 'config/release-signing.json').read_text(encoding='utf-8'))
    args.output.mkdir(parents=True, exist_ok=False)
    state = dict(outcome='running', application_id=APP_ID, build=build,
                 certificate_sha256=pin['certificate_sha256'], artifacts={}, debug_build=True)
    receipt = args.output / 'status.json'
    atomic_json(receipt, state)
    sdk = sdk_dir(ROOT, os.environ) / 'build-tools/36.0.0'
    signer = str(sdk_tool(sdk, 'apksigner'))
    try:
        for label, source, app_id in (('app', app, APP_ID), ('tests', tests, APP_ID + '.test')):
            target = args.output / source.name
            with (args.output / (label + '-sign.log')).open('wb') as log:
                subprocess.run([signer, 'sign', '--ks', str(key), '--ks-key-alias', os.environ['VDS_RELEASE_KEY_ALIAS'],
                    '--ks-pass', 'env:VDS_RELEASE_STORE_PASSWORD', '--key-pass', 'env:VDS_RELEASE_KEY_PASSWORD',
                    '--out', str(target), str(source)], stdout=log, stderr=subprocess.STDOUT, check=True)
            output = subprocess.check_output([signer, 'verify', '--verbose', '--print-certs', str(target)], text=True, encoding='utf-8')
            (args.output / (label + '-signature.txt')).write_text(output, encoding='utf-8')
            if re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', output) != [pin['certificate_sha256']]:
                raise ValueError('Certificate differs from the pinned signing identity.')
            badging = subprocess.check_output([str(sdk_tool(sdk, 'aapt')), 'dump', 'badging', str(target)], text=True, encoding='utf-8')
            verify_badging(badging, app_id)
            state['artifacts'][label] = dict(file=target.name, sha256=digest(target), bytes=target.stat().st_size,
                                            source_sha256=digest(source))
        state['outcome'] = 'signed_qualification_apks'
    except Exception as exc:
        state.update(outcome='failed', error=str(exc))
        raise
    finally:
        atomic_json(receipt, state)
    resolve_signed(receipt)
    print('Verified signed qualification APKs: ' + str(receipt))


if __name__ == '__main__':
    main()

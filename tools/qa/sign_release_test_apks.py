#!/usr/bin/env python3
"""Sign a verified minified Release/test pair with the pinned external key.

Preserves the immutable build attempt. This signs APKs; it does not qualify or
publish them. Passwords are read only by apksigner through environment variables.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from build_android import atomic_json, digest, sdk_dir, sdk_tool, verify_badging


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path, required=True)
    args = parser.parse_args()
    variables = ('VDS_RELEASE_KEYSTORE', 'VDS_RELEASE_KEY_ALIAS',
                 'VDS_RELEASE_STORE_PASSWORD', 'VDS_RELEASE_KEY_PASSWORD')
    if any(not os.environ.get(name) for name in variables):
        parser.error('External signing variables are required.')
    key = Path(os.environ['VDS_RELEASE_KEYSTORE']).resolve()
    if not key.is_file() or key.is_relative_to(ROOT):
        parser.error('The keystore must exist outside Git.')
    build = json.loads((args.build / 'status.json').read_text(encoding='utf-8'))
    if build.get('outcome') != 'release_test_apks_built':
        parser.error('A successfully built Release/test pair is required.')
    pin = json.loads((ROOT / 'config/release-signing.json').read_text(encoding='utf-8'))
    sources = {}
    for label in ('app', 'tests'):
        item = build['artifacts'][label]
        if Path(item['file']).name != item['file']:
            parser.error('Invalid artifact path.')
        source = args.build / item['file']
        if digest(source) != item['sha256'] or source.stat().st_size != item['bytes']:
            parser.error('Build artifact differs from its receipt.')
        sources[label] = source
    out = args.build / ('signing-' + uuid.uuid4().hex[:12])
    out.mkdir(exist_ok=False)
    state = dict(outcome='running', build_receipt_sha256=digest(args.build / 'status.json'),
                 source_manifest_sha256=digest(args.build / 'source-manifest.json'),
                 certificate_sha256=pin['certificate_sha256'], artifacts={},
                 production_qualified=False, tests_executed=False)
    atomic_json(out / 'status.json', state)
    sdk = sdk_dir(ROOT, os.environ) / 'build-tools/36.0.0'
    signer = str(sdk_tool(sdk, 'apksigner'))
    try:
        for label, source in sources.items():
            target = out / (label + '.apk')
            with (out / (label + '-sign.log')).open('wb') as log:
                subprocess.run([signer, 'sign', '--ks', str(key), '--ks-key-alias', os.environ['VDS_RELEASE_KEY_ALIAS'],
                    '--ks-pass', 'env:VDS_RELEASE_STORE_PASSWORD', '--key-pass', 'env:VDS_RELEASE_KEY_PASSWORD',
                    '--out', str(target), str(source)], stdout=log, stderr=subprocess.STDOUT, check=True)
            verification = subprocess.check_output([signer, 'verify', '--verbose', '--print-certs', str(target)], text=True, encoding='utf-8')
            (out / (label + '-signature.txt')).write_text(verification, encoding='utf-8')
            if re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', verification) != [pin['certificate_sha256']]:
                raise RuntimeError('Certificate differs from the pinned durable identity.')
            badging = subprocess.check_output([str(sdk_tool(sdk, 'aapt')), 'dump', 'badging', str(target)], text=True, encoding='utf-8')
            verify_badging(badging, 'com.unicornwhodev.visiondatasetstudio' + ('.test' if label == 'tests' else ''))
            if label == 'app' and 'application-debuggable' in badging:
                raise RuntimeError('Expected a non-debuggable Release.')
            state['artifacts'][label] = dict(file=target.name, sha256=digest(target),
                                            bytes=target.stat().st_size, source_sha256=digest(source))
        state['outcome'] = 'signed_release_test_apks'
    except Exception as error:
        state.update(outcome='failed', error=str(error))
        raise
    finally:
        state['finished_at'] = datetime.now(timezone.utc).isoformat()
        atomic_json(out / 'status.json', state)
        print('Evidence: ' + str(out), flush=True)


if __name__ == '__main__':
    main()

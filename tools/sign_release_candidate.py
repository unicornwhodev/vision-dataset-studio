#!/usr/bin/env python3
"""Sign a verified unsigned candidate with an explicitly chosen external key.

Set VDS_RELEASE_KEYSTORE, VDS_RELEASE_KEY_ALIAS, VDS_RELEASE_STORE_PASSWORD and
VDS_RELEASE_KEY_PASSWORD outside Git. Password values never enter argv or logs.
This does not create a key, prove its backup, or publish a release.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import uuid
from build_android import ROOT, atomic_json, digest, sdk_dir, sdk_tool


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, required=True, help='Directory returned by build_release_candidate.py')
    parser.add_argument('--certificate-sha256', required=True, help='Expected long-lived signing certificate fingerprint')
    args = parser.parse_args()
    expected = args.certificate_sha256.replace(':', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', expected):
        parser.error('An explicit SHA-256 certificate fingerprint is required.')
    pin = json.loads((ROOT / 'config/release-signing.json').read_text(encoding='utf-8'))
    if expected != pin['certificate_sha256']:
        parser.error('Certificate differs from the pinned long-lived application identity.')
    variables = ['VDS_RELEASE_KEYSTORE', 'VDS_RELEASE_KEY_ALIAS', 'VDS_RELEASE_STORE_PASSWORD', 'VDS_RELEASE_KEY_PASSWORD']
    if any(not os.environ.get(name) for name in variables):
        parser.error('All four VDS_RELEASE_* signing variables are required.')
    keystore = Path(os.environ['VDS_RELEASE_KEYSTORE']).resolve()
    if not keystore.is_file() or keystore.is_relative_to(ROOT):
        parser.error('The signing key must exist outside the repository.')
    state = json.loads((args.candidate / 'status.json').read_text(encoding='utf-8'))
    if state.get('outcome') != 'unsigned_candidate_built' or state.get('signed'):
        parser.error('A successful unsigned candidate receipt is required.')
    item = state['artifact']
    if Path(item['file']).name != item['file']:
        parser.error('Invalid artifact path.')
    source = args.candidate / item['file']
    if digest(source) != item['sha256'] or source.stat().st_size != item['bytes']:
        parser.error('Candidate bytes differ from its receipt.')
    out = args.candidate / ('signing-' + uuid.uuid4().hex[:12])
    out.mkdir(exist_ok=False)
    receipt = dict(outcome='running', unsigned_sha256=item['sha256'], expected_certificate_sha256=expected,
                   production_qualified=False, key_backup_verified=False)
    atomic_json(out / 'status.json', receipt)
    code = 1
    try:
        signer = str(sdk_tool(sdk_dir(ROOT, os.environ) / 'build-tools/36.0.0', 'apksigner'))
        target = out / 'vision-dataset-studio.apk'
        with (out / 'sign.log').open('wb') as log:
            subprocess.run([signer, 'sign', '--ks', str(keystore), '--ks-key-alias', os.environ['VDS_RELEASE_KEY_ALIAS'],
                '--ks-pass', 'env:VDS_RELEASE_STORE_PASSWORD', '--key-pass', 'env:VDS_RELEASE_KEY_PASSWORD',
                '--out', str(target), str(source)], stdout=log, stderr=subprocess.STDOUT, check=True)
        verification = subprocess.check_output([signer, 'verify', '--verbose', '--print-certs', str(target)], text=True, encoding='utf-8')
        (out / 'signature.txt').write_text(verification, encoding='utf-8')
        certificates = re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', verification)
        if certificates != [expected] or 'CN=Android Debug' in verification:
            raise RuntimeError('Unexpected or Debug certificate; signed artifact rejected.')
        receipt.update(outcome='signed_candidate', artifact=dict(file=target.name, sha256=digest(target), bytes=target.stat().st_size))
        code = 0
    except Exception as exc:
        receipt.update(outcome='failed', error=str(exc))
    finally:
        receipt.update(exit_code=code, finished_at=datetime.now(timezone.utc).isoformat())
        atomic_json(out / 'status.json', receipt)
        print(f'Evidence: {out}')
    return code


if __name__ == '__main__':
    raise SystemExit(main())

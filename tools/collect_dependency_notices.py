#!/usr/bin/env python3
"""Inventory licenses declared by resolved runtime POMs and retain embedded notices.

This is an evidence inventory, not a legal clearance or a replacement for upstream
native-source notices. Missing metadata remains visible and blocks completeness.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def notices(data, prefix=''):
    found = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in sorted(archive.namelist()):
            if re.search(r'(^|/)(NOTICE|LICENSE|COPYING|COPYRIGHT)([._-]|$)', name, re.I) and not name.endswith('/'):
                found.append((prefix + name, archive.read(name).decode('utf-8', errors='replace')))
            elif name == 'classes.jar' or (name.startswith('libs/') and name.endswith('.jar')):
                found.extend(notices(archive.read(name), prefix + name + '!/'))
    return found


def license_metadata(pom, depth=0):
    if not pom or not pom.is_file() or depth > 8:
        return [], []
    ns = {'p': 'http://maven.apache.org/POM/4.0.0'}
    root = ET.parse(pom).getroot()
    provenance = [dict(file=pom.name, sha256=sha(pom))]
    licenses = [{key: license.findtext('p:' + key, default='', namespaces=ns) for key in ('name', 'url', 'distribution')}
                for license in root.findall('p:licenses/p:license', ns)]
    if not licenses:
        parent = root.find('p:parent', ns)
        if parent is not None:
            parts = [parent.findtext('p:' + key, default='', namespaces=ns) for key in ('groupId', 'artifactId', 'version')]
            if all(re.fullmatch(r'[A-Za-z0-9_.-]+', part) for part in parts):
                candidates = list(pom.parents[4].joinpath(*parts).glob('*/*.pom'))
                if len(candidates) == 1:
                    licenses, ancestors = license_metadata(candidates[0], depth + 1)
                    provenance.extend(ancestors)
    return licenses, provenance


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, default=Path('dist/runtime-dependencies.json'))
    parser.add_argument('--output', type=Path, default=Path('dist/dependency-notices'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    rows, texts = [], []
    for item in json.loads(args.inventory.read_text(encoding='utf-8')):
        artifact = Path(item['artifact'])
        pom = Path(item['pom']) if item.get('pom') else None
        licenses, provenance = license_metadata(pom)
        embedded = notices(artifact.read_bytes()) if zipfile.is_zipfile(artifact) else []
        rows.append(dict(coordinate=item['coordinate'], artifact_sha256=sha(artifact),
                         pom_provenance=provenance, declared_licenses=licenses,
                         embedded_notices=[n for n, _ in embedded]))
        texts.append('\n' + '=' * 78 + '\n' + item['coordinate'] + '\n' + '=' * 78 + '\n')
        for license in licenses:
            texts.append(f"Declared license: {license['name']} ({license['url']})\n")
        for name, text in embedded:
            texts.append(f'\n--- {name} ---\n{text}\n')
    missing = [row['coordinate'] for row in rows if not row['declared_licenses']]
    result = dict(artifacts=len(rows), missing_license_metadata=missing,
                  legal_clearance=False, native_transitive_notices_reviewed=False, dependencies=rows)
    (args.output / 'inventory.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    (args.output / 'NOTICES.txt').write_text('Resolved runtime dependencies and verbatim embedded notices.\n' + ''.join(texts), encoding='utf-8')
    print(f'{len(rows)} artifacts; {len(missing)} with missing license metadata. Native transitive review remains required.')


if __name__ == '__main__':
    main()

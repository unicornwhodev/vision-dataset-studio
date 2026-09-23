#!/usr/bin/env python3
"""Preserve a real app database across process death and same-signature reinstall.

Dedicated synthetic installation only. Backups stay local and contain private app
data. This tests schema 4 -> 4 replacement, not an unavailable historical database.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tarfile
import uuid
from resolve_apks import APP_ID, ROOT, resolve
from run_device_qualification import parse_instrumentation


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
    args = parser.parse_args()
    if not args.serial or os.environ.get('VDS_ALLOW_TEST_INSTALL') != '1':
        parser.error('Select a dedicated serial and set VDS_ALLOW_TEST_INSTALL=1.')
    case = uuid.uuid4().hex[:12]
    out = ROOT / 'test-results' / ('data-preservation-' + case)
    out.mkdir(parents=True, exist_ok=False)
    adb = ['adb', '-s', args.serial]
    state = dict(case=case, serial=args.serial, outcome='running', schema_transition='4 -> 4',
                 historical_migration_qualified=False, backup_readback_verified=False)

    def run(name, command):
        with (out / name).open('wb') as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
        return (out / name).read_text(encoding='utf-8', errors='replace')

    def instrument(method):
        output = run(method + '.txt', [*adb, 'shell', 'am', 'instrument', '-w', '-r',
            '-e', 'class', APP_ID + '.InstalledDataPreservationTest#' + method,
            '-e', 'preservationCase', case, APP_ID + '.test/androidx.test.runner.AndroidJUnitRunner'])
        parsed = parse_instrumentation(output)
        state[method] = parsed
        if not parsed['complete'] or parsed['passed'] != 1:
            raise RuntimeError('Preservation instrumentation failed: ' + method)

    code = 1
    try:
        app, tests = resolve(ROOT / 'dist/android')
        state['build'] = json.loads((app.parent / 'status.json').read_text(encoding='utf-8'))
        if not state['build'].get('all_build_checks_passed'):
            raise RuntimeError('Build checks have not passed.')
        for name, apk in [('main', app), ('tests', tests)]:
            run('install-' + name + '.txt', [*adb, 'install', '--no-streaming', '-r', str(apk)])
        instrument('preparePersistentHumanAnnotation')
        run('stop.txt', [*adb, 'shell', 'am', 'force-stop', APP_ID])
        backup = out / 'private-app-backup.tar'
        with backup.open('wb') as stream:
            subprocess.run([*adb, 'exec-out', 'run-as', APP_ID, 'tar', '-cf', '-', 'databases', 'files', 'shared_prefs'],
                           stdout=stream, stderr=subprocess.PIPE, check=True)
        state['backup_sha256'] = digest(backup)
        copied = out / 'backup-readback'
        copied.mkdir()
        with tarfile.open(backup) as archive:
            expected = json.load(archive.extractfile(f'files/qa-evidence/preservation/{case}.json'))
            for name in ('vision_dataset_studio.db', 'vision_dataset_studio.db-wal', 'vision_dataset_studio.db-shm'):
                try:
                    content = archive.extractfile('databases/' + name)
                except KeyError:
                    if name.endswith('.db'):
                        raise
                    continue
                (copied / name).write_bytes(content.read())
        with sqlite3.connect((copied / 'vision_dataset_studio.db').as_uri() + '?mode=ro', uri=True) as db:
            assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
            assert db.execute('SELECT lastRowCursor FROM projects WHERE id=?', (expected['project_id'],)).fetchone()[0] == expected['cursor']
            assert db.execute('SELECT dataJson FROM annotations WHERE sampleId=?', (expected['sample_id'],)).fetchone()[0] == expected['annotation_json']
        state['backup_readback_verified'] = True
        run('replace-package.txt', [*adb, 'install', '--no-streaming', '-r', str(app)])
        instrument('verifyAfterProcessDeathAndPackageReplacement')
        assert digest(backup) == state['backup_sha256']
        state['outcome'] = 'preserved_after_package_replacement'
        code = 0
    except Exception as exc:
        state.update(outcome='failed', error=str(exc))
        print(f'FAILED: {exc}')
    finally:
        state.update(exit_code=code, finished_at=datetime.now(timezone.utc).isoformat())
        (out / 'status.json').write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')
        print(f'Private local evidence: {out}')
    return code


if __name__ == '__main__':
    raise SystemExit(main())

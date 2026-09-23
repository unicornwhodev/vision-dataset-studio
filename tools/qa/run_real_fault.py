#!/usr/bin/env python3
"""Opt-in OS/Hub fault qualification on a dedicated, rooted emulator.

Never resets the app, uses a user corpus, disables SELinux, or deletes a Hub repo.
Network rules affect only this app UID and are removed in finally. HF writes need
the exact, separately authorized NEW QA repository and --allow-hf-writes.
Private receipts stay in ignored test-results. A killed phase is not a passed test.
"""
import argparse
import io
import json
from pathlib import Path
import re
import subprocess
import time
import tarfile
import uuid
from resolve_apks import APP_ID, ROOT
from run_device_qualification import parse_instrumentation


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--scenario', required=True, choices=['enospc', 'download', 'purge', 'hf-live', 'hf-lost-response', 'hf-conflict'])
    parser.add_argument('--case', default=uuid.uuid4().hex[:12])
    parser.add_argument('--repo')
    parser.add_argument('--allow-hf-writes', action='store_true')
    parser.add_argument('--reuse-qa-repo', action='store_true', help='Reuse only the repository newly created for this authorized QA session.')
    args = parser.parse_args()
    if not re.fullmatch('[a-f0-9]{12}', args.case):
        parser.error('Case must contain 12 lowercase hexadecimal characters.')
    if args.scenario.startswith('hf-') and (not args.allow_hf_writes or not args.repo or not re.fullmatch(
            r'[A-Za-z0-9][A-Za-z0-9_-]*/vision-dataset-studio-qa-[0-9]{8}[a-z0-9-]*', args.repo)):
        parser.error('Explicit authorization and the exact private QA repository are required.')
    out = ROOT / 'test-results' / f'real-fault-{args.scenario}-{args.case}'
    out.mkdir(parents=True, exist_ok=False)
    adb = ['adb', '-s', args.serial]
    state = dict(scenario=args.scenario, case=args.case, outcome='running', emulator_only=True, tests={})
    running = None
    rules = []
    mounted = None

    def command(*parts, timeout=30, check=True, data=None):
        result = subprocess.run([*adb, *parts], input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if check and result.returncode:
            # Avoid including arbitrary tool output or network credentials in exceptions.
            raise RuntimeError(f'ADB command failed with exit {result.returncode}: {parts[:3]}')
        return result.stdout.decode('utf-8', errors='replace')

    def stage_token():
        from huggingface_hub import get_token
        token = get_token()
        if not token:
            raise RuntimeError('A local HF credential is required; never pass it on the command line.')
        command('shell', 'run-as', APP_ID, 'mkdir', '-p', 'files/qa-private')
        # stdin only. The instrumentation consumes and removes this private, app-owned file.
        payload = token.encode()
        uid = int(command('shell', 'run-as', APP_ID, 'id', '-u'))
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode='w') as archive:
            info = tarfile.TarInfo('files/qa-private/hf-token'); info.size = len(payload); info.mode = 0o600; info.uid = info.gid = uid
            archive.addfile(info, io.BytesIO(payload))
        command('exec-in', 'run-as', APP_ID, 'tar', '-x', '-f', '-', data=stream.getvalue())

    def start(class_name, method, options=None):
        nonlocal running
        if class_name.startswith('Hf') and class_name != 'HfInterruptedDownloadTest':
            stage_token()
        opts = ['-e', 'class', f'{APP_ID}.{class_name}#{method}', '-e', 'faultCase', args.case]
        if args.repo:
            opts += ['-e', 'hfLiveQa', 'authorized', '-e', 'hfQaRepository', args.repo]
        if args.reuse_qa_repo:
            opts += ['-e', 'hfReuseQa', 'true']
        for key, value in (options or {}).items():
            opts += ['-e', key, value]
        log = (out / (method + '.txt')).open('wb')
        running = subprocess.Popen([*adb, 'shell', 'am', 'instrument', '-w', '-r', *opts,
            APP_ID + '.test/androidx.test.runner.AndroidJUnitRunner'], stdout=log, stderr=subprocess.STDOUT)
        log.close()
        print('Running ' + method, flush=True)

    def finish(method, timeout=180, expected_kill=False):
        nonlocal running
        running.wait(timeout=timeout)
        running = None
        parsed = parse_instrumentation((out / (method + '.txt')).read_text(encoding='utf-8', errors='replace'))
        state['tests'][method] = parsed
        if expected_kill:
            if parsed['complete']:
                raise RuntimeError('The process-kill phase unexpectedly completed.')
        elif not parsed['complete'] or parsed['passed'] != 1:
            raise RuntimeError('Instrumentation did not pass: ' + method)

    def wait_marker(path, timeout=150):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            value = command('shell', 'run-as', APP_ID, 'cat', path, check=False).strip()
            if value.startswith('{'):
                json.loads(value)
                (out / Path(path).name).write_text(value + '\n', encoding='utf-8')
                return
            if running and running.poll() is not None:
                raise RuntimeError('Instrumentation ended before the required boundary: ' + Path(path).name)
            time.sleep(.25)
        raise TimeoutError('Required boundary not reached: ' + Path(path).name)

    code = 1
    try:
        if command('shell', 'getprop', 'ro.kernel.qemu').strip() != '1' or command('shell', 'id', '-u').strip() != '0':
            raise RuntimeError('Real OS fault injection is restricted to a dedicated rooted emulator.')
        if command('shell', 'getenforce').strip() != 'Enforcing':
            raise RuntimeError('SELinux must stay Enforcing.')
        state['page_size'] = int(command('shell', 'getconf', 'PAGE_SIZE').strip())
        packages = command('shell', 'cmd', 'package', 'list', 'packages', '-U', APP_ID)
        uid = re.search(r'^package:' + re.escape(APP_ID) + r' uid:(\d+)\s*$', packages, re.M).group(1)
        if args.scenario == 'enospc':
            relative = f'files/qa-evidence/external-faults/{args.case}/quota'
            path = '/data/user/0/' + APP_ID + '/' + relative
            command('shell', 'run-as', APP_ID, 'mkdir', '-p', relative)
            label = command('shell', 'ls', '-Zd', path).split()[0]
            if not label.startswith('u:object_r:app_data_file:s0:'):
                raise RuntimeError('Unexpected app SELinux label.')
            command('shell', 'mount', '-t', 'tmpfs', '-o', 'size=1m', 'vds-qa-' + args.case, path)
            mounted = path
            command('shell', 'chown', f'{uid}:{uid}', path)
            command('shell', 'chcon', label, path)
            method = 'realEnospcPreservesPreviousAtomicExport'
            start('ExternalFaultQualificationTest', method, {'quotaMounted':'true'})
            finish(method)
            wait_marker(f'files/qa-evidence/external-faults/{args.case}/enospc.json')
        elif args.scenario == 'download':
            root = f'files/qa-evidence/hf-interruption/{args.case}'
            method = 'downloadDuringRealPacketDropPreservesOriginalAndCheckpoint'
            start('HfInterruptedDownloadTest', method)
            wait_marker(root + '/network-cut-requested.json')
            for tool in ('iptables', 'ip6tables'):
                rule = ['OUTPUT', '-m', 'owner', '--uid-owner', uid, '-m', 'comment', '--comment', 'vds-qa-' + args.case, '-j', 'DROP']
                command('shell', tool, '-I', *rule)
                rules.append((tool, rule))
            command('shell', 'run-as', APP_ID, 'touch', root + '/network-cut-installed')
            finish(method, timeout=60)
            wait_marker(root + '/interrupted.json')
            while rules:
                tool, rule = rules[-1]
                command('shell', tool, '-D', *rule)
                rules.pop()
            method = 'resumeAfterNetworkRecoveryMatchesPublishedSha256'
            start('HfInterruptedDownloadTest', method)
            finish(method)
            wait_marker(root + '/resumed.json')
        elif args.scenario == 'purge':
            root = f'files/qa-evidence/purge-death/{args.case}'
            method = 'prepareAndPurgeUntilHostKillsStoppedProcess'
            start('PurgeProcessDeathTest', method)
            wait_marker(root + '/purge-cut.json')
            command('shell', 'am', 'force-stop', APP_ID)
            finish(method, expected_kill=True)
            method = 'resumePurgeRetainsHumanAnnotationsAndBackupReceipt'
            start('PurgeProcessDeathTest', method)
            finish(method)
            wait_marker(root + '/resumed.json')
        elif args.scenario == 'hf-live':
            method = 'privateQaPublicationConcurrentReservationsAndReadback'
            start('HfLivePublicationTest', method)
            finish(method, timeout=300)
            wait_marker(f'files/qa-evidence/hf-live/{args.case}/result.json')
        elif args.scenario == 'hf-lost-response':
            root = f'files/qa-evidence/hf-faults/{args.case}'
            method = 'prepareAndPublishAwaitingRealProcessKill'
            start('HfFaultPublicationTest', method)
            wait_marker(root + '/server-accepted.json', timeout=180)
            command('shell', 'am', 'force-stop', APP_ID)
            finish(method, expected_kill=True)
            state['real_process_kill_after_commit_acceptance'] = True
            method = 'reconcileLostResponseWithoutDuplicateCommit'
            start('HfFaultPublicationTest', method)
            finish(method, timeout=240)
            wait_marker(root + '/reconciled.json')
        else:
            method = 'staleParentIsRejectedByRealHub'
            start('HfFaultPublicationTest', method)
            finish(method)
            wait_marker(f'files/qa-evidence/hf-faults/{args.case}/conflict.json')
        state['outcome'] = 'passed'
        code = 0
    except Exception as exc:
        state.update(outcome='failed', error=str(exc))
        print('FAILED: ' + str(exc), flush=True)
    finally:
        cleanup_errors = []
        if running and running.poll() is None:
            try:
                command('shell', 'am', 'force-stop', APP_ID)
                running.wait(timeout=10)
            except Exception as exc:
                running.kill()
                cleanup_errors.append(type(exc).__name__)
        for tool, rule in reversed(rules):
            try: command('shell', tool, '-D', *rule)
            except Exception: cleanup_errors.append(tool + ' rule remains: vds-qa-' + args.case)
        if mounted:
            try: command('shell', 'umount', mounted)
            except Exception: cleanup_errors.append('Limited tmpfs remains mounted: ' + mounted)
        if args.scenario.startswith('hf-'):
            try: command('shell', 'run-as', APP_ID, 'rm', '-f', 'files/qa-private/hf-token')
            except Exception: cleanup_errors.append('Private test credential removal unconfirmed')
        state['cleanup_errors'] = cleanup_errors
        if cleanup_errors:
            state['outcome'] = 'cleanup_failed'
            code = 1
        (out / 'status.json').write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')
        print('Private evidence: ' + str(out), flush=True)
    return code


if __name__ == '__main__':
    raise SystemExit(main())

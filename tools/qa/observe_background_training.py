#!/usr/bin/env python3
"""Observe a user-started Release training service after Home, without keeping it alive.

This is observation evidence only. Verify durable progress/data separately after
the interval; a surviving process alone is not a successful training run.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import time

APP = 'com.unicornwhodev.visiondatasetstudio'


def app_task_visibility(activities):
    """Reject unknown visibility; OEM dumps can contain unrelated visible tasks."""
    tasks = [line.strip() for line in activities.splitlines()
             if re.search(r'Task\{.* A=\d+:' + re.escape(APP) + r'\s', line)]
    if not tasks or any(not re.search(r'\bvisible=(true|false)\b', line) for line in tasks):
        raise RuntimeError('Could not determine visibility of the app task.')
    visible = any(re.search(r'\b(?:visible|visibleRequested)=true\b', line) for line in tasks)
    return visible, tasks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=720)
    args = parser.parse_args()
    if args.seconds < 600:
        parser.error('The prolonged observation must last at least ten minutes.')
    args.output.mkdir(parents=True, exist_ok=False)
    adb = ['adb', '-s', args.serial]
    state = dict(outcome='running', started_at=datetime.now(timezone.utc).isoformat(),
                 instrumentation=False, freezing_exemption=False,
                 training_qualified=False, samples=[])

    def command(*parts):
        return subprocess.check_output([*adb, *parts], timeout=20).decode('utf-8', errors='replace')

    def save():
        (args.output / 'status.json').write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')

    save()
    try:
        package = command('shell', 'dumpsys', 'package', APP)
        if 'DEBUGGABLE' in package:
            raise RuntimeError('The installed app must be the non-debuggable Release.')
        service = command('shell', 'dumpsys', 'activity', 'services', APP)
        if 'isForeground=true foregroundId=4102' not in service:
            raise RuntimeError('Start real training from the app before observing.')
        (args.output / 'memory-before.txt').write_text(command('shell', 'dumpsys', 'meminfo', APP))
        command('shell', 'input', 'keyevent', '3')
        start = time.monotonic()
        while True:
            elapsed = time.monotonic() - start
            pid = command('shell', 'pidof', APP).strip()
            service = command('shell', 'dumpsys', 'activity', 'services', APP)
            activities = command('shell', 'dumpsys', 'activity', 'activities', APP)
            # Some OEMs ignore dumpsys' package filter. Inspect only this app's
            # task records, never another application's visible activity.
            visible, tasks = app_task_visibility(activities)
            sample = dict(elapsed_seconds=round(elapsed, 3), pid=pid,
                          training_service='isForeground=true foregroundId=4102' in service,
                          app_visible=visible, app_tasks=tasks)
            state['samples'].append(sample)
            (args.output / f'service-{len(state["samples"]):03}.txt').write_text(service)
            save()
            print(json.dumps(sample), flush=True)
            if visible or not sample['training_service'] or not pid:
                raise RuntimeError('App visible or training service/process stopped during observation.')
            if elapsed >= args.seconds:
                break
            time.sleep(min(30, args.seconds - elapsed))
        (args.output / 'memory-after.txt').write_text(command('shell', 'dumpsys', 'meminfo', APP))
        state['outcome'] = 'observation_completed'
    except Exception as error:
        state.update(outcome='failed', error=str(error))
        raise
    finally:
        state['finished_at'] = datetime.now(timezone.utc).isoformat()
        save()


if __name__ == '__main__':
    main()

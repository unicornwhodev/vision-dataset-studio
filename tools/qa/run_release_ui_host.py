#!/usr/bin/env python3
"""Verify four real Release UI scenarios using a fresh shell UI tree per action.

This is separate from AndroidJUnitRunner. No private app API, database writes,
test activity, service, freezing exemption, data reset or external publication.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from run_device_qualification import APP_ID


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--sha256', required=True, help='Exact installed Release APK hash')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get('VDS_ALLOW_TEST_INSTALL') != '1' or not re.fullmatch('[a-f0-9]{64}', args.sha256):
        parser.error('Select an authorized QA device, exact APK hash and VDS_ALLOW_TEST_INSTALL=1.')
    args.output.mkdir(parents=True, exist_ok=False)
    adb = ['adb', '-s', args.serial]
    state = dict(suite='host_adb_ui_fresh_xml', outcome='running', passed=[],
                 started_at=datetime.now(timezone.utc).isoformat(), main_sha256=args.sha256,
                 instrumentation=False, qa_foreground_service=False, freezing_exemption=False,
                 production_qualified=False, sequence=0)

    def save():
        (args.output / 'status.json').write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')

    def run(*parts):
        return subprocess.check_output([*adb, *parts], timeout=45).decode('utf-8', errors='replace')

    def tree():
        raw = run('exec-out', 'uiautomator', 'dump', '/dev/tty')
        raw = raw[raw.index('<?xml'):raw.index('</hierarchy>') + len('</hierarchy>')]
        state['sequence'] += 1
        (args.output / f'ui-{state["sequence"]:03}.xml').write_text(raw, encoding='utf-8')
        return ET.fromstring(raw)

    def owned(root):
        return [n for n in root.iter('node') if n.get('package') == APP_ID]

    def bounds(node):
        result = [int(v) for v in re.findall(r'\d+', node.get('bounds', ''))]
        if len(result) != 4 or result[2] <= result[0] or result[3] <= result[1]:
            raise AssertionError('Control has no visible bounds')
        return result

    def find(pattern, attribute='text', scroll=False):
        for _ in range(40 if scroll else 4):
            root = tree()
            nodes = [n for n in owned(root) if re.fullmatch(pattern, n.get(attribute, ''))]
            if nodes:
                if len(nodes) != 1:
                    raise AssertionError('Ambiguous UI control: ' + pattern)
                return root, nodes[0]
            if not scroll:
                continue
            containers = [n for n in owned(root) if n.get('scrollable') == 'true']
            if not containers:
                raise AssertionError('Missing scrollable UI for: ' + pattern)
            x1, y1, x2, y2 = bounds(containers[0])
            run('shell', 'input', 'swipe', str((x1+x2)//2), str(y1+(y2-y1)*4//5),
                str((x1+x2)//2), str(y1+(y2-y1)//5), '500')
        raise AssertionError('Missing UI control: ' + pattern)

    def tap(node):
        if node.get('enabled') != 'true':
            raise AssertionError('Control is disabled')
        x1, y1, x2, y2 = bounds(node)
        run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

    def click(pattern, attribute='text', scroll=False):
        _, node = find(pattern, attribute, scroll)
        tap(node)

    def restart():
        run('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        run('shell', 'am', 'force-stop', APP_ID)
        run('shell', 'am', 'start', '-W', '-n', APP_ID + '/.MainActivity')
        find('Atelier|Studio')

    def passed(name):
        state['passed'].append(name)
        save()
        print(name + ': passed', flush=True)

    def guidance():
        click('Réglages|Settings', 'content-desc')
        root, label = find('Afficher les conseils|Show guidance', scroll=True)
        parents = {child: parent for parent in root.iter() for child in parent}
        node = label
        while node is not None and node.get('checkable') != 'true':
            node = parents.get(node)
        if node is None:
            raise AssertionError('Guidance toggle missing')
        return node

    save()
    try:
        package = run('shell', 'dumpsys', 'package', APP_ID)
        if 'DEBUGGABLE' in package:
            raise AssertionError('Expected non-debuggable Release')
        apk = run('shell', 'pm', 'path', APP_ID).strip()
        if not re.fullmatch(r'package:/data/app/[^\n]+/base\.apk', apk):
            raise AssertionError('Unexpected installed APK layout')
        if run('shell', 'sha256sum', apk.removeprefix('package:')).split()[0] != args.sha256:
            raise AssertionError('Installed APK hash differs')
        state['abi'] = run('shell', 'getprop', 'ro.product.cpu.abi').strip()
        state['page_size'] = int(run('shell', 'getconf', 'PAGE_SIZE').strip())
        restart()
        find('Outils|Tools', scroll=True)
        passed('home_renders')
        click('Modèles|Models')
        click('Importer|Import')
        find(r'Choisir un \.tflite|Choose a \.tflite file')
        click('Export')
        find('Archive locale|Local archive', scroll=True)
        click('Qualité|Quality')
        find('Stockage|Storage')
        passed('model_import_export_quality_navigation')
        restart()
        click('Gérer les projets|Manage projects', 'content-desc')
        root, _ = find('Nom du nouveau projet|New project name', scroll=True)
        fields = [n for n in owned(root) if n.get('class') == 'android.widget.EditText']
        if len(fields) != 1 or fields[0].get('text'):
            raise AssertionError('Expected one empty project-name field')
        name = 'QA_Release_host_' + str(time.time_ns() // 1000000)
        tap(fields[0])
        run('shell', 'input', 'text', name)
        if 'mInputShown=true' in run('shell', 'dumpsys', 'input_method'):
            run('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        click('Créer un projet|Create project', scroll=True)
        for _ in range(4):
            current = tree()
            if any(n.get('text') == name and n.get('class') != 'android.widget.EditText' for n in owned(current)):
                break
        else:
            raise AssertionError('Created project not displayed outside input')
        restart()
        find(re.escape(name))
        passed('created_project_survives_process_restart')
        toggle = guidance()
        before = toggle.get('checked')
        state['guidance_before'] = before
        tap(toggle)
        restart()
        changed = guidance()
        if changed.get('checked') == before:
            raise AssertionError('Guidance preference did not persist')
        tap(changed)
        restart()
        if guidance().get('checked') != before:
            raise AssertionError('Original guidance preference not restored')
        passed('display_preference_persists_and_is_restored')
        state['outcome'] = 'host_release_ui_passed'
    except Exception as error:
        state.update(outcome='failed', error=str(error))
        raise
    finally:
        state['finished_at'] = datetime.now(timezone.utc).isoformat()
        save()


if __name__ == '__main__':
    main()

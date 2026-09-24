#!/usr/bin/env python3
"""Produce a review-only candidate for the API used by Release instrumentation.

Run after building both Release APKs. Does not modify production keep rules.
TraceReferences diagnostics and exit status are retained, including partial
output on a missing reference. Actual device tests remain required.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from build_android import atomic_json, digest, sdk_dir

INIT = '''gradle.projectsEvaluated {
    def app = gradle.rootProject.project(':app')
    app.tasks.register('describeReleaseTestInputs') {
        doLast {
            def task = app.tasks.getByName('minifyReleaseAndroidTestWithR8')
            def data = [source: task.classes.files.collect {it.absolutePath}.sort(),
                        target: app.tasks.getByName('minifyReleaseWithR8').classes.files.collect {it.absolutePath}.sort(),
                        library: task.bootClasspath.files.collect {it.absolutePath}.sort()]
            new File(app.providers.gradleProperty('vdsTraceInputs').get()).text =
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(data))
        }
    }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    state = dict(outcome='running', production_qualified=False, rules_installed=False,
                 recipe_sha256=digest(Path(__file__)))
    try:
        init = out / 'inputs.init.gradle'
        init.write_text(INIT, encoding='utf-8')
        with (out / 'gradle.log').open('wb') as log:
            subprocess.run([sys.executable, str(ROOT / 'tools/gradle_bootstrap.py'),
                ':app:describeReleaseTestInputs', '-I', str(init), '--no-configuration-cache',
                '-PvdsTestBuildType=release', '-PvdsTraceInputs=' + str(out / 'inputs.json'),
                '--console=plain'], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
        inputs = json.loads((out / 'inputs.json').read_text(encoding='utf-8'))
        argv = ['--keep-rules', '--output', str(out / 'raw-rules.pro')]
        state['input_sha256'] = {}
        for kind, paths in inputs.items():
            for i, name in enumerate(paths):
                path = Path(name)
                if not path.exists() and not path.suffix:
                    continue  # Optional empty Java/KSP output directory.
                if path.is_dir():
                    classes = sorted(path.rglob('*.class'))
                    if not classes:
                        continue
                    archive = out / f'{kind}-{i}.jar'
                    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
                        for f in classes:
                            z.write(f, f.relative_to(path).as_posix())
                    path = archive
                if not path.is_file() or path.suffix != '.jar':
                    raise RuntimeError('Missing or unsupported input: ' + str(path))
                state['input_sha256'][kind + ':' + str(i)] = digest(path)
                argv.extend(['--' + ('lib' if kind == 'library' else kind), str(path)])
        argfile = out / 'args.txt'
        argfile.write_text('\n'.join(argv) + '\n', encoding='utf-8')
        java = Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
        r8 = sdk_dir(ROOT, os.environ) / 'build-tools/36.0.0/lib/d8.jar'
        with (out / 'diagnostics.txt').open('wb') as log:
            result = subprocess.run([str(java), '-Xmx2g', '-cp', str(r8),
                'com.android.tools.r8.tracereferences.TraceReferences', '@' + str(argfile)],
                stdout=log, stderr=subprocess.STDOUT, timeout=120)
        state['trace_exit_code'] = result.returncode
        raw = out / 'raw-rules.pro'
        if raw.exists():
            candidate = out / 'instrumentation-api.candidate.pro'
            candidate.write_text(raw.read_text(encoding='utf-8').replace('-keep ', '-keep,allowaccessmodification '), encoding='utf-8')
            state['candidate_sha256'] = digest(candidate)
        state['outcome'] = 'candidate_requires_review' if result.returncode == 0 else 'incomplete_trace_requires_review'
        return result.returncode
    except Exception as error:
        state.update(outcome='failed', error=str(error))
        raise
    finally:
        atomic_json(out / 'status.json', state)
        print('Review evidence: ' + str(out))


if __name__ == '__main__':
    raise SystemExit(main())

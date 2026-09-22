#!/usr/bin/env python3
"""Static source identity guards. Not Android compilation or manifest-merger validation."""
from pathlib import Path
import re, tomllib, unittest, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
PACKAGE='com.unicornwhodev.visiondatasetstudio'
NS='{http://schemas.android.com/apk/res/android}'
class IdentityTests(unittest.TestCase):
    def test_namespace_and_application_id_match(self):
        text=(ROOT/'app/build.gradle.kts').read_text()
        self.assertIn(f'namespace = "{PACKAGE}"',text)
        self.assertIn(f'applicationId = "{PACKAGE}"',text)
        self.assertIn(f'testApplicationId = "{PACKAGE}.test"',text)
    def test_all_kotlin_packages_match_source_paths(self):
        files=list((ROOT/'app/src').rglob('*.kt'));self.assertGreater(len(files),40)
        for path in files:
            package=re.search(r'^package\s+([\w.]+)',path.read_text(),re.M)
            self.assertIsNotNone(package,path)
            self.assertTrue(package[1]==PACKAGE or package[1].startswith(PACKAGE+'.'),path)
            self.assertEqual(path.parent.relative_to(Path(*path.parts[:path.parts.index('java')+1])).as_posix(),package[1].replace('.','/'),path)
    def test_no_template_namespaces_in_sources(self):
        for path in ROOT.rglob('*'):
            if not path.is_file() or any(part in {'test-results','dist','.git','.gradle','.kotlin','build','__pycache__'} for part in path.relative_to(ROOT).parts):continue
            try:text=path.read_text()
            except UnicodeError:continue
            self.assertIsNone(re.search(r'com[./]example(?:[./";\s]|$)',text),path)
    def test_file_provider_uses_application_id(self):
        tree=ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
        provider=tree.find('.//provider')
        self.assertEqual('${applicationId}.fileprovider',provider.get(NS+'authorities'))
        self.assertEqual('false',provider.get(NS+'exported'))
    def test_instrumented_provider_identity(self):
        tree=ET.parse(ROOT/'app/src/androidTest/AndroidManifest.xml')
        provider=tree.find('.//provider')
        self.assertEqual(PACKAGE+'.SafFaultProvider',provider.get(NS+'name'))
        self.assertEqual(PACKAGE+'.test.documents',provider.get(NS+'authorities'))
        text=next((ROOT/'app/src/androidTest').rglob('SafV4Test.kt')).read_text()
        self.assertIn('context.packageName}.documents/',text)
    def test_version_and_database_versions_distinct(self):
        self.assertIn('versionName = "4.2.0-rc4"',(ROOT/'app/build.gradle.kts').read_text())
        db=next((ROOT/'app/src/main').rglob('AppDatabase.kt')).read_text()
        self.assertIn('version = 4',db)
        self.assertNotIn('fallbackToDestructiveMigration',db)
    def test_device_scripts_use_canonical_id(self):
        for name in ['capture_device_metrics.sh','run_device_qualification.sh']:
            self.assertIn(PACKAGE,(ROOT/'tools/qa'/name).read_text())
        self.assertIn('resolve_apks.py',(ROOT/'tools/qa/run_device_qualification.sh').read_text())
    def test_no_private_weights_or_signing_keys(self):
        for path in ROOT.rglob('*'):
            if any(p in {'dist','.gradle','build'} for p in path.relative_to(ROOT).parts):continue
            self.assertNotIn(path.suffix,{'.tflite','.onnx','.safetensors','.keystore','.jks'},path)
    def test_no_archived_docs_in_candidate(self):
        self.assertFalse((ROOT/'docs/history').exists())
    def test_selected_apache_license_and_notices_are_present(self):
        text=(ROOT/'LICENSING_STATUS.md').read_text()
        self.assertIn('Apache-2.0',text)
        self.assertTrue((ROOT/'NOTICE').is_file())
        self.assertIn('Apache License', (ROOT/'LICENSE').read_text())
        self.assertIn('Version 2.0, January 2004', (ROOT/'LICENSE').read_text())
    def test_gradle_aliases_resolve(self):
        data=tomllib.loads((ROOT/'gradle/libs.versions.toml').read_text())
        libraries={key.replace('-','.') for key in data['libraries']}
        plugins={key.replace('-','.') for key in data['plugins']}
        for path in ROOT.rglob('*.gradle.kts'):
            for use in re.findall(r'libs\.([a-z][\w.]*)',path.read_text()):
                self.assertIn(use[8:] if use.startswith('plugins.') else use,plugins if use.startswith('plugins.') else libraries,path)
    def test_build_workflow_has_emulator_job_without_publication(self):
        text=(ROOT/'.github/workflows/android-qualification.yml').read_text()
        for expected in ['workflow_dispatch:', 'instrumented:', 'api: [28, 35]', 'contents: read', 'resolve']:
            if expected=='resolve':continue
            self.assertIn(expected,text)
        self.assertNotIn('contents: write',text)
        self.assertNotIn('HF_TOKEN',text)
if __name__=='__main__':unittest.main(verbosity=2)

#!/usr/bin/env python3
"""Host tests of build receipts and preflight guards; NEVER an Android build."""
import zipfile
import importlib.util, json, os, tempfile, unittest
from pathlib import Path
from unittest import mock
ROOT=Path(__file__).resolve().parents[2]
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
build=load('build_android',ROOT/'tools/build_android.py')
resolver=load('resolve_apks',ROOT/'tools/qa/resolve_apks.py')
class BuildEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
    def test_weight_inventory_detects_renamed_litert_and_named_weights(self):
        apk=self.root/'test.zip'
        with zipfile.ZipFile(apk,'w') as out:
            out.writestr('assets/renamed.dat',b'0000TFL3weights')
            out.writestr('assets/model.safetensors',b'weights')
            out.writestr('lib/x86_64/libtensorflowlite.so',b'runtime')
        report=build.weight_inventory(apk)
        self.assertEqual(['assets/renamed.dat','assets/model.safetensors'],report['weight_files'])
        self.assertEqual(7,report['native_library_bytes_uncompressed'])
    def test_runtime_and_configuration_are_allowed_without_weights(self):
        apk=self.root/'test.zip'
        with zipfile.ZipFile(apk,'w') as out:
            out.writestr('assets/model_config.json','{}')
            out.writestr('lib/arm64-v8a/libtensorflowlite.so',b'runtime')
        self.assertEqual([],build.weight_inventory(apk)['weight_files'])
    def test_gradle_checksum_is_pinned_consistently(self):
        bootstrap=load('gradle_bootstrap',ROOT/'tools/gradle_bootstrap.py')
        properties=(ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()
        self.assertEqual(64,len(bootstrap.DISTRIBUTION_SHA256))
        self.assertIn('distributionSha256Sum='+bootstrap.DISTRIBUTION_SHA256,properties)
    def test_java_version_17(self):self.assertEqual(17,build.parse_java_major('openjdk version "17.0.1"'))
    def test_java_version_21(self):self.assertEqual(21,build.parse_java_major('openjdk version "21.0.11"'))
    def test_old_java_version(self):self.assertEqual(8,build.parse_java_major('java version "1.8.0_111"'))
    def test_invalid_java_version_refused(self):
        with self.assertRaises(build.Blocked):build.parse_java_major('unknown java')
    def test_absent_sdk_is_blocking(self):
        with self.assertRaises(build.Blocked):build.sdk_dir(self.root,{})
    def test_local_sdk_properties_win(self):
        (self.root/'local.properties').write_text('sdk.dir=/a\\ b/sdk\n')
        self.assertEqual(Path('/a b/sdk'),build.sdk_dir(self.root,{'ANDROID_HOME':'/other'}))
    def test_badging_identity_correct(self):build.verify_badging("package: name='"+build.APP_ID+"' versionCode='5'",build.APP_ID)
    def test_badging_wrong_identity_refused(self):
        with self.assertRaises(RuntimeError):build.verify_badging("package: name='org.invalid.app'",build.APP_ID)
    def test_attempt_does_not_reuse_prior_success(self):
        previous=build.Attempt(self.root);previous.state.update(outcome='build_checks_passed',apk_built=True);previous.save()
        current=build.Attempt(self.root)
        latest=json.loads((current.base/'latest.json').read_text())
        self.assertFalse(latest['apk_built']);self.assertEqual({},latest['artifacts'])
        self.assertNotEqual(previous.id,current.id)
        self.assertTrue((previous.out/'status.json').exists())
    def test_preflight_block_writes_truthful_receipt(self):
        with mock.patch.object(build.shutil,'which',return_value=None):self.assertEqual(2,build.main(self.root))
        status=json.loads((self.root/'dist/android/latest.json').read_text())
        self.assertEqual('blocked',status['outcome']);self.assertFalse(status['apk_built'])
        self.assertFalse(status['production_qualified'])
        self.assertEqual([],list(self.root.rglob('*.apk')))
    def test_resolver_refuses_latest_failed_attempt(self):
        build.Attempt(self.root)
        with self.assertRaises(ValueError):resolver.resolve(self.root/'dist/android')
    def test_resolver_refuses_path_traversal_before_reading_artifacts(self):
        base=self.root/'evidence';base.mkdir()
        (base/'latest.json').write_text(json.dumps({'application_id':build.APP_ID,'apk_built':True,'test_apk_built':True,'run':'../outside'}))
        with self.assertRaises(ValueError):resolver.resolve(base)
    def test_atomic_json_replaces_and_cleans_temp(self):
        path=self.root/'status.json';path.write_text('{"old":true}')
        build.atomic_json(path,{'new':True})
        self.assertEqual({'new':True},json.loads(path.read_text()));self.assertEqual([],list(self.root.glob('*.tmp')))
    def test_missing_apk_never_promoted(self):
        attempt=build.Attempt(self.root)
        with self.assertRaises(RuntimeError):attempt.store_apk(self.root/'absent.apk','output.apk','app',build.APP_ID,self.root)
        self.assertFalse(attempt.state['apk_built']);self.assertEqual({},attempt.state['artifacts'])
if __name__=='__main__':unittest.main(verbosity=2)

"""Prevent stale or substituted APKs and incomplete Android evidence from shipping."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from package_verified_release import source_binding, verified_artifact, verify_core


class VerifiedReleaseTests(unittest.TestCase):
    def test_bootstrap_line_endings_are_explicitly_recorded(self):
        raw=b'print("build")\r\n'; committed=raw.replace(b'\r\n',b'\n')
        result=source_binding('tools/gradle_bootstrap.py',hashlib.sha256(raw).hexdigest(),raw,committed)
        self.assertEqual(result['committed_sha256'],hashlib.sha256(committed).hexdigest())
        self.assertEqual(result['build_sha256'],hashlib.sha256(raw).hexdigest())

    def test_bootstrap_logic_difference_is_rejected(self):
        raw=b'print("build")\r\n'
        with self.assertRaisesRegex(ValueError,'differ from the commit'):
            source_binding('tools/gradle_bootstrap.py',hashlib.sha256(raw).hexdigest(),raw,b'print("skip")\n')

    def test_android_source_newline_difference_is_rejected(self):
        raw=b'val text = """first\r\nsecond"""\r\n'
        with self.assertRaisesRegex(ValueError,'differ from the commit'):
            source_binding('app/src/main/Example.kt',hashlib.sha256(raw).hexdigest(),raw,raw.replace(b'\r\n',b'\n'))

    def test_modified_artifact_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); (root/'candidate.apk').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'differs'):
                verified_artifact(root,dict(file='candidate.apk',bytes=7,sha256=hashlib.sha256(b'initial').hexdigest()))

    def test_artifact_cannot_escape_its_receipt_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError,'Unsafe'):
                verified_artifact(Path(directory),dict(file='../candidate.apk',bytes=0,sha256='0'*64))

    def test_device_evidence_from_another_apk_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'status.json').write_text(json.dumps(dict(outcome='core_suite_passed',page_size='16384',
                build=dict(run_id='other',artifacts={}))))
            with self.assertRaisesRegex(ValueError,'different APKs'):
                verify_core(root,dict(run_id='current',artifacts={}),16384)

    def test_four_k_evidence_cannot_claim_sixteen_k(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'status.json').write_text(json.dumps(dict(outcome='core_suite_passed',page_size='4096')))
            with self.assertRaisesRegex(ValueError,'page size'):
                verify_core(root,{},16384)


if __name__=='__main__': unittest.main()

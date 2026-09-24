"""Publication gates reject payload changes and misleading success receipts."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_native_release import apk_payload, verify_suite
from run_device_qualification import parse_instrumentation


class NativeReleaseTests(unittest.TestCase):
    def test_signed_payload_comparison_detects_code_tampering(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = [Path(tmp) / (n + '.zip') for n in ['original', 'signed', 'changed']]
            for i, path in enumerate(paths):
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr('classes.dex', b'changed' if i == 2 else b'test payload')
                    if i:
                        archive.writestr('META-INF/CERT.SF', b'test signature metadata')
            self.assertEqual(apk_payload(paths[0]), apk_payload(paths[1]))
            self.assertNotEqual(apk_payload(paths[0]), apk_payload(paths[2]))

    def prepare(self, folder):
        text = ''.join(f'INSTRUMENTATION_STATUS: class=TestFlow\nINSTRUMENTATION_STATUS: test=case{i}\n'
                       'INSTRUMENTATION_STATUS: numtests=4\nINSTRUMENTATION_STATUS_CODE: 0\n'
                       for i in range(4)) + 'OK (4 tests)\n'
        state = dict(outcome='release_ui_suite_passed', main_sha256='a' * 64,
                     qa_sha256='b' * 64, tests=parse_instrumentation(text),
                     art_crashes_before=[], art_crashes_after=[], page_size=16384, abi='x86_64')
        (folder / 'instrumentation.txt').write_text(text)
        return state

    def save(self, folder, state):
        (folder / 'status.json').write_text(json.dumps(state))

    def test_a_different_apk_cannot_reuse_successful_results(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp); self.save(p, self.prepare(p))
            with self.assertRaisesRegex(ValueError, 'selected APK'):
                verify_suite(p, 'c' * 64, 4, qa_sha='b' * 64)

    def test_truncated_output_cannot_reuse_a_successful_receipt(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp); self.save(p, self.prepare(p))
            (p / 'instrumentation.txt').write_text('INSTRUMENTATION_FAILED\n')
            with self.assertRaisesRegex(ValueError, 'instrumentation'):
                verify_suite(p, 'a' * 64, 4, qa_sha='b' * 64)

    def test_system_art_crash_blocks_publication_despite_passing_assertions(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp); state = self.prepare(p)
            state['art_crashes_after'] = [dict(process='system_server')]; self.save(p, state)
            with self.assertRaisesRegex(ValueError, 'ART'):
                verify_suite(p, 'a' * 64, 4, qa_sha='b' * 64)

    def test_complete_matching_evidence_is_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp); self.save(p, self.prepare(p))
            result = verify_suite(p, 'a' * 64, 4, qa_sha='b' * 64)
            self.assertEqual(result['passed'], 4)
            self.assertFalse(result['physical_device'])


if __name__ == '__main__':
    unittest.main()

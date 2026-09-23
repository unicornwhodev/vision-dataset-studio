"""A zero adb exit code or JUnit OK with skipped tests is not qualification."""
import unittest
from run_device_qualification import parse_instrumentation


def result(codes, summary='OK (2 tests)'):
    return '\n'.join(f'INSTRUMENTATION_STATUS: class=Example\nINSTRUMENTATION_STATUS: test=test{i}\n'
                     f'INSTRUMENTATION_STATUS: numtests=2\nINSTRUMENTATION_STATUS_CODE: {code}'
                     for i, code in enumerate(codes)) + '\n' + summary


class DeviceReceiptTests(unittest.TestCase):
    def test_full_success(self):
        self.assertTrue(parse_instrumentation(result([0, 0]))['complete'])

    def test_assumption_is_not_a_pass(self):
        parsed = parse_instrumentation(result([0, -4]))
        self.assertEqual(1, parsed['skipped'])
        self.assertFalse(parsed['complete'])

    def test_failure_or_truncated_execution_cannot_qualify(self):
        for output in [result([0, -2]), result([0]), result([], ''), result([0, 0], 'INSTRUMENTATION_FAILED')]:
            self.assertFalse(parse_instrumentation(output)['complete'])


if __name__ == '__main__':
    unittest.main()

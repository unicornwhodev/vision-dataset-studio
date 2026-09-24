import unittest
from art_environment import art_crashes


class ArtEnvironmentTest(unittest.TestCase):
    def test_system_art_failure_is_not_hidden_by_successful_app_tests(self):
        text = '''09-23 18:50:27 F DEBUG : *** *** *** *** *** *** *** *** *** ***
09-23 18:50:27 F DEBUG : Cmdline: system_server
09-23 18:50:27 F DEBUG : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x40
09-23 18:50:27 F DEBUG : #00 pc 0000000000a87312 /apex/com.android.art/lib64/libart.so (NterpGetShorty+2)
09-23 18:50:27 F DEBUG : #01 pc 0000000000209908 /apex/com.android.art/lib64/libart.so (nterp_helper+3992)
'''
        incidents = art_crashes(text)
        self.assertEqual(len(incidents), 1)
        self.assertEqual(incidents[0]['process'], 'system_server')

    def test_art_in_an_unrelated_crash_stack_is_not_an_art_fault(self):
        self.assertEqual(art_crashes('''*** *** ***
Cmdline: example
#00 pc 0001 /apex/com.android.runtime/lib64/bionic/libc.so (abort+4)
#01 pc 0002 /apex/com.android.art/lib64/libart.so (Invoke+4)
'''), [])

    def test_empty_crash_buffer_is_clean(self):
        self.assertEqual(art_crashes('--------- beginning of crash\n'), [])


if __name__ == '__main__':
    unittest.main()

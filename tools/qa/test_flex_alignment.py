"""Parser regression cases, not native runtime qualification."""
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from check_flex_runtime import LIBRARY, verify_flex


def elf(*, alignment=16384, relro_end=32768, machine=62):
    data = bytearray(512)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<H', data, 18, machine)
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 2)
    struct.pack_into('<IIQQQQQQ', data, 64, 1, 5, 0, 0, 0, 512, 512, alignment)
    struct.pack_into('<IIQQQQQQ', data, 120, 0x6474e552, 4, 0, 16384, 0, 128, relro_end-16384, 1)
    return data


class FlexAlignmentTest(unittest.TestCase):
    def check(self, data, *, name=LIBRARY, required=None):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'parser-case.aar'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('jni/x86_64/' + name, data)
            return verify_flex(path, required)

    def test_accepts_aligned_segments(self):
        self.assertIn('x86_64', self.check(elf())['libraries'])

    def test_rejects_old_4k_load_segments(self):
        with self.assertRaisesRegex(ValueError, 'not 16 KB'):
            self.check(elf(alignment=4096))

    def test_rejects_4k_relro_even_with_16k_loads(self):
        with self.assertRaisesRegex(ValueError, 'not 16 KB'):
            self.check(elf(relro_end=20480))

    def test_rejects_missing_flex(self):
        with self.assertRaisesRegex(ValueError, 'Missing Flex'):
            self.check(elf(), name='different.so')

    def test_rejects_missing_arm64_in_distributable_aar(self):
        with self.assertRaisesRegex(ValueError, 'Missing Flex'):
            self.check(elf(), required=['arm64-v8a', 'x86_64'])

    def test_rejects_wrong_architecture(self):
        with self.assertRaisesRegex(ValueError, 'architecture'):
            self.check(elf(machine=183))


if __name__ == '__main__':
    unittest.main()

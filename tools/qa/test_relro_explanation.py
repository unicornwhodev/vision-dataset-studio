import struct
import unittest
from explain_relro_layout import explain


def elf(headers):
    data=bytearray(64+len(headers)*56)
    data[:6]=b'\x7fELF\x02\x01'
    struct.pack_into('<Q',data,32,64)
    struct.pack_into('<HH',data,54,56,len(headers))
    for index,header in enumerate(headers):
        struct.pack_into('<IIQQQQQQ',data,64+index*56,*header)
    return bytes(data)


class RelroExplanationTests(unittest.TestCase):
    def test_shared_writable_page_is_not_explained_away(self):
        value=explain(elf([(1,6,0,0,0,20000,20000,16384),(0x6474e552,4,0,0,0,4096,4096,1)]))[0]
        self.assertEqual([dict(start=4096,end=16384)],value['writable_bytes_outside_relro_affected_by_rounding'])

    def test_whole_relro_load_with_gap_keeps_strict_warning(self):
        value=explain(elf([(1,6,0,16384,0,8000,8000,16384),(1,6,0,32768,0,8,8,16384),
                           (0x6474e552,4,0,16384,0,8192,8192,1)]))[0]
        self.assertEqual([],value['writable_bytes_outside_relro_affected_by_rounding'])
        self.assertFalse(value['strict_relro_end_alignment'])


if __name__=='__main__':unittest.main()

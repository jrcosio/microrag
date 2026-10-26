"""PDF fixtures of the app's instrumented tests (F06 T003, research R12, rule X3)."""

import io
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gen_pdf_fixtures  # noqa: E402
from pypdf import PdfReader  # noqa: E402


class PdfFixturesTest(unittest.TestCase):
    def setUp(self):
        self.files = gen_pdf_fixtures.fixtures()

    def test_deterministic_and_committed(self):
        self.assertEqual(self.files, gen_pdf_fixtures.fixtures())
        for name, data in self.files.items():
            self.assertEqual((gen_pdf_fixtures.TARGET / name).read_bytes(), data, name)

    def test_small(self):
        self.assertLess(sum(map(len, self.files.values())), 1_000_000)

    def test_contents(self):
        read = lambda name: PdfReader(io.BytesIO(self.files[name]))  # noqa: E731
        self.assertIn("configura-", read("text_3_pages.pdf").pages[0].extract_text())
        self.assertEqual([p.extract_text() for p in read("scanned.pdf").pages], ["", ""])
        self.assertEqual(len(read("text_200_pages.pdf").pages), 200)
        encrypted = read("encrypted.pdf")
        self.assertTrue(encrypted.is_encrypted)
        self.assertFalse(encrypted.decrypt(""))
        restricted = read("restricted.pdf")
        self.assertTrue(restricted.is_encrypted)
        self.assertTrue(restricted.decrypt(""))
        self.assertFalse(self.files["fake.pdf"].startswith(b"%PDF-"))


if __name__ == "__main__":
    unittest.main()

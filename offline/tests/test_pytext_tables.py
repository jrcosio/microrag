"""PyTables.kt is up to date with this Python (F06 T002, research R8)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gen_pytext_tables  # noqa: E402


class PyTablesTest(unittest.TestCase):
    def test_generated_kotlin_is_up_to_date(self):
        self.assertEqual(gen_pytext_tables.TARGET.read_text(encoding="utf-8"), gen_pytext_tables.render())

    def test_tables_hold_the_known_cases(self):
        spaces = gen_pytext_tables.ranges(str.isspace)
        self.assertIn((0x1C, 0x20), spaces)  # \x1c-\x1f are spaces in Python, unlike Java's isSpaceChar
        self.assertIn(0x85, gen_pytext_tables.line_breaks())
        digits = gen_pytext_tables.ranges(str.isdigit)
        self.assertTrue(any(a <= ord("²") <= b for a, b in digits))  # superscripts are digits, not decimals
        self.assertFalse(any(a <= ord("²") <= b for a, b in gen_pytext_tables.ranges(str.isdecimal)))


if __name__ == "__main__":
    unittest.main()

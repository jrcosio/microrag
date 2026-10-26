"""The D-036 overflow golden is up to date with embedding_text.overflow_cut (F07 T001, research R6)."""

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gen_overflow_cases  # noqa: E402
from embedding_text import overflow_cut  # noqa: E402


class OverflowCasesTest(unittest.TestCase):
    def test_committed_golden_matches_the_rule(self):
        self.assertEqual(gen_overflow_cases.TARGET.read_text(encoding="utf-8"), gen_overflow_cases.render())

    def test_cuts_count_code_points_and_stop_at_a_space(self):
        self.assertEqual(overflow_cut("ab cd ef gh ij"), "ab cd ef gh")
        # Ten code points, of which two are outside the BMP: 90 % keeps nine, then back to the space.
        self.assertEqual(overflow_cut("😀😀 abcdef"), "😀😀")
        self.assertEqual(overflow_cut("sinespacios"), "sinespaci")
        self.assertEqual(overflow_cut(""), "")

    def test_every_case_has_eleven_cuts(self):
        cases = [json.loads(line) for line in gen_overflow_cases.TARGET.read_text(encoding="utf-8").split("\n") if line]
        self.assertEqual(len(cases), len(gen_overflow_cases.CASES))
        for case in cases:
            self.assertEqual(len(case["steps"]), 11, case["case"])


if __name__ == "__main__":
    unittest.main()

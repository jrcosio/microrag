"""F14 reviews against v4 (D-088, D-090): the quality rule of the levers and the judged rows of each variant."""

import csv
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import f14_review  # noqa: E402

RULE = {"max_correct_drop_points": 1.0, "min_refusal": 0.85, "min_citations": 0.90}
BASE = {"answer_correct": 0.811, "refusal_correct": 0.96, "citations_correct": 0.94, "undue_refusals": 10}


class PassesTest(unittest.TestCase):

    def test_one_point_below_the_base_still_passes(self):
        self.assertTrue(f14_review.passes(BASE, {**BASE, "answer_correct": 0.801}, RULE))

    def test_more_than_one_point_below_fails(self):
        self.assertFalse(f14_review.passes(BASE, {**BASE, "answer_correct": 0.800}, RULE))

    def test_refusal_and_citations_have_their_minimums(self):
        self.assertFalse(f14_review.passes(BASE, {**BASE, "refusal_correct": 0.849}, RULE))
        self.assertFalse(f14_review.passes(BASE, {**BASE, "citations_correct": 0.899}, RULE))
        self.assertTrue(f14_review.passes(BASE, {**BASE, "refusal_correct": 0.85, "citations_correct": 0.90}, RULE))


class ReviewedTest(unittest.TestCase):

    def test_only_judged_rows_of_the_variant_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "f14-review.csv"
            with path.open("w", encoding="utf-8-sig", newline="") as out:
                writer = csv.DictWriter(out, fieldnames=f14_review.FIELDS)
                writer.writeheader()
                writer.writerow({"id": "q1", "variante": "runtime-0.19.1", "manual": "correcta", "manual_citas": "1/1"})
                writer.writerow({"id": "q2", "variante": "runtime-0.19.1", "manual": "", "manual_citas": ""})
                writer.writerow({"id": "q1", "variante": "v4-cifras", "manual": "incorrecta", "manual_citas": "0/1"})
            rows = f14_review.existing_rows(path)

        reviewed = f14_review.reviewed_of(rows, "runtime-0.19.1")

        self.assertEqual(reviewed, {"q1": {"manual": "correcta", "manual_citas": "1/1"}})

    def test_runtime_names_carry_the_version(self):
        self.assertEqual(f14_review.runtime_name("0.19.1"), "runtime-0.19.1")


if __name__ == "__main__":
    unittest.main()

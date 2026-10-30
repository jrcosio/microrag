"""Shape of the answer golden (F11 T007, contracts/eval-answer.md rule G5).

Regenerating it needs the reference index and the generation runs (offline/build); `gen_answer_golden.py
--check` does it.
"""

import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gen_answer_golden  # noqa: E402
from eval.pipeline import live_questions  # noqa: E402
from eval.retrieval import PARAMETERS  # noqa: E402

FIELDS = {"id", "template", "question", "previous", "blocks", "prompt_chars", "prompt_sha256", "prompt", "answer",
          "citations", "cited_order", "refusal", "norm"}


class AnswerGoldenShapeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = [json.loads(line) for line in gen_answer_golden.GOLDEN.read_text(encoding="utf-8").splitlines() if line]

    def test_one_row_per_live_question_in_id_order_with_the_active_template(self):
        self.assertEqual([r["id"] for r in self.rows], sorted(q.id for q in live_questions()))
        self.assertEqual(len(self.rows), 426)
        template = json.loads(PARAMETERS.read_text(encoding="utf-8"))["prompt_template"]
        self.assertEqual({r["template"] for r in self.rows}, {template})

    def test_fields_and_the_previous_turn(self):
        for row in self.rows:
            self.assertEqual(set(row), FIELDS, row["id"])
            self.assertTrue(row["previous"] is None or len(row["previous"]) == 2, row["id"])
            for block in row["blocks"]:
                self.assertEqual(set(block), {"document_name", "heading_path", "page", "text"}, row["id"])

    def test_ten_rows_keep_the_whole_prompt_and_it_matches_its_digest(self):
        full = [r for r in self.rows if r["prompt"] is not None]
        self.assertEqual(len(full), 10)
        self.assertGreaterEqual(sum(r["previous"] is not None for r in full), 3)
        for row in full:
            self.assertEqual(len(row["prompt"]), row["prompt_chars"], row["id"])
            self.assertEqual(hashlib.sha256(row["prompt"].encode("utf-8")).hexdigest(), row["prompt_sha256"], row["id"])

    def test_cited_order_is_a_valid_subset_of_the_citations(self):
        for row in self.rows:
            self.assertTrue(set(row["cited_order"]) <= set(row["citations"]), row["id"])
            self.assertEqual(len(row["cited_order"]), len(set(row["cited_order"])), row["id"])
            self.assertTrue(all(1 <= n <= len(row["blocks"]) for n in row["cited_order"]), row["id"])

    def test_cited_order_rule(self):
        self.assertEqual(gen_answer_golden.cited_order([3, 1, 3, 7, 0, 1], 4), [3, 1])


if __name__ == "__main__":
    unittest.main()

"""Cross-language measure of F12 (D-058, D-060, contracts/eval-language.md L7, L11–L14)."""

import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import crosslang  # noqa: E402


def q(qid, qtype, previous=None):
    return SimpleNamespace(id=qid, type=qtype, previous=previous, review="ok")


class SelectionTest(unittest.TestCase):
    QUESTIONS = [q("q1", "answerable"), q("q2", "followup", "q9"), q("q3", "followup", "q1"), q("q9", "unanswerable"),
                 q("q4", "answerable"), *[q(f"u{i}", "unanswerable") for i in range(10)]]

    def test_sample_then_missing_previous_then_unanswerable(self):
        chosen = crosslang.es_docs_sources(self.QUESTIONS, ["q1", "q2", "q3"], unanswerable=4)
        self.assertEqual(chosen[:4], ["q1", "q2", "q3", "q9"])
        self.assertEqual(sum(c.startswith("u") for c in chosen), 3)  # q9 already counts as one
        self.assertEqual(len(chosen), len(set(chosen)))

    def test_the_draw_is_deterministic(self):
        first = crosslang.es_docs_sources(self.QUESTIONS, ["q1"], unanswerable=5)
        self.assertEqual(first, crosslang.es_docs_sources(self.QUESTIONS, ["q1"], unanswerable=5))
        self.assertNotEqual(first, crosslang.es_docs_sources(self.QUESTIONS, ["q1"], unanswerable=5, seed=1))


class SetFileTest(unittest.TestCase):
    def test_load_set_keeps_the_source_id_apart(self):
        row = {"id": "e1", "type": "answerable", "question": "Deadline?", "previous": None, "doc": "boe-lau",
               "content_type": "dato", "expected_answer": "30 días.", "key_facts": ["30 días"], "evidence": "x",
               "relevant_ref": [], "review": "ok", "review_note": None, "source_id": "q0001"}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "set.jsonl"
            path.write_text(json.dumps(row) + "\n", encoding="utf-8")
            questions, sources = crosslang.load_set(path)
        self.assertEqual((questions[0].id, questions[0].question), ("e1", "Deadline?"))
        self.assertEqual(sources, {"e1": "q0001"})


class CombinationTest(unittest.TestCase):
    def test_each_combination_has_its_index_set_and_answer_language(self):
        by_key = {c.key: c for c in crosslang.COMBINATIONS}
        self.assertEqual((by_key["ES·EN"].build, by_key["ES·EN"].set_name), ("ref", "es-docs.en"))
        self.assertEqual((by_key["EN·EN"].build, by_key["EN·ES"].build), ("en", "en"))
        self.assertEqual(crosslang.answer_language(by_key["ES·EN"], "auto"), "auto")
        self.assertEqual(crosslang.answer_language(by_key["EN·EN"], "es"), "es")
        self.assertEqual(crosslang.answer_language(by_key["EN·ES"], "auto"), "es")


class FiguresTest(unittest.TestCase):
    OK = {"answer_correct": 0.75, "refusal_correct": 13 / 15, "citations_correct": 0.91}

    def test_reference_is_the_share_of_correct_source_answers(self):
        rows = {"q1": {"tipo": "answerable", "manual": "correcta"}, "q2": {"tipo": "followup", "manual": "parcial"},
                "q3": {"tipo": "unanswerable", "manual": "correcta"}}
        self.assertEqual(crosslang.reference_correct(rows, ["q1", "q2", "q3"]), 0.5)

    def test_ten_points_below_the_reference_is_the_limit(self):
        self.assertTrue(crosslang.combination_ok(self.OK, 0.85))
        self.assertFalse(crosslang.combination_ok({**self.OK, "answer_correct": 0.749}, 0.85))
        self.assertTrue(crosslang.combination_ok({**self.OK, "answer_correct": 0.2}, None))  # EN·EN: floors only
        self.assertFalse(crosslang.combination_ok({**self.OK, "refusal_correct": 12 / 15}, None))

    def test_card_text_needs_every_combination(self):
        self.assertTrue(crosslang.keeps_card_text({"ES·EN": (self.OK, 0.8), "EN·EN": (self.OK, None)}))
        self.assertFalse(crosslang.keeps_card_text({"ES·EN": (self.OK, 0.9), "EN·EN": (self.OK, None)}))

    def test_a_verdict_only_carries_over_for_the_same_answer(self):
        old = {"respuesta": "A [1].", "manual": "correcta", "manual_citas": "1/1", "comentario": ""}
        self.assertEqual(crosslang.kept_verdict(old, "A [1].")["manual"], "correcta")
        self.assertEqual(crosslang.kept_verdict(old, "B [1].")["manual"], "")


if __name__ == "__main__":
    unittest.main()

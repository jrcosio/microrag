"""«Automático» review of F12 (D-058, D-060, contracts/eval-language.md L5–L6)."""

import sys
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import language_review as lr  # noqa: E402
from eval import template_review as tr  # noqa: E402

# v4 with the merged manual verdicts (eval-report.md, D-056).
V4 = {"answer_correct": 300 / 370, "refusal_correct": 51 / 56, "citations_correct": 559 / 593}


class RuleTest(unittest.TestCase):
    def test_holds_at_the_v4_figures(self):
        self.assertTrue(lr.meets_rule(V4, dict(V4)))
        self.assertTrue(lr.meets_rule(V4, {**V4, "answer_correct": 301 / 370}))

    def test_one_figure_below_v4_fails(self):
        for key, value in (("answer_correct", 299 / 370), ("refusal_correct", 50 / 56), ("citations_correct", 558 / 593)):
            self.assertFalse(lr.meets_rule(V4, {**V4, key: value}), key)

    def test_the_report_floors_apply_even_below_a_low_reference(self):
        low = {"answer_correct": 0.5, "refusal_correct": 0.80, "citations_correct": 0.85}
        self.assertFalse(lr.meets_rule(low, {**low, "citations_correct": 0.95}))
        self.assertFalse(lr.meets_rule(low, {**low, "refusal_correct": 0.95}))


class AdoptionTest(unittest.TestCase):
    ES_EN = {"answer_correct": 0.72, "refusal_correct": 13 / 15, "citations_correct": 0.91}

    def test_needs_the_rule_the_floors_and_ten_points(self):
        self.assertTrue(lr.adopts_automatic(True, self.ES_EN, 0.82))
        self.assertFalse(lr.adopts_automatic(False, self.ES_EN, 0.82))
        self.assertFalse(lr.adopts_automatic(True, {**self.ES_EN, "answer_correct": 0.719}, 0.82))
        self.assertFalse(lr.adopts_automatic(True, {**self.ES_EN, "refusal_correct": 12 / 15}, 0.82))
        self.assertFalse(lr.adopts_automatic(True, {**self.ES_EN, "citations_correct": 0.899}, 0.82))

    def test_ten_points_is_inclusive(self):
        self.assertTrue(lr.within_points(0.70, 0.80))
        self.assertFalse(lr.within_points(0.6999, 0.80))


class ReviewRowsTest(unittest.TestCase):
    QUESTION = SimpleNamespace(id="q1", type="answerable", question="¿Plazo?", expected_answer="30 días.",
                               key_facts=["30 días"])

    def test_row_has_the_review_fields_and_keeps_the_old_verdict(self):
        old = {"manual": "parcial", "manual_citas": "1/2", "comentario": "falta el plazo"}
        row = lr.review_row(self.QUESTION, "[1] Doc", "30 días [1].", "correcta", "Treinta días [1].", "correcta",
                            {"citations_ok": 1, "citations_total": 1}, old)
        self.assertEqual(list(row), lr.REVIEW_FIELDS)
        self.assertEqual((row["manual"], row["manual_citas"], row["comentario"], row["regla_citas"]),
                         ("parcial", "1/2", "falta el plazo", "1/1"))

    def test_unchanged_answers_inherit_and_an_unjudged_change_is_pending(self):
        base = [{"id": "q1", "tipo": "answerable", "manual": "correcta", "manual_citas": "1/1",
                 "respuesta_modelo": "30 días [1].", "regla": "correcta"},
                {"id": "q2", "tipo": "unanswerable", "manual": "correcta", "manual_citas": "",
                 "respuesta_modelo": "No encuentro esa información en los documentos disponibles.", "regla": "correcta"}]
        same = {"q1": "30 días [1].", "q2": base[1]["respuesta_modelo"]}
        self.assertEqual(tr.merged_rows(base, same, {"q1": "correcta", "q2": "correcta"}, {}), base)
        self.assertIsNone(tr.merged_rows(base, {**same, "q1": "Treinta [1]."}, {"q1": "correcta", "q2": "correcta"}, {}))


class BlocksTest(unittest.TestCase):
    def test_different_blocks_stop_the_review(self):
        lr.check_blocks({"q1": [1], "q2": [2]}, {"q1": [1], "q2": [2]})
        with self.assertRaises(ValueError):
            lr.check_blocks({"q1": [1], "q2": [2]}, {"q1": [1], "q2": [3]})


if __name__ == "__main__":
    unittest.main()

"""Automatic judgement of answers, refusals and citations (F05 T035, research R9)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import judge  # noqa: E402
from eval.selector import ContextBlock  # noqa: E402

BLOCKS = [ContextBlock("Doc", None, None, 1, 0, 1, "texto", 10), ContextBlock("Otro", None, None, 2, 4, 4, "texto", 10)]
RELEVANT = {(1, 1)}


class NormalizationTest(unittest.TestCase):
    def test_key_facts_ignore_case_accents_punctuation_and_thousands(self):
        self.assertTrue(judge.has_key_facts("El plazo es de TREINTA días (art. 5).", ["treinta dias"]))
        self.assertTrue(judge.has_key_facts("Hasta 1.000 euros.", ["1000 euros"]))
        self.assertTrue(judge.has_key_facts("Gasta 1,632 calorías.", ["1.632 calorías"]))
        self.assertFalse(judge.has_key_facts("Hasta 100 euros.", ["1000 euros"]))
        self.assertFalse(judge.has_key_facts("Hasta 10 euros.", ["1 euros"]))

    def test_key_facts_allow_word_forms_and_order(self):
        # Cases from the manual check of the rule (D-032 calibration).
        self.assertTrue(judge.has_key_facts("puede ser renovado otro periodo", ["renovarse"]))
        self.assertTrue(judge.has_key_facts("deben ser destruidos o devueltos al responsable", ["destruyen", "devuelven al responsable"]))
        self.assertTrue(judge.has_key_facts("deberán previamente consultar los sistemas", ["consultar previamente"]))
        self.assertFalse(judge.has_key_facts("la versión 10.7 o superior", ["10.4 o superior"]))
        self.assertFalse(judge.has_key_facts("en cinco meses", ["sesenta días"]))


class RefusalTest(unittest.TestCase):
    def test_exact_refusal_after_normalization(self):
        self.assertTrue(judge.is_refusal("No encuentro esa información en los documentos disponibles."))
        self.assertTrue(judge.is_refusal("  no encuentro esa informacion en los documentos disponibles  "))
        self.assertFalse(judge.is_refusal("No encuentro esa información en los documentos disponibles. Sin embargo, el plazo es 30 días."))


class CitationsTest(unittest.TestCase):
    def test_citation_forms(self):
        self.assertEqual(judge.citations("Uno [1]. Dos [1, 2]. Tres [2][3]."), [1, 1, 2, 2, 3])

    def test_judge_answerable(self):
        ok = judge.judge_answer("answerable", "Cada 500 horas [1].", ["500 horas"], BLOCKS, RELEVANT)
        self.assertEqual((ok.correct, ok.refusal, ok.citations_ok, ok.citations_total), (True, False, 1, 1))
        wrong_source = judge.judge_answer("answerable", "Cada 500 horas [2] y [7].", ["500 horas"], BLOCKS, RELEVANT)
        self.assertEqual((wrong_source.citations_ok, wrong_source.citations_total), (0, 2))
        uncited = judge.judge_answer("answerable", "Cada 500 horas.", ["500 horas"], BLOCKS, RELEVANT)
        self.assertEqual((uncited.correct, uncited.citations_ok, uncited.citations_total), (True, 0, 1))
        refused = judge.judge_answer("followup", judge.REFUSAL, ["500 horas"], BLOCKS, RELEVANT)
        self.assertEqual((refused.correct, refused.refusal, refused.citations_total), (False, True, 0))

    def test_judge_unanswerable(self):
        self.assertTrue(judge.judge_answer("unanswerable", judge.REFUSAL, [], BLOCKS, set()).correct)
        self.assertFalse(judge.judge_answer("unanswerable", "El precio es 10 euros [1].", [], BLOCKS, set()).correct)

    def test_summary(self):
        results = [
            judge.judge_answer("answerable", "Cada 500 horas [1].", ["500 horas"], BLOCKS, RELEVANT),
            judge.judge_answer("answerable", "Cada 600 horas [2].", ["500 horas"], BLOCKS, RELEVANT),
            judge.judge_answer("unanswerable", judge.REFUSAL, [], BLOCKS, set()),
        ]
        summary = judge.summarize(["answerable", "answerable", "unanswerable"], results)
        self.assertEqual(summary, {"answer_correct": 0.5, "refusal_correct": 1.0, "citations_correct": 0.5,
                                   "answered": 2, "unanswerable": 1, "citations": 2})


if __name__ == "__main__":
    unittest.main()

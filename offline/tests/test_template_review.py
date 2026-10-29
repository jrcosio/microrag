"""Template review of F11 (D-035, D-053, contracts/eval-answer.md §2): screening, inherited verdicts and adoption."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import template_review as tr  # noqa: E402

# The report template with the automatic rule (eval-report.md): the baseline of the screening.
INFORME = {"answer_correct": 0.689, "refusal_correct": 0.964, "citations_correct": 0.764}


def manual_row(qid, tipo, manual, citas, answer="Respuesta [1].", regla="correcta"):
    return {"id": qid, "tipo": tipo, "manual": manual, "manual_citas": citas, "respuesta_modelo": answer, "regla": regla}


class ScreeningTest(unittest.TestCase):
    def test_passes_with_one_point_more_and_the_floors(self):
        self.assertTrue(tr.passes_screen(INFORME, {"answer_correct": 0.699, "refusal_correct": 0.85, "citations_correct": 0.744}))

    def test_each_figure_below_its_floor_fails(self):
        ok = {"answer_correct": 0.70, "refusal_correct": 0.90, "citations_correct": 0.76}
        for key, value in (("answer_correct", 0.698), ("refusal_correct", 0.849), ("citations_correct", 0.743)):
            self.assertFalse(tr.passes_screen(INFORME, {**ok, key: value}), key)


class InheritedVerdictsTest(unittest.TestCase):
    def test_unchanged_answers_keep_the_reference_verdict_and_changed_ones_use_the_review(self):
        reference = [manual_row("q1", "answerable", "correcta", "1/1", "Uno [1]."),
                     manual_row("q2", "answerable", "parcial", "1/2", "Dos [1][2].")]
        answers = {"q1": "Uno [1].", "q2": "Dos completa [1][2]."}
        rules = {"q1": "correcta", "q2": "correcta"}
        reviewed = {"q2": {"manual": "correcta", "manual_citas": "2/2"}}
        rows = tr.merged_rows(reference, answers, rules, reviewed)
        self.assertEqual([(r["id"], r["manual"], r["manual_citas"]) for r in rows],
                         [("q1", "correcta", "1/1"), ("q2", "correcta", "2/2")])
        self.assertEqual(rows[1]["respuesta_modelo"], "Dos completa [1][2].")

    def test_a_changed_answer_without_review_is_pending(self):
        reference = [manual_row("q1", "answerable", "correcta", "1/1", "Uno [1].")]
        self.assertIsNone(tr.merged_rows(reference, {"q1": "Otra [1]."}, {"q1": "correcta"}, {}))
        self.assertEqual(tr.changed_ids(reference, {"q1": "Otra [1]."}), ["q1"])


class AdoptionTest(unittest.TestCase):
    def test_needs_two_points_and_both_floors(self):
        base = {"answer_correct": 0.784}
        self.assertTrue(tr.adopts(base, {"answer_correct": 0.804, "refusal_correct": 0.85, "citations_correct": 0.90}))
        self.assertFalse(tr.adopts(base, {"answer_correct": 0.80, "refusal_correct": 0.95, "citations_correct": 0.95}))
        self.assertFalse(tr.adopts(base, {"answer_correct": 0.85, "refusal_correct": 0.84, "citations_correct": 0.95}))
        self.assertFalse(tr.adopts(base, {"answer_correct": 0.85, "refusal_correct": 0.95, "citations_correct": 0.89}))

    def test_the_best_adopted_variant_wins_and_ties_go_to_the_shorter_template(self):
        results = [{"template": "v4", "adopted": True, "manual": {"answer_correct": 0.82}, "rules_chars": 300},
                   {"template": "v3", "adopted": True, "manual": {"answer_correct": 0.82}, "rules_chars": 200},
                   {"template": "x", "adopted": False, "manual": {"answer_correct": 0.90}, "rules_chars": 10}]
        self.assertEqual(tr.winner(results)["template"], "v3")
        self.assertIsNone(tr.winner([results[2]]))


if __name__ == "__main__":
    unittest.main()

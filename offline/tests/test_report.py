"""Manual judgement metrics and the chosen configuration of the report (F05 T043)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import report  # noqa: E402
from eval.judge import REFUSAL  # noqa: E402


def row(tipo, manual, regla, citas, answer="Respuesta [1]."):
    return {"tipo": tipo, "manual": manual, "regla": regla, "manual_citas": citas, "respuesta_modelo": answer}


class ManualMetricsTest(unittest.TestCase):
    def test_partial_is_not_correct_and_counts_agreement(self):
        rows = [
            row("answerable", "correcta", "correcta", "2/2"),
            row("answerable", "parcial", "incorrecta", "1/2"),
            row("followup", "incorrecta", "incorrecta", "0/0", REFUSAL),
            row("unanswerable", "correcta", "correcta", "0/0", REFUSAL),
        ]
        m = report.manual_metrics(rows)
        self.assertAlmostEqual(m["answer_correct"], 1 / 3)
        self.assertEqual((m["refusal_correct"], m["citations_correct"], m["citations"]), (1.0, 0.75, 4))
        self.assertEqual((m["undue_refusals"], m["agreement"]), (1, 1.0))
        self.assertEqual(m["outcomes"][("answerable", "parcial")], 1)


class ChosenTest(unittest.TestCase):
    def test_picks_the_entry_run_with_the_decided_context(self):
        base = {"build": "ref", "mode": "hybrid", "follow_up_rule": True, "max_blocks": 4}
        entries = [{"label": "con vecinos", "config": {**base, "neighbors": True}},
                   {"label": "sin vecinos", "config": {**base, "neighbors": False}}]
        params = {"follow_up_rule": True, "max_blocks": 4, "expand_top": 0}
        self.assertEqual(report.chosen(entries, params)["label"], "sin vecinos")
        self.assertIsNone(report.chosen(entries, {**params, "max_blocks": 6}))


class TemplateReviewSectionTest(unittest.TestCase):
    def test_renders_the_table_and_the_decision(self):
        import json
        import tempfile

        results = [
            {"template": "informe", "label": "informe", "changed": 0, "auto": {"answer_correct": 0.689, "refusal_correct": 0.964,
             "citations_correct": 0.764}, "screen": None, "manual": {"answer_correct": 0.784, "refusal_correct": 0.964,
             "citations_correct": 0.942}, "pending": 0, "adopted": None},
            {"template": "v3", "label": "v3", "changed": 120, "auto": {"answer_correct": 0.70, "refusal_correct": 0.95,
             "citations_correct": 0.75}, "screen": True, "manual": {"answer_correct": 0.79, "refusal_correct": 0.95,
             "citations_correct": 0.93}, "pending": 0, "adopted": False},
            {"template": "v4", "label": "v4", "changed": 150, "auto": {"answer_correct": 0.68, "refusal_correct": 0.90,
             "citations_correct": 0.74}, "screen": False, "manual": None, "pending": 0, "adopted": False},
        ]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "template_review.json"
            path.write_text(json.dumps(results))
            original, report.TEMPLATE_REVIEW = report.TEMPLATE_REVIEW, path
            try:
                lines = "\n".join(report.template_review_section())
            finally:
                report.TEMPLATE_REVIEW = original
        self.assertIn("## Revisión de la plantilla (D-035, F11)", lines)
        self.assertIn("| v3 | 120 | 70.0 % | 95.0 % | 75.0 % | pasa | 79.0 % | 95.0 % | 93.0 % | no |", lines)
        self.assertIn("| v4 | 150 | 68.0 % | 90.0 % | 74.0 % | no pasa | — | — | — | no |", lines)
        self.assertIn("se mantiene la plantilla «informe»", lines)

    def test_without_results_there_is_no_section(self):
        original, report.TEMPLATE_REVIEW = report.TEMPLATE_REVIEW, Path("/nonexistent/template_review.json")
        try:
            self.assertEqual(report.template_review_section(), [])
        finally:
            report.TEMPLATE_REVIEW = original


class LanguageSectionTest(unittest.TestCase):
    """F12 L15: the «Idiomas (F12)» section of the report."""

    LANGUAGE = {"template": "v4", "changed": 196, "pending": 0, "meets_rule": False,
                "v4": {"answer_correct": 0.811, "refusal_correct": 0.911, "citations_correct": 0.943, "undue_refusals": 23},
                "manual": {"answer_correct": 0.811, "refusal_correct": 0.893, "citations_correct": 0.929, "undue_refusals": 20}}
    FIGURES = {"answer_correct": 0.509, "refusal_correct": 1.0, "citations_correct": 0.961, "undue_refusals": 16}
    CROSSLANG = {"english_answers": "es", "skipped": [], "keeps_card_text": False, "combinations": {
        "ES·EN": {"questions": 72, "pending": 0, "blocks_hit": 0.579, "manual": FIGURES, "reference": 0.825, "ok": False},
        "EN·EN": {"questions": 65, "pending": 3, "blocks_hit": 0.9, "manual": None, "ok": None}}}

    def render(self, language, crosslang):
        import json
        import tempfile

        with tempfile.TemporaryDirectory() as folder:
            paths = []
            for name, content in (("language.json", language), ("crosslang.json", crosslang)):
                path = Path(folder) / name
                if content is not None:
                    path.write_text(json.dumps(content))
                paths.append(path)
            original = report.LANGUAGE_REVIEW, report.CROSSLANG_REVIEW
            report.LANGUAGE_REVIEW, report.CROSSLANG_REVIEW = paths
            try:
                return "\n".join(report.language_section())
            finally:
                report.LANGUAGE_REVIEW, report.CROSSLANG_REVIEW = original

    def test_renders_both_reviews_and_the_card_decision(self):
        lines = self.render(self.LANGUAGE, self.CROSSLANG)
        self.assertIn("## Idiomas (F12)", lines)
        self.assertIn("| Automático | 81.1 % | 89.3 % | 92.9 % | 20 |", lines)
        self.assertIn("Regla: no cumple.", lines)
        self.assertIn("| ES·EN | español | inglés (72) | 57.9 % | 50.9 % | 82.5 % | 100.0 % | 96.1 % | 16 | no |", lines)
        self.assertIn("| EN·EN | inglés | inglés (65) | 90.0 % | pendiente (3) | — | — | — | — | — |", lines)
        self.assertIn("Todas las respuestas en español", lines)
        self.assertIn("el texto de la tarjeta del PNG no se sostiene", lines)

    def test_without_results_there_is_no_section(self):
        self.assertEqual(self.render(None, None), "")


if __name__ == "__main__":
    unittest.main()

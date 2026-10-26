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


if __name__ == "__main__":
    unittest.main()

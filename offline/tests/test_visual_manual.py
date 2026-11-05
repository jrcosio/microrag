"""The manual judgement of the visual answers, the text loss and the report (F18 T033, T037, R11, D-075)."""

import csv
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import visual_manual, visual_report, visual_text_loss  # noqa: E402
from eval.vision_runtime import Image, Text  # noqa: E402
from eval.visual_testset import VisualQuestion  # noqa: E402


def question(id_, type_="tabla"):
    return VisualQuestion(id=id_, type=type_, question="¿Cuánto?", doc="doc", pages=[["sha", 1]], expected_answer="12,5",
                          key_facts=["12,5"], review="ok", review_note="")


def metrics(correct, complete=True):
    return {"complete": complete, "summary": {"visual": {"correct": correct}}}


JUDGED = {"correct": True, "citations_ok": 1, "citations_total": 1}


class MainVariantsTest(unittest.TestCase):
    def test_texto_and_the_best_complete_of_each_family(self):
        runs = {"texto": metrics(0.3), "paginas-p1-b280-t0": metrics(0.5), "paginas-p2-b280-t0": metrics(0.6, complete=False),
                "mezcla-p1-b70-t0": metrics(0.55), "mezcla-p1-b140-t0": metrics(0.55)}
        self.assertEqual(visual_manual.main_variants(runs), ["texto", "paginas-p1-b280-t0", "mezcla-p1-b70-t0"])

    def test_the_sample_is_reproducible_and_shared(self):
        ids = [f"v{n:04d}" for n in range(1, 144)]
        first = visual_manual.sample_ids(ids, 30, 20261105)
        self.assertEqual(first, visual_manual.sample_ids(list(reversed(ids)), 30, 20261105))
        self.assertEqual(len(first), 30)


class SheetTest(unittest.TestCase):
    def test_a_verdict_stays_while_its_answer_does_not_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "manual.csv")
            rows = [visual_manual.row("comprobacion", "texto", question("v0001"), "Es 12,5 [1].", JUDGED, [{"label": "Doc"}]),
                    visual_manual.row("comprobacion", "texto", question("v0002"), "Es 3 [1].", JUDGED, [{"label": "Doc"}])]
            visual_manual.export(rows, path)
            judged = visual_manual.load(path)  # the reviewer fills the sheet by hand
            for r in judged:
                r["manual"], r["manual_citas"] = "correcta", "1/1"
            with path.open("w", encoding="utf-8-sig", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=visual_manual.FIELDS)
                writer.writeheader()
                writer.writerows(judged)
            again = [dict(rows[0]), {**rows[1], "respuesta_modelo": "Es 4 [1]."}]
            merged = {r["id"]: r for r in visual_manual.export(again, path)}
        self.assertEqual(merged["v0001"]["manual"], "correcta")
        self.assertEqual(merged["v0002"]["manual"], "")

    def test_agreement_and_decisive_figures(self):
        rows = [
            {**visual_manual.row("comprobacion", "texto", question("v0001"), "a", JUDGED, []), "manual": "correcta"},
            {**visual_manual.row("comprobacion", "texto", question("v0002"), "b", JUDGED, []), "manual": "parcial"},
            {**visual_manual.row("decisiva", "texto", question("v0001", "tabla"), "a", JUDGED, []), "manual": "correcta", "manual_citas": "1/1"},
            {**visual_manual.row("decisiva", "texto", question("v0002", "control"), "b", JUDGED, []), "manual": "incorrecta", "manual_citas": "0/1"},
            {**visual_manual.row("decisiva", "texto", question("v0003", "sin_respuesta"), "c", JUDGED, []), "manual": "correcta"},
        ]
        self.assertEqual(visual_manual.agreement(rows)["texto"], {"judged": 2, "agree": 1, "share": 0.5})
        figures = visual_manual.figures(rows, "texto")
        self.assertEqual(figures["visual_correct"], 100.0)
        self.assertEqual(figures["answerable_correct"], 50.0)
        self.assertEqual(figures["refusal_correct"], 100.0)
        self.assertEqual(figures["citations_correct"], 50.0)
        rows[-1]["manual"] = ""
        self.assertIsNone(visual_manual.figures(rows, "texto"))

    def test_an_unknown_verdict_is_refused(self):
        rows = [{**visual_manual.row("decisiva", "texto", question("v0001"), "a", JUDGED, []), "manual": "bien"}]
        with self.assertRaises(SystemExit):
            visual_manual.figures(rows, "texto")


class TextLossTest(unittest.TestCase):
    def test_the_message_hash_covers_texts_and_images_in_order(self):
        one = [Text("a"), Image(b"x"), Text("b")]
        self.assertEqual(visual_text_loss.parts_digest(one), visual_text_loss.parts_digest([Text("a"), Image(b"x"), Text("b")]))
        self.assertNotEqual(visual_text_loss.parts_digest(one), visual_text_loss.parts_digest([Text("a"), Image(b"y"), Text("b")]))
        self.assertNotEqual(visual_text_loss.parts_digest(one), visual_text_loss.parts_digest([Image(b"x"), Text("a"), Text("b")]))

    def test_types_and_points(self):
        self.assertEqual(visual_text_loss.judge_type("unanswerable"), "sin_respuesta")
        self.assertEqual(visual_text_loss.judge_type("followup"), "control")
        self.assertEqual(visual_text_loss.loss_points({"answer_correct": 0.80}, {"answer_correct": 0.785}), 1.5)


class ReportTest(unittest.TestCase):
    def test_the_phone_and_rule_sections_wait_for_their_files(self):
        self.assertIn("Pendiente", "\n".join(visual_report.phone_section(None)))
        self.assertIn("Pendiente", "\n".join(visual_report.decision_section(None)))
        self.assertIn("Pendiente", "\n".join(visual_report.retrieval_section(None, None)))

    def test_parity_lines(self):
        parity = {"vectors": {"same_bytes": {"n": 20, "min": 0.9951, "mean": 0.998, "threshold": 0.99, "below": []},
                              "rendered_pages": {"n": 10, "min": 0.97, "mean": 0.985}},
                  "reading": {"n": 12, "phone_correct": 9, "desktop_correct": 10, "same_verdict": 11}}
        text = "\n".join(visual_report.phone_section(parity))
        self.assertIn("coseno mínimo 0.9951", text)
        self.assertIn("9 de 12 correctas en el teléfono", text)


if __name__ == "__main__":
    unittest.main()

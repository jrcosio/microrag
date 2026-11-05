"""The visual test set: rules V1–V8, the review sheet and the freeze (F18 T023, research R9, D-033)."""

import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import visual_testset  # noqa: E402

DOCS = {"doc-a": ("sha-a", 3, "pdf"), "img-b": ("sha-b", 1, "jpg")}


def question(id_, type_="tabla", doc="doc-a", pages=((1,),), facts=("12,5",), review="ok"):
    sha = DOCS[doc][0] if doc else None
    return visual_testset.VisualQuestion(
        id=id_, type=type_, question="¿Cuál es la tasa de paro del tercer trimestre?", doc=doc,
        pages=[[sha, page[0]] for page in pages] if doc else [], expected_answer="12,5 %",
        key_facts=list(facts), review=review, review_note="",
    )


def balanced(total=100):
    plan = [("tabla", 20), ("grafico", 15), ("escaneado", 15), ("imagen", 15), ("mixta", 5), ("control", 20), ("sin_respuesta", 10)]
    out, n = [], 0
    for type_, count in plan:
        for _ in range(count):
            n += 1
            if type_ == "sin_respuesta":
                out.append(question(f"v{n:04d}", type_, doc=None, facts=()))
            elif type_ == "imagen":
                out.append(question(f"v{n:04d}", type_, doc="img-b"))
            else:
                out.append(question(f"v{n:04d}", type_))
    return out[:total]


class RulesTest(unittest.TestCase):
    def test_a_balanced_set_passes(self):
        self.assertEqual(visual_testset.validate(balanced(), DOCS), [])

    def test_v1_unknown_type_or_review(self):
        errors = visual_testset.validate([question("v0001", type_="foto")] + balanced()[1:], DOCS)
        self.assertTrue(any(e.startswith("V1") for e in errors))

    def test_v2_duplicated_ids(self):
        items = balanced()
        items[1] = question("v0001")
        self.assertTrue(any(e.startswith("V2") for e in visual_testset.validate(items, DOCS)))

    def test_v3_pages_must_exist(self):
        items = balanced()
        items[0] = question("v0001", pages=((4,),))
        self.assertTrue(any(e.startswith("V3") for e in visual_testset.validate(items, DOCS)))

    def test_v4_a_page_of_its_own_document_and_only_corpus_pages(self):
        items = balanced()
        bad = question("v0001")
        bad.pages = [["sha-b", 1]]
        items[0] = bad
        self.assertTrue(any(e.startswith("V4") and "no page" in e for e in visual_testset.validate(items, DOCS)))
        bad.pages = [["sha-a", 1], ["sha-x", 1]]
        self.assertTrue(any(e.startswith("V4") and "outside" in e for e in visual_testset.validate(items, DOCS)))

    def test_v4_pages_of_another_document_with_the_same_content_are_allowed(self):
        items = balanced()
        items[0].pages = [["sha-a", 1], ["sha-b", 1]]
        self.assertEqual(visual_testset.validate(items, DOCS), [])
        items[0].pages = [["sha-a", 1], ["sha-b", 2]]
        self.assertTrue(any(e.startswith("V3") for e in visual_testset.validate(items, DOCS)))

    def test_v5_facts_only_when_answerable(self):
        items = balanced()
        items[0] = question("v0001", facts=())
        items[-1] = question(items[-1].id, "sin_respuesta", doc="doc-a")
        errors = visual_testset.validate(items, DOCS)
        self.assertEqual(sum(e.startswith("V5") for e in errors), 2)

    def test_v7_minimums_count_only_accepted_questions(self):
        items = balanced()
        for item in items[:20]:
            item.review = "descartada"
        errors = visual_testset.validate(items, DOCS)
        self.assertTrue(any(e.startswith("V7") and "tabla" in e for e in errors))
        self.assertTrue(any(e.startswith("V7") and "100" in e for e in errors))

    def test_v8_visual_share(self):
        items = balanced()
        for item in items:
            if item.type in ("grafico", "escaneado"):
                item.type = "control"
        self.assertTrue(any(e.startswith("V8") for e in visual_testset.validate(items, DOCS)))

    def test_pre_review_only_checks_the_shape(self):
        items = [question("v0001", review="pendiente")]
        self.assertEqual(visual_testset.validate(items, DOCS, pre_review=True), [])


class ReviewTest(unittest.TestCase):
    def test_sheet_and_verdicts_round_trip(self):
        items = [question("v0001", review="pendiente"), question("v0002", review="pendiente")]
        with tempfile.TemporaryDirectory() as tmp:
            sheet = Path(tmp, "review.csv")
            visual_testset.write_review_sheet(items, sheet)
            rows = list(csv.DictReader(sheet.open(encoding="utf-8")))
            rows[0]["veredicto"], rows[0]["comentario"] = "ok", "revisada por Claude (D-033)"
            rows[1]["veredicto"], rows[1]["comentario"] = "descartar", "ambigua"
            with sheet.open("w", encoding="utf-8", newline="") as out:
                writer = csv.DictWriter(out, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            reviewed = visual_testset.apply_review(items, sheet)
        self.assertEqual([(q.review, q.review_note) for q in reviewed],
                         [("ok", "revisada por Claude (D-033)"), ("descartada", "ambigua")])

    def test_an_unknown_verdict_is_refused(self):
        items = [question("v0001", review="pendiente")]
        with tempfile.TemporaryDirectory() as tmp:
            sheet = Path(tmp, "review.csv")
            visual_testset.write_review_sheet(items, sheet)
            rows = list(csv.DictReader(sheet.open(encoding="utf-8")))
            rows[0]["veredicto"] = "quizá"
            with sheet.open("w", encoding="utf-8", newline="") as out:
                writer = csv.DictWriter(out, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaises(SystemExit):
                visual_testset.apply_review(items, sheet)

    def test_save_and_load(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "questions.jsonl")
            visual_testset.save([question("v0001")], path)
            self.assertEqual(visual_testset.load(path)[0].pages, [["sha-a", 1]])
            self.assertEqual(json.loads(path.read_text().splitlines()[0])["id"], "v0001")


if __name__ == "__main__":
    unittest.main()

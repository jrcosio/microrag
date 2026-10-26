"""Test set rules T1–T9, relevants from evidence and the review flow (F05 T020, contracts/testset.md)."""

import csv
import dataclasses
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import testset  # noqa: E402
from eval.testset import Question  # noqa: E402

DOC_TEXT = {
    "doc-a": "Las bombas se revisan cada 500 horas de funcionamiento. El aceite se cambia una vez al año.",
    "doc-b": "Las facturas se conservan durante cuatro años. El plazo de presentación termina el día 20.",
}
SHA = {"doc-a": "a" * 64, "doc-b": "b" * 64}
CHUNKS = {SHA["doc-a"]: [(0, "Las bombas se revisan cada 500 horas de funcionamiento."), (1, "El aceite se cambia una vez al año.")],
          SHA["doc-b"]: [(0, "Las facturas se conservan durante cuatro años. El plazo de presentación termina el día 20.")]}


def answerable(qid, doc="doc-a", evidence="cada 500 horas de funcionamiento", relevant=((0,),), **kw):
    base = dict(id=qid, type="answerable", question="¿Con qué frecuencia hay que revisar las bombas?", previous=None,
                doc=doc, content_type="dato", expected_answer="Cada 500 horas de funcionamiento.", key_facts=["500 horas"],
                evidence=evidence, relevant_ref=[[SHA[doc], o] for (o,) in relevant], review="ok", review_note=None)
    return Question(**{**base, **kw})


def unanswerable(qid, **kw):
    base = dict(id=qid, type="unanswerable", question="¿Cuánto cuesta el seguro de la bomba?", previous=None, doc=None,
                content_type=None, expected_answer=None, key_facts=[], evidence=None, relevant_ref=[], review="ok", review_note=None)
    return Question(**{**base, **kw})


def validate(questions, **kw):
    limits = dict(min_answerable=1, min_unanswerable=1, min_followup=0, min_reviewed=1, max_doc_share=1.0, min_per_content_type=1)
    return testset.validate(questions, DOC_TEXT, SHA, CHUNKS, **{**limits, **kw})


class RulesTest(unittest.TestCase):
    def test_valid_set_has_no_errors(self):
        self.assertEqual(validate([answerable("q1"), unanswerable("q2")]), [])

    def test_t1_ids_and_types(self):
        self.assertTrue(validate([answerable("q1"), answerable("q1"), unanswerable("q2")]))
        self.assertTrue(validate([answerable("q1", type="other"), unanswerable("q2")]))

    def test_t2_and_t3_minimums_ignore_discarded(self):
        questions = [answerable("q1"), answerable("q3", review="discarded"), unanswerable("q2")]
        self.assertTrue(validate(questions, min_answerable=2))
        self.assertTrue(validate([answerable("q1", review="pending"), unanswerable("q2", review="pending")], min_reviewed=1))

    def test_t4_evidence_and_key_facts(self):
        self.assertTrue(validate([answerable("q1", evidence="cada 600 horas"), unanswerable("q2")]))
        self.assertTrue(validate([answerable("q1", key_facts=["700 horas"]), unanswerable("q2")]))
        self.assertTrue(validate([answerable("q1", key_facts=[]), unanswerable("q2")]))
        self.assertTrue(validate([answerable("q1", doc="doc-x", relevant=()), unanswerable("q2")]))

    def test_t5_relevant_must_match_the_evidence(self):
        self.assertTrue(validate([answerable("q1", relevant=((1,),)), unanswerable("q2")]))

    def test_t6_unanswerable_has_no_evidence(self):
        self.assertTrue(validate([answerable("q1"), unanswerable("q2", evidence="algo")]))

    def test_t7_followup_points_to_an_existing_question(self):
        ok = answerable("q3", type="followup", previous="q2", question="¿Y el aceite?", evidence="una vez al año",
                        relevant=((1,),), key_facts=["una vez al año"], expected_answer="Una vez al año.")
        self.assertEqual(validate([answerable("q1"), unanswerable("q2"), ok]), [])
        self.assertTrue(validate([answerable("q1"), unanswerable("q2"), dataclasses.replace(ok, previous="q9")]))

    def test_t8_question_must_not_copy_eight_words(self):
        copied = "¿Las bombas se revisan cada 500 horas de funcionamiento?"
        self.assertTrue(validate([answerable("q1", question=copied, evidence="Las bombas se revisan cada 500 horas de funcionamiento"),
                                  unanswerable("q2")]))

    def test_t9_document_share_and_content_types(self):
        questions = [answerable("q1"), answerable("q3"), answerable("q4", doc="doc-b", evidence="durante cuatro años",
                     key_facts=["cuatro años"], expected_answer="Cuatro años.", relevant=((0,),)), unanswerable("q2")]
        self.assertTrue(validate(questions, max_doc_share=0.5))
        self.assertTrue(validate(questions, min_per_content_type=4))


class RelevantTest(unittest.TestCase):
    def test_whole_evidence_in_one_or_more_chunks(self):
        self.assertEqual(testset.derive_relevant("aceite se cambia", CHUNKS[SHA["doc-a"]]), [1])

    def test_split_evidence_uses_its_longest_sentence(self):
        chunks = [(0, "Primera frase corta. Inicio"), (1, "de la segunda frase que es bastante más larga.")]
        evidence = "Primera frase corta. Inicio de la segunda frase que es bastante más larga."
        self.assertEqual(testset.derive_relevant(evidence, chunks), [])
        self.assertEqual(testset.derive_relevant("Primera frase corta. Otra frase mucho más larga que la primera.",
                                                 [(0, "Otra frase mucho más larga que la primera.")]), [0])

    def test_whitespace_is_normalized(self):
        self.assertEqual(testset.derive_relevant("cada  500\nhoras", CHUNKS[SHA["doc-a"]]), [0])


class ReviewTest(unittest.TestCase):
    def test_sheet_is_stratified_seeded_and_skips_reviewed(self):
        questions = [answerable(f"q{i}", review="pending") for i in range(50)] + [answerable("r1", review="ok")]
        first = testset.review_sample(questions, size=20, seed=7)
        self.assertEqual(len(first), 20)
        self.assertEqual(first, testset.review_sample(questions, size=20, seed=7))
        self.assertNotIn("r1", [q.id for q in first])

    def test_apply_verdicts(self):
        questions = [answerable("q1", review="pending"), answerable("q2", review="pending"), answerable("q3", review="pending")]
        with tempfile.TemporaryDirectory() as tmp:
            sheet = Path(tmp, "review.csv")
            testset.write_review_sheet(questions, sheet, {"doc-a": "Manual"})
            rows = list(csv.DictReader(sheet.open(encoding="utf-8-sig")))
            rows[0]["veredicto"], rows[1]["veredicto"], rows[1]["comentario"], rows[2]["veredicto"] = "OK", "corregir", "más concreta", "Descartar"
            with sheet.open("w", encoding="utf-8-sig", newline="") as out:
                writer = csv.DictWriter(out, fieldnames=rows[0].keys())
                writer.writeheader()
                writer.writerows(rows)
            updated, summary = testset.apply_review(questions, sheet)
        states = {q.id: (q.review, q.review_note) for q in updated}
        self.assertEqual(states, {"q1": ("ok", None), "q2": ("pending", "más concreta"), "q3": ("discarded", None)})
        self.assertEqual(summary, {"ok": 1, "corregir": 1, "descartar": 1})


if __name__ == "__main__":
    unittest.main()

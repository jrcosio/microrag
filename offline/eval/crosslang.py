"""Cross-language measure of F12 (D-058, D-060; specs/012-settings-languages/contracts/eval-language.md L7–L14).

Three combinations of document and question language, each a test set of its own in testset/crosslang/,
so the Spanish test set, its runs and the goldens keep their fingerprints:

- ES·EN: the Spanish documents (index ref) with the F10 sample translated into English;
- EN·EN: English documents (index en) with questions written in English;
- EN·ES: the same English documents with those questions translated into Spanish.

Every answer is judged by hand. A combination is compared with its reference for correct answers:
ES·EN with its Spanish source questions under v4, EN·ES with EN·EN (D-060).
"""

import dataclasses
import json
import random
from pathlib import Path

from eval import testset
from eval import template_review as tr

OFFLINE = Path(__file__).resolve().parents[1]
CROSSLANG = OFFLINE / "testset" / "crosslang"
SEED = 20261101
UNANSWERABLE = 15
REVIEW_FIELDS = ["combinacion", "id", "id_origen", "tipo", "pregunta", "respuesta_esperada", "datos_clave", "fuentes",
                 "respuesta", "regla", "regla_citas", "manual", "manual_citas", "comentario"]
# Validation minimums of a crosslang set (L10): proportional to its size, not the 426 of F05.
MINIMUMS = {"min_answerable": 40, "min_unanswerable": 15, "min_followup": 10, "min_reviewed": 0,
            "max_doc_share": 0.5, "min_per_content_type": 0}


@dataclasses.dataclass(frozen=True)
class Combination:
    key: str
    build: str
    set_name: str
    question_language: str


COMBINATIONS = (
    Combination("ES·EN", "ref", "es-docs.en", "en"),
    Combination("EN·EN", "en", "en-docs.en", "en"),
    Combination("EN·ES", "en", "en-docs.es", "es"),
)


def answer_language(combination, english_answers):
    """Spanish questions are answered in Spanish; English ones as the run asks («auto», or «es» if not adopted)."""
    return "es" if combination.question_language == "es" else english_answers


def set_path(name):
    return CROSSLANG / f"{name}.jsonl"


def load_set(path):
    """The questions of a crosslang set and, by id, the id of the Spanish question each one translates."""
    questions, sources = [], {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        sources[row["id"]] = row.pop("source_id", None)
        questions.append(testset.Question(**row))
    return questions, sources


def es_docs_sources(questions, parity_ids, seed=SEED, unanswerable=UNANSWERABLE):
    """Spanish source questions of ES·EN, in this order: the F10 sample, the previous questions its follow-ups
    need, and unanswerable questions up to [unanswerable] (those previous ones count), drawn with [seed]."""
    by_id = {q.id: q for q in questions if q.review != "discarded"}
    chosen = list(parity_ids)
    for qid in parity_ids:
        previous = by_id[qid].previous
        if previous and previous not in chosen:
            chosen.append(previous)
    have = sum(by_id[qid].type == "unanswerable" for qid in chosen)
    pool = sorted(qid for qid, q in by_id.items() if q.type == "unanswerable" and qid not in chosen)
    chosen += sorted(random.Random(seed).sample(pool, max(0, unanswerable - have)))
    return chosen


def reference_correct(source_rows, source_ids):
    """Share of correct manual verdicts among the answerable and follow-up questions of [source_ids]."""
    rows = [source_rows[qid] for qid in source_ids if source_rows[qid]["tipo"] in ("answerable", "followup")]
    return sum(r["manual"] == "correcta" for r in rows) / len(rows) if rows else None


def combination_ok(figures, reference):
    """L14 for one combination: the report floors and, when it has a reference, at most 10 points below it."""
    from eval import language_review as lr

    if not lr.meets_floors(figures):
        return False
    return reference is None or lr.within_points(figures["answer_correct"], reference)


def keeps_card_text(results):
    """FR-032: the PNG text stays when every combination is ok; [results] maps a key to (figures, reference)."""
    return all(combination_ok(figures, reference) for figures, reference in results.values())


def review_key(row):
    return row["combinacion"], row["id"]


def kept_verdict(old, answer):
    """A verdict written for another answer does not carry over: the run was generated again differently."""
    if not old or old.get("respuesta") != answer:
        return {"manual": "", "manual_citas": "", "comentario": ""}
    return {key: old.get(key, "") for key in ("manual", "manual_citas", "comentario")}


EPS = tr.EPS

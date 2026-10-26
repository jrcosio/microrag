"""Test set: format, validation (rules T1–T9), relevants from evidence and the review flow.

F05 contracts/testset.md and data-model §4. Relevants are derived from the literal evidence, so they
survive any change of chunk size (research R8).
"""

import csv
import dataclasses
import json
import random
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

OFFLINE = Path(__file__).resolve().parents[1]
TESTSET = OFFLINE / "testset" / "questions.jsonl"
REVIEW_SHEET = OFFLINE / "testset" / "review.csv"

TYPES = ("answerable", "unanswerable", "followup")
CONTENT_TYPES = ("texto", "lista", "tabla", "procedimiento", "dato")
REVIEW_STATES = ("pending", "ok", "fixed", "discarded")
VERDICTS = {"ok": "ok", "corregir": "corregir", "descartar": "descartar"}
SHEET_FIELDS = ["id", "documento", "tipo", "pregunta", "pregunta_anterior", "respuesta_esperada", "datos_clave",
                "evidencia", "veredicto", "comentario"]

MIN_ANSWERABLE, MIN_UNANSWERABLE, MIN_FOLLOWUP, MIN_REVIEWED = 300, 50, 40, 100
MAX_DOC_SHARE, MIN_PER_CONTENT_TYPE, COPY_NGRAM = 0.15, 10, 8
SENTENCE = re.compile(r"(?<=[.!?;:])\s+")


@dataclasses.dataclass(frozen=True)
class Question:
    id: str
    type: str
    question: str
    previous: str | None
    doc: str | None
    content_type: str | None
    expected_answer: str | None
    key_facts: list
    evidence: str | None
    relevant_ref: list
    review: str
    review_note: str | None

    @property
    def has_answer(self):
        return self.type in ("answerable", "followup")


def load(path=TESTSET):
    if not path.exists():
        return []
    return [Question(**json.loads(line)) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def save(questions, path=TESTSET):
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = (json.dumps(dataclasses.asdict(q), ensure_ascii=False) + "\n" for q in questions)
    path.write_text("".join(lines), encoding="utf-8")


def norm_ws(text):
    return " ".join(text.split())


def fold(text):
    """Lowercase, no diacritics, punctuation as spaces: for overlap checks only."""
    plain = unicodedata.normalize("NFD", text.lower())
    plain = "".join(c for c in plain if unicodedata.category(c) != "Mn")
    return re.sub(r"[^\w]+", " ", plain).split()


def derive_relevant(evidence, chunks):
    """Ordinals of the chunks that contain the evidence; if none holds it whole, those with its longest sentence."""
    target = norm_ws(evidence)
    hits = [ordinal for ordinal, content in chunks if target in norm_ws(content)]
    if hits:
        return hits
    longest = max(SENTENCE.split(target), key=len)
    return [ordinal for ordinal, content in chunks if longest in norm_ws(content)]


def _copies_evidence(question, evidence, n=COPY_NGRAM):
    words = fold(evidence)
    grams = {tuple(words[i:i + n]) for i in range(len(words) - n + 1)}
    asked = fold(question)
    return any(tuple(asked[i:i + n]) in grams for i in range(len(asked) - n + 1))


def validate(questions, doc_texts, doc_sha, chunks_by_sha, min_answerable=MIN_ANSWERABLE, min_unanswerable=MIN_UNANSWERABLE,
             min_followup=MIN_FOLLOWUP, min_reviewed=MIN_REVIEWED, max_doc_share=MAX_DOC_SHARE,
             min_per_content_type=MIN_PER_CONTENT_TYPE):
    """Errors of the set against rules T1–T9 (an empty list means valid)."""
    errors = []
    ids = Counter(q.id for q in questions)
    errors += [f"T1 duplicated id {qid}" for qid, n in ids.items() if n > 1]
    by_id = {q.id: q for q in questions}
    live = [q for q in questions if q.review != "discarded"]
    for q in questions:
        if q.type not in TYPES:
            errors.append(f"T1 {q.id}: unknown type {q.type}")
        if q.review not in REVIEW_STATES:
            errors.append(f"T1 {q.id}: unknown review state {q.review}")
    counts = Counter(q.type for q in live)
    for qtype, minimum in (("answerable", min_answerable), ("unanswerable", min_unanswerable), ("followup", min_followup)):
        if counts[qtype] < minimum:
            errors.append(f"T2 {counts[qtype]} {qtype} questions, need {minimum}")
    reviewed = sum(q.review in ("ok", "fixed") for q in live)
    if reviewed < min_reviewed:
        errors.append(f"T3 {reviewed} reviewed questions, need {min_reviewed}")
    for q in live:
        if q.has_answer:
            errors += _validate_answer(q, doc_texts, doc_sha, chunks_by_sha)
        elif q.doc or q.evidence or q.key_facts or q.relevant_ref:
            errors.append(f"T6 {q.id}: an unanswerable question carries evidence")
        if q.type == "followup" and (q.previous not in by_id or by_id[q.previous].review == "discarded"):
            errors.append(f"T7 {q.id}: previous question {q.previous} does not exist")
        if q.has_answer and q.evidence and _copies_evidence(q.question, q.evidence):
            errors.append(f"T8 {q.id}: the question copies {COPY_NGRAM} or more words of the evidence")
    answered = [q for q in live if q.type == "answerable"]
    for doc, n in Counter(q.doc for q in answered).items():
        if answered and n / len(answered) > max_doc_share:
            errors.append(f"T9 {doc} has {n} of {len(answered)} answerable questions (> {max_doc_share:.0%})")
    for content_type, n in Counter(q.content_type for q in answered).items():
        if n < min_per_content_type:
            errors.append(f"T9 content type {content_type} has {n} questions, need {min_per_content_type}")
    return errors


def _validate_answer(q, doc_texts, doc_sha, chunks_by_sha):
    errors = []
    if q.doc not in doc_texts:
        return [f"T4 {q.id}: unknown document {q.doc}"]
    if q.content_type not in CONTENT_TYPES:
        errors.append(f"T4 {q.id}: unknown content type {q.content_type}")
    if not q.evidence or norm_ws(q.evidence) not in norm_ws(doc_texts[q.doc]):
        errors.append(f"T4 {q.id}: evidence not found in {q.doc}")
    if not 1 <= len(q.key_facts) <= 4:
        errors.append(f"T4 {q.id}: {len(q.key_facts)} key facts, need 1 to 4")
    folded_answer = " ".join(fold(q.expected_answer or ""))
    for fact in q.key_facts:
        if " ".join(fold(fact)) not in folded_answer:
            errors.append(f"T4 {q.id}: key fact «{fact}» is not in the expected answer")
    sha = doc_sha[q.doc]
    expected = [[sha, ordinal] for ordinal in derive_relevant(q.evidence or "", chunks_by_sha.get(sha, []))]
    if not expected:
        errors.append(f"T5 {q.id}: no chunk holds the evidence")
    elif sorted(q.relevant_ref) != sorted(expected):
        errors.append(f"T5 {q.id}: relevant_ref {q.relevant_ref} differs from the evidence ({expected})")
    return errors


def with_relevants(questions, doc_sha, chunks_by_sha):
    """Fills relevant_ref from the evidence for the reference index."""
    out = []
    for q in questions:
        if q.has_answer and q.doc in doc_sha and q.evidence:
            sha = doc_sha[q.doc]
            relevant = [[sha, o] for o in derive_relevant(q.evidence, chunks_by_sha.get(sha, []))]
            q = dataclasses.replace(q, relevant_ref=relevant)
        out.append(q)
    return out


def review_sample(questions, size=MIN_REVIEWED, seed=20261026):
    """Questions for the next review sheet: not reviewed yet, stratified by type and document."""
    pending = [q for q in questions if q.review == "pending" and not q.review_note]
    groups = defaultdict(list)
    for q in pending:
        groups[(q.type, q.doc)].append(q)
    rng = random.Random(seed)
    for key in sorted(groups, key=str):
        rng.shuffle(groups[key])
    picked, keys = [], sorted(groups, key=str)
    while len(picked) < min(size, len(pending)):
        for key in keys:
            if groups[key] and len(picked) < size:
                picked.append(groups[key].pop())
    return sorted(picked, key=lambda q: q.id)


def write_review_sheet(questions, path, titles, previous_by_id=None):
    previous_by_id = previous_by_id or {}
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as out:
        writer = csv.DictWriter(out, fieldnames=SHEET_FIELDS)
        writer.writeheader()
        for q in questions:
            writer.writerow({
                "id": q.id, "documento": titles.get(q.doc, "") if q.doc else "(sin respuesta en el corpus)",
                "tipo": q.type, "pregunta": q.question, "pregunta_anterior": previous_by_id.get(q.previous, ""),
                "respuesta_esperada": q.expected_answer or "", "datos_clave": " | ".join(q.key_facts),
                "evidencia": q.evidence or "", "veredicto": "", "comentario": "",
            })


def _verdict(raw):
    key = " ".join(fold(raw or ""))
    return VERDICTS.get(key)


def apply_review(questions, path):
    """Applies the verdicts of a filled review sheet; returns the updated questions and a summary."""
    rows = {row["id"]: row for row in csv.DictReader(path.open(encoding="utf-8-sig"))}
    summary, out = Counter(), []
    for q in questions:
        row = rows.get(q.id)
        verdict = _verdict(row["veredicto"]) if row else None
        if verdict == "ok":
            q = dataclasses.replace(q, review="ok", review_note=None)
        elif verdict == "descartar":
            q = dataclasses.replace(q, review="discarded", review_note=(row["comentario"] or None))
        elif verdict == "corregir":
            q = dataclasses.replace(q, review="pending", review_note=row["comentario"] or "corregir")
        if verdict:
            summary[verdict] += 1
        out.append(q)
    return out, dict(summary)

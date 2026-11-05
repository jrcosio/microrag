"""The visual test set of F18 (research R9, data-model §1.3, D-033, D-073).

Questions are drafted by separate agents from the page images and reviewed by the main session, page in
view. Relevance is by (document, page), not by literal evidence: scanned pages and images have no text.
"""

import csv
import json
from dataclasses import asdict, dataclass
from pathlib import Path

OFFLINE = Path(__file__).resolve().parents[1]
TESTSET = OFFLINE / "testset" / "visual" / "questions.jsonl"
REVIEW = OFFLINE / "testset" / "visual" / "review.csv"

TYPES = ("tabla", "grafico", "escaneado", "imagen", "mixta", "control", "sin_respuesta")
VISUAL_TYPES = ("tabla", "grafico", "escaneado", "imagen")
REVIEWS = ("pendiente", "ok", "descartada")
VERDICTS = {"ok": "ok", "descartar": "descartada"}
# Accepted minimums per type: about 80 % of the drafting plan of research R9, so a few discards still pass.
MINIMUMS = {"tabla": 15, "grafico": 10, "escaneado": 10, "imagen": 10, "mixta": 3, "control": 15, "sin_respuesta": 8}
MIN_ACCEPTED = 100
MIN_VISUAL_SHARE = 0.60
SHEET_FIELDS = ("id", "type", "doc", "pages", "question", "expected_answer", "key_facts", "veredicto", "comentario")


@dataclass
class VisualQuestion:
    id: str
    type: str
    question: str
    doc: str | None
    pages: list
    expected_answer: str
    key_facts: list
    review: str
    review_note: str


def load(path=TESTSET):
    return [VisualQuestion(**json.loads(line)) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def save(questions, path=TESTSET):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text("".join(json.dumps(asdict(q), ensure_ascii=False) + "\n" for q in questions), encoding="utf-8")


def corpus_pages(docs, docs_dir):
    """{doc id: (sha256, page count, format)} of the visual corpus."""
    from eval import raster

    return {doc.id: (doc.sha256, raster.page_count(docs_dir / doc.file_name) if doc.format == "pdf" else 1, doc.format)
            for doc in docs}


def validate(questions, docs, pre_review=False):
    """Rules V1–V8; [docs] is {doc id: (sha256, pages, format)}. Before the review, only the shape (V1–V6)."""
    errors = []
    seen = set()
    for q in questions:
        if q.type not in TYPES or q.review not in REVIEWS:
            errors.append(f"V1 {q.id}: type {q.type!r} or review {q.review!r} not allowed")
        if q.id in seen:
            errors.append(f"V2 {q.id}: duplicated id")
        seen.add(q.id)
        answerable = q.type != "sin_respuesta"
        if answerable:
            if q.doc not in docs:
                errors.append(f"V3 {q.id}: unknown document {q.doc}")
                continue
            sha, count, _ = docs[q.doc]
            for page_sha, page in q.pages:
                if page_sha != sha:
                    errors.append(f"V4 {q.id}: page of another document ({page_sha[:8]})")
                elif not 1 <= page <= count:
                    errors.append(f"V3 {q.id}: page {page} of {count}")
            if not q.pages or not q.key_facts:
                errors.append(f"V5 {q.id}: an answerable question needs its pages and key facts")
        elif q.doc or q.pages or q.key_facts:
            errors.append(f"V5 {q.id}: a question without answer has no document, pages or key facts")
        if not q.question.strip() or not q.question.strip().endswith("?"):
            errors.append(f"V6 {q.id}: the question must be a question")
    if pre_review:
        return errors
    accepted = [q for q in questions if q.review == "ok"]
    for type_, minimum in MINIMUMS.items():
        count = sum(q.type == type_ for q in accepted)
        if count < minimum:
            errors.append(f"V7 {type_}: {count} accepted, {minimum} needed")
    if len(accepted) < MIN_ACCEPTED:
        errors.append(f"V7 total: {len(accepted)} accepted, {MIN_ACCEPTED} needed")
    visual = sum(q.type in VISUAL_TYPES for q in accepted)
    if accepted and visual / len(accepted) < MIN_VISUAL_SHARE:
        errors.append(f"V8 visual share: {visual} of {len(accepted)} ({visual / len(accepted):.0%}), {MIN_VISUAL_SHARE:.0%} needed")
    return errors


def write_review_sheet(questions, path=REVIEW):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with Path(path).open("w", encoding="utf-8", newline="") as out:
        writer = csv.DictWriter(out, fieldnames=SHEET_FIELDS)
        writer.writeheader()
        for q in questions:
            writer.writerow({"id": q.id, "type": q.type, "doc": q.doc or "", "pages": " ".join(str(p) for _, p in q.pages),
                             "question": q.question, "expected_answer": q.expected_answer, "key_facts": " | ".join(q.key_facts),
                             "veredicto": "", "comentario": ""})


def apply_review(questions, path=REVIEW):
    """Verdicts of the sheet: ok or descartar, with the reviewer's note (D-033)."""
    rows = {row["id"]: row for row in csv.DictReader(Path(path).open(encoding="utf-8"))}
    for q in questions:
        row = rows.get(q.id)
        if row is None or not row["veredicto"]:
            continue
        if row["veredicto"] not in VERDICTS:
            raise SystemExit(f"{q.id}: unknown verdict {row['veredicto']!r}; use {', '.join(VERDICTS)}")
        q.review = VERDICTS[row["veredicto"]]
        q.review_note = row["comentario"]
    return questions


def counts(questions):
    accepted = [q for q in questions if q.review == "ok"]
    return {"total": len(questions), "accepted": len(accepted),
            "by_type": {t: sum(q.type == t for q in accepted) for t in TYPES},
            "visual_share": round(sum(q.type in VISUAL_TYPES for q in accepted) / len(accepted), 3) if accepted else 0.0}

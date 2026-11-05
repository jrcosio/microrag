"""The text loss of the chosen configuration on F05's test set (F18 research R11, D-075).

The chosen mix answers F05's questions: D-034's text blocks, without the chunks that lie on a page that enters as
an image (duplicate rule), plus the first pages of F05's 6 PDFs by the page list (D-076); MD and TXT documents have
no pages. Follow-ups see this run's previous answer, as in F05. An answer equal to the v4 one keeps its manual
verdict (D-056); the others are judged by hand in reports/visual-text-loss.csv, whose verdicts stay between runs.
The loss is v4's manual correct answers minus the mix's, in points, over the 323 answerable questions.

Gemma keeps the Mac GPU's default fp16 activations here, as F05's v4 answers were generated: in F05's corpus they
do not corrupt figures (with FP32, 239 of 426 answers change their wording and the rule judge gives 72.2 % against
72.4 %), so only the pages change what the review has to judge.
"""

import csv
import hashlib
import json
from contextlib import ExitStack
from pathlib import Path

from eval import builds, corpus, index, pipeline, visual_index, visual_judge, visual_prompt, visual_retrieval
from eval.retrieval import FUSED_LIMIT
from eval.selector import ContextSelector
from eval.vision_runtime import Text

OFFLINE = Path(__file__).resolve().parents[1]
REVIEW = OFFLINE / "reports" / "visual-text-loss.csv"
RUNS = visual_index.F05_DIR / "text-loss"
FIELDS = ["id", "variante", "tipo", "pregunta", "respuesta_esperada", "datos_clave", "fuentes", "respuesta_v4", "manual_v4",
          "respuesta_mezcla", "regla", "regla_citas", "manual", "manual_citas", "comentario"]


def parts_digest(parts):
    """A stable hash of the message: its texts and the hash of each image, in order."""
    flat = [["t", p.text] if isinstance(p, Text) else ["i", hashlib.sha256(p.data).hexdigest()] for p in parts]
    return hashlib.sha256(json.dumps(flat, ensure_ascii=False).encode("utf-8")).hexdigest()


def judge_type(f05_type):
    """F05's types in the visual judge's terms: only the unanswerable questions are judged as refusals."""
    return "sin_respuesta" if f05_type == "unanswerable" else "control"


def loss_points(v4, mix):
    """Points of manual correct answers the mix loses against v4 (positive is a loss)."""
    return round(100 * (v4["answer_correct"] - mix["answer_correct"]), 2)


def _source_label(block):
    where = " › ".join(x for x in (block.document_name, block.heading_path) if x is not None)
    page = f" (p. {block.page})" if block.page is not None else ""
    return f"{where}{page}"


def run(variant, decision, rules):
    """Generates [variant] on F05 and writes the review sheet; returns the figures, None while answers are unjudged."""
    from eval import models, report, vision_runtime
    from eval import template_review as tr
    from eval.visual_cli import _parse_variant

    family, count, budget, tiles = _parse_variant(variant)
    if family != "mezcla":
        raise SystemExit(f"{variant}: the text loss measures a mix (research R12)")
    px, tokens, dim = decision["px"], decision["tokens"], decision["dim"]
    config, base_answers, _, base_rows = pipeline._active_template_base()
    ctx = pipeline.load_context(config.build)
    source = corpus.CORPORA["es"]
    index_path = builds.index_path(config.build)
    chunk_pages = visual_index.chunk_pages(index_path, ctx.docs, source.docs, out=visual_index.F05_CHUNK_PAGES)
    chunk_doc = visual_retrieval.chunk_documents(index_path)
    visual_index.build_pages(px, tokens, "es")
    pages = visual_index.load_pages(visual_index.pages_path(px, tokens, "es"), dim)
    _, pdfs = visual_index.page_documents("es")
    docs = {doc.sha256: doc for doc in pdfs}
    retrieval = pipeline._retrieval_rows(ctx, config)
    info = index.build_info(ctx.db)
    selector = ContextSelector(config.max_blocks, config.max_per_document, pipeline.budget_tokens(config, info["chars_per_token"]),
                               2 if config.neighbors else 0)
    neighbor = pipeline._neighbor(ctx.db)
    embedder = visual_retrieval.QueryEmbedder()
    images = {}

    def page_images(key):
        if key not in images:
            from eval import raster

            sha, page = key
            image = raster.render_page(source.docs / docs[sha].file_name, page, round(px * 1.8) if tiles else px)
            images[key] = tuple(raster.encode(part, "jpeg") for part in (raster.tiles(image) if tiles else (image,)))
        return images[key]

    by_id = {q.id: q for q in pipeline.live_questions()}
    questions = sorted(by_id.values(), key=lambda q: (q.type == "followup", q.id))
    base_by_id = {r["id"]: r for r in base_rows}
    folder = RUNS / variant
    folder.mkdir(parents=True, exist_ok=True)
    out_path = folder / "answers.jsonl"
    done = {}
    if out_path.exists():
        done = {json.loads(l)["id"]: json.loads(l) for l in out_path.read_text(encoding="utf-8").splitlines() if l.strip()}
    existing = {}
    if REVIEW.exists():
        existing = {(r["id"], r["variante"]): r for r in csv.DictReader(REVIEW.open(encoding="utf-8-sig"))}

    answers, verdicts, review_rows = {}, {}, []
    with ExitStack() as stack:
        engine = None
        for q in questions:
            row = retrieval[q.id]
            chosen = visual_retrieval.rank_pages(embedder.truncated(row["search_text"], dim), pages)[:count]
            ranked = visual_prompt.without_pages(row["fused"][:FUSED_LIMIT], chunk_pages, chunk_doc, set(chosen))
            views = pipeline._chunk_views(ctx.db, ranked)
            blocks = selector.select([views[i] for i in ranked if i in views], neighbor)
            text_sources = [visual_prompt.Source("text", _source_label(b), frozenset(
                (chunk_doc[c], p) for c in _chunk_ids(ctx.db, b) for p in chunk_pages.get(c, [])), text=b.text) for b in blocks]
            page_sources = [visual_prompt.Source("page", f"{docs[sha].title}, página {page}", frozenset([(sha, page)]),
                                                 images=page_images((sha, page))) for sha, page in chosen]
            sources = text_sources + page_sources
            previous = None
            if q.previous in by_id:
                previous = (by_id[q.previous].question, answers.get(q.previous, by_id[q.previous].expected_answer or ""))
            parts = visual_prompt.build_parts(q.question, sources, previous)
            digest = parts_digest(parts)
            record = done.get(q.id)
            if record is not None and record["parts_sha256"] != digest:
                raise SystemExit(f"{q.id}: {out_path} was generated with another message; delete it and run again")
            if record is None:
                if engine is None:
                    engine = stack.enter_context(_closing(vision_runtime.open_gemma(models.verify("GEMMA4_E2B"), max_num_images=6, fp32=False)))
                result = vision_runtime.generate(engine, parts, budget, max_output_tokens=config.max_output_tokens)
                relevant = frozenset((chunk_doc[c], p) for c in row["relevant"] for p in chunk_pages.get(c, []))
                j = visual_judge.judge(judge_type(q.type), result["answer"], q.key_facts, sources, relevant)
                record = {"id": q.id, "type": q.type, "parts_sha256": digest, **result,
                          "tokens": visual_prompt.tokens(parts, budget, info["chars_per_token"]),
                          "sources": [{"kind": s.kind, "label": s.label} for s in sources],
                          "judge": {"correct": j.correct, "citations_ok": j.citations_ok, "citations_total": j.citations_total}}
                with out_path.open("a", encoding="utf-8") as out:
                    out.write(json.dumps(record, ensure_ascii=False) + "\n")
                done[q.id] = record
            answers[q.id] = record["answer"]
            verdicts[q.id] = "correcta" if record["judge"]["correct"] else "incorrecta"
            if answers[q.id] != base_answers[q.id]:
                old = existing.get((q.id, variant), {})
                if old.get("respuesta_mezcla") != answers[q.id]:
                    old = {}
                judged = record["judge"]
                review_rows.append({
                    "id": q.id, "variante": variant, "tipo": q.type, "pregunta": q.question,
                    "respuesta_esperada": q.expected_answer or "", "datos_clave": " | ".join(q.key_facts),
                    "fuentes": " || ".join(f"[{n}] {s['label']}" for n, s in enumerate(record["sources"], start=1)),
                    "respuesta_v4": base_answers[q.id], "manual_v4": base_by_id[q.id]["manual"],
                    "respuesta_mezcla": answers[q.id], "regla": verdicts[q.id],
                    "regla_citas": f"{judged['citations_ok']}/{judged['citations_total']}",
                    "manual": old.get("manual", ""), "manual_citas": old.get("manual_citas", ""), "comentario": old.get("comentario", ""),
                })
    embedder.save()

    reviewed = {r["id"]: {"manual": r["manual"], "manual_citas": r["manual_citas"]} for r in review_rows if r["manual"]}
    merged = tr.merged_rows(base_rows, answers, verdicts, reviewed)
    v4 = report.manual_metrics(base_rows)
    mix = report.manual_metrics(merged) if merged is not None else None
    result = {
        "variant": variant, "decision": decision, "questions": len(answers), "answered": v4["answered"],
        "changed_answers": len(review_rows), "pending": sum(1 for r in review_rows if not r["manual"]),
        "v4_answer_correct": v4["answer_correct"], "mix_answer_correct": mix["answer_correct"] if mix else None,
        "loss_points": loss_points(v4, mix) if mix else None,
        "meets": None if mix is None else loss_points(v4, mix) <= rules["go_rule"]["text_loss_points"] + 1e-9,
    }
    kept = [r for (qid, v), r in existing.items() if v != variant]
    with REVIEW.open("w", encoding="utf-8-sig", newline="") as out:
        writer = csv.DictWriter(out, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(sorted(kept + review_rows, key=lambda r: (r["variante"], r["id"])))
    visual_retrieval.write_json(folder / "result.json", result)
    return result


def _chunk_ids(db, block):
    return [r[0] for r in db.execute("SELECT id FROM chunks WHERE document_id = ? AND ordinal BETWEEN ? AND ?",
                                     (block.document_id, block.first_ordinal, block.last_ordinal)).fetchall()]


class _closing:
    """Closes a LiteRT-LM engine on exit, as a context manager."""

    def __init__(self, engine):
        self.engine = engine

    def __enter__(self):
        return self.engine

    def __exit__(self, *exc):
        self.engine.close()
        return False

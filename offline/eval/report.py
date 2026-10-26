"""Evaluation report in Markdown (F05 FR-017, contracts/eval-cli.md E8).

Built from the manifest, the reference index, the test set and offline/build/ablations.json. The
chosen configuration and its manual judgement come from offline/config/rag_parameters.json and
offline/reports/manual-judgement.csv when they exist.
"""

import csv
import json
from collections import Counter
from pathlib import Path

from eval import builds, corpus, index, testset

OFFLINE = Path(__file__).resolve().parents[1]
REPORT = OFFLINE / "reports" / "eval-report.md"
MANUAL = OFFLINE / "reports" / "manual-judgement.csv"
THRESHOLDS = {
    "hybrid_recall@20": ("Recall@20 híbrido", 0.90),
    "blocks_recall": ("Recall@bloques (Recall@6)", 0.80),
    "answer_correct": ("Respuestas correctas", 0.80),
    "refusal_correct": ("Negativa correcta", 0.85),
    "citations_correct": ("Citas correctas", 0.90),
}


def pct(value):
    return "—" if value is None else f"{value * 100:.1f} %"


def num(value, digits=3):
    return "—" if value is None else f"{value:.{digits}f}"


def corpus_section():
    docs = corpus.load_manifest()
    lines = ["## Corpus", "", f"{len(docs)} documentos públicos en español (D-032). Fuentes y licencias en `corpus/SOURCES.md`.", "",
             "| Documento | Tipo | Formato | Licencia |", "|---|---|---|---|"]
    lines += [f"| {d.title} | {d.kind} | {d.format} | {d.license} |" for d in docs]
    kinds = Counter(d.kind for d in docs)
    formats = Counter(d.format for d in docs)
    lines += ["", f"Por tipo: {dict(kinds)}. Por formato: {dict(formats)}.", ""]
    return lines


def index_section():
    lines = ["## Índices de evaluación", "", "| Variante | Fragmentos | Objetivo / máximo (caracteres) | Caracteres/token | Por encima del máximo | Tiempo |",
             "|---|---|---|---|---|---|"]
    for variant in builds.VARIANTS:
        path = builds.index_path(variant)
        if not path.exists():
            continue
        db = index.connect(path)
        info = index.build_info(db)
        db.close()
        lines.append(
            f"| {variant} | {info.get('chunks', '—')} | {info.get('target_chars', '—')} / {info.get('max_chars', '—')} | "
            f"{info.get('chars_per_token', '—')} | {info.get('too_long', '—')} | {info.get('seconds', '—')} s |"
        )
    return lines + [""]


def testset_section():
    questions = testset.load()
    live = [q for q in questions if q.review != "discarded"]
    types = Counter(q.type for q in live)
    reviews = Counter(q.review for q in questions)
    contents = Counter(q.content_type for q in live if q.type == "answerable")
    return ["## Conjunto de prueba", "",
            f"- Preguntas: {types.get('answerable', 0)} con respuesta, {types.get('unanswerable', 0)} sin respuesta y "
            f"{types.get('followup', 0)} de seguimiento.",
            f"- Revisión: {reviews.get('ok', 0)} aceptadas, {reviews.get('fixed', 0)} corregidas, "
            f"{reviews.get('discarded', 0)} descartadas y {reviews.get('pending', 0)} sin revisar.",
            f"- Tipos de contenido (con respuesta): {dict(contents)}.", ""]


def retrieval_table(results):
    lines = ["| Variante | R@20 híbrido | R@20 vector | R@20 FTS | MRR híbrido | MRR vector | MRR FTS | nDCG@10 vector | Recall@bloques | R@20 seguimientos | Presupuesto | Tiempo |",
             "|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in results:
        lines.append(
            f"| {r['label']} | {num(r.get('hybrid_recall@20'))} | {num(r.get('vector_recall@20'))} | {num(r.get('fts_recall@20'))} | "
            f"{num(r.get('hybrid_mrr'))} | {num(r.get('vector_mrr'))} | {num(r.get('fts_mrr'))} | {num(r.get('vector_ndcg@10'))} | "
            f"{num(r.get('blocks_recall'))} | {num(r.get('followup_recall@20'))} | {r.get('budget_tokens', '—')} | {r.get('seconds', '—')} s |"
        )
    return lines


def generation_table(results):
    lines = ["| Variante | Correctas | Negativa correcta | Citas correctas | Prompt p50 / p95 (tokens) | s/respuesta |",
             "|---|---|---|---|---|---|"]
    for r in results:
        lines.append(
            f"| {r['label']} | {pct(r.get('answer_correct'))} | {pct(r.get('refusal_correct'))} | {pct(r.get('citations_correct'))} | "
            f"{r.get('prompt_tokens_median', '—')} / {r.get('prompt_tokens_p95', '—')} | {num(r.get('answer_seconds_mean'), 2)} |"
        )
    return lines


def manual_section():
    if not MANUAL.exists():
        return ["## Juicio manual", "", "Pendiente.", ""]
    rows = list(csv.DictReader(MANUAL.open(encoding="utf-8-sig")))
    agree = sum(r["regla"] == r["manual"] for r in rows if r.get("manual"))
    judged = sum(1 for r in rows if r.get("manual"))
    return ["## Juicio manual", "", f"{judged} respuestas juzgadas a mano; las reglas automáticas coinciden en "
            f"{agree} ({pct(agree / judged if judged else None)}).", ""]


def render(extra_sections=()):
    ablations = json.loads(builds.BUILD.joinpath("ablations.json").read_text()) if builds.BUILD.joinpath("ablations.json").exists() else {}
    lines = ["# Informe de evaluación offline (F05)", "",
             "Generado con `run_eval.py report`. Umbrales del informe técnico (§Evaluación) y presupuesto de prompt de la constitución IV (D-026).", ""]
    lines += corpus_section() + index_section() + testset_section()
    if ablations.get("retrieval"):
        lines += ["## Recuperación", "", *retrieval_table(ablations["retrieval"]), ""]
    if ablations.get("generation"):
        lines += ["## Respuesta de extremo a extremo", "", *generation_table(ablations["generation"]), ""]
    lines += manual_section()
    for section in extra_sections:
        lines += section
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return REPORT

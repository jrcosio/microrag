"""Evaluation report in Markdown (F05 FR-017, contracts/eval-cli.md E8).

Built from the manifest, the reference index, the test set and offline/build/ablations.json. The
chosen configuration and its manual judgement come from offline/config/rag_parameters.json and
offline/reports/manual-judgement.csv when they exist.
"""

import csv
import json
from collections import Counter
from pathlib import Path

from eval import builds, corpus, index, judge, testset

OFFLINE = Path(__file__).resolve().parents[1]
REPORT = OFFLINE / "reports" / "eval-report.md"
MANUAL = OFFLINE / "reports" / "manual-judgement.csv"
PARAMETERS = OFFLINE / "config" / "rag_parameters.json"
THRESHOLDS = {
    "hybrid_recall@20": ("Recall@20 híbrido", 0.90),
    "blocks_recall": ("Recall@bloques (tras la selección)", 0.80),
    "answer_correct": ("Respuestas correctas", 0.80),
    "refusal_correct": ("Negativa correcta", 0.85),
    "citations_correct": ("Citas correctas", 0.90),
}
PROMPT_LIMIT = 2000
LIMITATIONS = [
    "Las preguntas se redactaron leyendo fragmentos concretos, así que la recuperación sale optimista: "
    "un usuario real no conoce las palabras del documento.",
    "Los relevantes se derivan solo de la evidencia literal; otro fragmento que también responda cuenta como fallo.",
    "Las tablas no se evalúan: el corpus tiene 4 fragmentos de tabla y la única pregunta de ese tipo se "
    "reclasificó como dato.",
    "La revisión del conjunto y el juicio manual los hizo el asistente a petición del usuario (D-033), no una persona.",
    "Los tiempos son del Mac (GPU M5 Pro): la latencia del teléfono la mide F14, incluida la de E4B.",
    "El corpus son 21 documentos públicos (D-032); la calidad con documentos privados del usuario puede ser otra.",
]


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
        spec = builds.VARIANTS[variant]
        built = f"derivada de ref ({spec['derive']})" if "derive" in spec else f"{info.get('seconds', '—')} s"
        lines.append(
            f"| {variant} | {info.get('chunks', '—')} | {info.get('target_chars', '—')} / {info.get('max_chars', '—')} | "
            f"{info.get('chars_per_token', '—')} | {info.get('too_long', '—')} | {built} |"
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
    lines = ["| Variante | Correctas | Negativa correcta | Citas correctas | Presupuesto | Prompt p50 / p95 (tokens) | s/respuesta |",
             "|---|---|---|---|---|---|---|"]
    for r in results:
        lines.append(
            f"| {r['label']} | {pct(r.get('answer_correct'))} | {pct(r.get('refusal_correct'))} | {pct(r.get('citations_correct'))} | "
            f"{r['config'].get('budget_tokens') or '—'} | {r.get('prompt_tokens_median', '—')} / {r.get('prompt_tokens_p95', '—')} | "
            f"{num(r.get('answer_seconds_mean'), 2)} |"
        )
    return lines


def manual_rows():
    return list(csv.DictReader(MANUAL.open(encoding="utf-8-sig"))) if MANUAL.exists() else []


def manual_metrics(rows):
    """Metrics of the manual judgement: a partial answer counts as not correct."""
    answered = [r for r in rows if r["tipo"] in ("answerable", "followup")]
    unanswerable = [r for r in rows if r["tipo"] == "unanswerable"]
    cited = [tuple(map(int, r["manual_citas"].split("/"))) for r in rows if r.get("manual_citas")]
    agree = sum(r["regla"] == ("correcta" if r["manual"] == "correcta" else "incorrecta") for r in rows)
    return {
        "answer_correct": sum(r["manual"] == "correcta" for r in answered) / len(answered),
        "refusal_correct": sum(r["manual"] == "correcta" for r in unanswerable) / len(unanswerable),
        "citations_correct": sum(ok for ok, _ in cited) / sum(total for _, total in cited),
        "answered": len(answered), "unanswerable": len(unanswerable), "citations": sum(t for _, t in cited),
        "outcomes": {(t, m): sum(r["tipo"] == t and r["manual"] == m for r in rows)
                     for t in ("answerable", "followup", "unanswerable") for m in ("correcta", "parcial", "incorrecta")},
        "undue_refusals": sum(judge.is_refusal(r["respuesta_modelo"]) for r in answered),
        "agreement": agree / len(rows),
    }


def manual_section():
    rows = manual_rows()
    if not rows:
        return ["## Juicio manual", "", "Pendiente.", ""]
    m = manual_metrics(rows)
    o = m["outcomes"]
    lines = ["## Juicio manual de la configuración elegida", "",
             f"{len(rows)} respuestas juzgadas una a una (`reports/manual-judgement.csv`). Una respuesta parcial cuenta como no correcta.", "",
             "| Tipo | Correctas | Parciales | Incorrectas |", "|---|---|---|---|"]
    names = {"answerable": "Con respuesta", "followup": "Seguimiento", "unanswerable": "Sin respuesta"}
    lines += [f"| {names[t]} | {o[(t, 'correcta')]} | {o[(t, 'parcial')]} | {o[(t, 'incorrecta')]} |" for t in names]
    lines += ["",
              f"- Respuestas correctas: {pct(m['answer_correct'])} de {m['answered']}; {m['undue_refusals']} son negativas indebidas.",
              f"- Negativa correcta: {pct(m['refusal_correct'])} de {m['unanswerable']}.",
              f"- Citas correctas: {pct(m['citations_correct'])} de {m['citations']}.",
              f"- La regla automática coincide con el juicio manual en {pct(m['agreement'])} de las respuestas.", ""]
    return lines


def parameters():
    return json.loads(PARAMETERS.read_text(encoding="utf-8")) if PARAMETERS.exists() else None


def chosen(entries, params):
    """The ablation entry run with the decided context parameters."""
    for entry in entries:
        c = entry["config"]
        if (c["build"], c["mode"], c["follow_up_rule"], c["max_blocks"], c["neighbors"]) == (
                "ref", "hybrid", params["follow_up_rule"], params["max_blocks"], params["expand_top"] > 0):
            return entry
    return None


def thresholds_section(ablations, params):
    retrieval = chosen(ablations.get("retrieval", []), params)
    generation = chosen(ablations.get("generation", []), params)
    rows = manual_rows()
    if not (retrieval and generation and rows):
        return []
    manual = manual_metrics(rows)

    def row(name, value, threshold, ok, note=""):
        return f"| {name} | {value} | {threshold} | {'✓' if ok else '✗'} | {note} |"

    lines = ["## Umbrales frente a la configuración elegida", "",
             f"Recuperación de «{retrieval['label']}» y respuestas del juicio manual de «{generation['label']}».", "",
             "| Métrica | Valor | Umbral | Cumple | Nota |", "|---|---|---|---|---|"]
    for key in ("hybrid_recall@20", "blocks_recall"):
        name, threshold = THRESHOLDS[key]
        lines.append(row(name, num(retrieval[key]), f"≥ {threshold:.2f}", retrieval[key] >= threshold))
    best_single = max(retrieval["vector_mrr"], retrieval["fts_mrr"])
    lines.append(row("MRR híbrido", num(retrieval["hybrid_mrr"]),
                     f"≥ vector ({num(retrieval['vector_mrr'])}) y ≥ FTS ({num(retrieval['fts_mrr'])})",
                     retrieval["hybrid_mrr"] >= best_single))
    for key, note in (("answer_correct", "aceptado por el usuario (D-035)"), ("refusal_correct", ""), ("citations_correct", "")):
        name, threshold = THRESHOLDS[key]
        lines.append(row(f"{name} (manual)", pct(manual[key]), f"≥ {threshold * 100:.0f} %", manual[key] >= threshold,
                         note if manual[key] < threshold else ""))
    p95 = generation["prompt_tokens_p95"]
    lines.append(row("Prompt p95 (tokens)", str(p95), f"≤ {PROMPT_LIMIT} (constitución IV)", p95 <= PROMPT_LIMIT))
    dims = {r["label"]: r for r in ablations.get("retrieval", [])}
    full, ref = dims.get("768 dimensiones"), ablations["retrieval"][0]
    if full:
        loss = 1 - ref["vector_ndcg@10"] / full["vector_ndcg@10"]
        lines.append(row(f"nDCG@10 vectorial, {params['dims']} frente a 768", f"−{loss * 100:.1f} %", "≤ 1 %", loss <= 0.01,
                         "256 por decisión del usuario; el híbrido no pierde (D-034)"))
    return lines + [""]


def comparisons_section(ablations, params):
    comparisons = ablations.get("comparisons")
    if not comparisons:
        return []
    base = chosen(ablations.get("generation", []), params)
    return ["## Palancas probadas para el umbral de respuestas", "",
            "Misma recuperación y presupuesto que la configuración elegida, cambiando una sola cosa (regla automática).", "",
            *generation_table(([{**base, "label": f"Elegida: {base['label']}"}] if base else []) + comparisons), ""]


def failures_section(ablations, params):
    """Not-correct answers of the manual judgement split by whether a relevant chunk reached the context."""
    from eval import metrics, pipeline
    from eval.runs import run_dir
    from eval.selector import ContextBlock

    retrieval = chosen(ablations.get("retrieval", []), params)
    rows = manual_rows()
    path = run_dir(retrieval["fingerprint"]) / "retrieval.jsonl" if retrieval else None
    if not (rows and path and path.exists()):
        return []
    runs = {r["id"]: r for r in map(json.loads, path.read_text(encoding="utf-8").splitlines())}
    ctx = pipeline.load_context("ref")
    counts = Counter()
    for row in rows:
        run = runs.get(row["id"])
        if row["tipo"] == "unanswerable" or not run:
            continue
        hit = bool(run["relevant"]) and metrics.blocks_hit([ContextBlock(**b) for b in run["blocks"]],
                                                           pipeline._pairs(ctx, run["relevant"]))  # noqa: SLF001
        refused = judge.is_refusal(row["respuesta_modelo"])
        counts[(row["manual"], hit, refused)] += 1
    ctx.db.close()
    lines = ["## Análisis de fallos", "",
             "Respuestas no correctas del juicio manual (con respuesta y seguimiento), según si algún fragmento relevante llegó al contexto.", "",
             "| Juicio | Relevante en el contexto | Responde | Se niega |", "|---|---|---|---|"]
    for verdict in ("parcial", "incorrecta"):
        for hit in (True, False):
            lines.append(f"| {verdict} | {'sí' if hit else 'no'} | {counts[(verdict, hit, False)]} | {counts[(verdict, hit, True)]} |")
    missed = sum(n for (verdict, hit, _), n in counts.items() if verdict != "correcta" and not hit)
    failed = sum(n for (verdict, _, _), n in counts.items() if verdict != "correcta")
    lines += ["", f"{missed} de {failed} fallos no tenían el fragmento relevante en el contexto, aunque el Recall@20 híbrido es "
              f"{num(retrieval['hybrid_recall@20'])}: el relevante está entre los candidatos, pero no entre los bloques que caben en el "
              f"presupuesto (de media {retrieval['blocks_mean']:.1f} bloques). El resto falla con el relevante delante: negativas "
              "indebidas y respuestas incompletas.", ""]
    return lines


def parameters_section(params):
    lines = ["## Configuración elegida", "",
             f"Fuente única: `config/rag_parameters.json`, igual a `RagParameters.kt` (decisiones {', '.join(params['decisions'])}).", "",
             "| Parámetro | Valor |", "|---|---|"]
    lines += [f"| `{key}` | {json.dumps(value, ensure_ascii=False)} |" for key, value in params.items() if key not in ("decided_in", "decisions")]
    return lines + [""]


ORDER_REVIEW = OFFLINE / "build" / "order_review.json"
ADOPTION_POINTS = 2.0  # clarify of F10: Recall@bloques must rise this many points over the decided configuration


def order_review_section():
    """F10 (D-035): order within the budget, from run_eval.py order-review."""
    if not ORDER_REVIEW.exists():
        return []
    results = json.loads(ORDER_REVIEW.read_text(encoding="utf-8"))
    base, variants = results[0], results[1:]
    budget = base["config"]["budget_tokens"]
    lines = ["## Revisión del orden (D-035, F10)", "",
             f"Variantes del orden dentro del presupuesto sobre la configuración elegida ({base['label']}), con "
             f"{base['questions']} preguntas puntuables:", "",
             "- **Trigramas como tercera lista de RRF:** una tabla FTS5 `trigram` temporal sobre el contenido, con los "
             "términos de 3 o más caracteres, sin tildes ni palabras vacías.",
             "- **Reordenación:** los primeros de la fusión se ordenan por α · similitud coseno + (1 − α) · BM25, "
             "normalizados min-max en cada lista (0 si el fragmento no está en ella); el resto conserva su orden.", "",
             "| Variante | Recall@bloques | Diferencia | R@20 híbrido | MRR híbrido | Tokens de contexto (media) |",
             "|---|---|---|---|---|---|"]
    for r in results:
        diff = "—" if r is base else f"{(r['blocks_recall'] - base['blocks_recall']) * 100:+.1f} puntos"
        lines.append(f"| {r['label']} | {num(r['blocks_recall'])} | {diff} | {num(r['hybrid_recall@20'])} | "
                     f"{num(r['hybrid_mrr'])} | {r['context_tokens_mean']:.0f} |")
    best = max(variants, key=lambda r: r["blocks_recall"])
    gain = (best["blocks_recall"] - base["blocks_recall"]) * 100
    lines += ["", f"Regla de adopción (clarify de F10): una variante solo entra en la app si sube el Recall@bloques "
              f"{ADOPTION_POINTS:.0f} puntos o más, sin pasar el presupuesto de {budget} tokens que aplica el selector.", ""]
    if gain >= ADOPTION_POINTS:
        lines.append(f"Decisión: «{best['label']}» sube {gain:.1f} puntos y cumple la regla; se consulta antes de llevarla a la app.")
    else:
        lines.append(f"Decisión (D-051): ninguna variante cumple la regla. La mejor, «{best['label']}», sube {gain:.1f} "
                     "puntos. La app mantiene D-034, y esa variante queda anotada como palanca para F14.")
    return lines + [""]


def limitations_section():
    return ["## Limitaciones", "", *[f"- {item}" for item in LIMITATIONS], ""]


def render(extra_sections=()):
    ablations = json.loads(builds.BUILD.joinpath("ablations.json").read_text()) if builds.BUILD.joinpath("ablations.json").exists() else {}
    lines = ["# Informe de evaluación offline (F05)", "",
             "Generado con `run_eval.py report`. Umbrales del informe técnico (§Evaluación) y presupuesto de prompt de la constitución IV (D-026).", ""]
    lines += corpus_section() + index_section() + testset_section()
    if ablations.get("retrieval"):
        lines += ["## Recuperación", "",
                  "La referencia es la configuración de partida del informe técnico: 6 bloques, con vecino para los 2 mejores. "
                  "Cada ablación cambia una sola cosa. El presupuesto de estas filas se deriva de la plantilla; la generación "
                  "usa el presupuesto calibrado con el tokenizador real para que el p95 del prompt quepa en 2.000 tokens.", "",
                  *retrieval_table(ablations["retrieval"]), ""]
    if ablations.get("generation"):
        lines += ["## Respuesta de extremo a extremo", "",
                  "Gemma 4 E2B en la GPU del Mac, juzgado con la regla automática calibrada (el juicio manual va más abajo).", "",
                  *generation_table(ablations["generation"]), ""]
    params = parameters()
    if params:
        lines += parameters_section(params) + thresholds_section(ablations, params)
    lines += manual_section()
    if params:
        lines += failures_section(ablations, params) + comparisons_section(ablations, params)
    lines += order_review_section()
    lines += limitations_section()
    for section in extra_sections:
        lines += section
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return REPORT

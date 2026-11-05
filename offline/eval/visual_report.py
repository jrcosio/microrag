"""The evaluation report of F18 (contracts/visual-harness.md §5): `reports/visual-eval-report.md`, generated.

Sections 1–3 and 6 come from the desktop; 4 (parity and reading on the phone) and 5 (the rule) are filled when
their files exist: `build/visual/parity.json` and `build/visual/decision-final.json`.
"""

import json
from collections import Counter
from pathlib import Path

from eval import corpus, visual_index, visual_manual, visual_testset
from eval.report import num, pct

OFFLINE = Path(__file__).resolve().parents[1]
REPORT = OFFLINE / "reports" / "visual-eval-report.md"
TYPE_NAMES = {"tabla": "Tabla", "grafico": "Gráfico", "escaneado": "Escaneado", "imagen": "Imagen", "mixta": "Mixta",
              "control": "Control", "sin_respuesta": "Sin respuesta", "visual": "Visuales", "all": "Todas",
              "answerable": "Respondibles"}
ANSWER_TYPES = ("tabla", "grafico", "escaneado", "imagen", "mixta", "control")


def _read(path):
    return json.loads(Path(path).read_text(encoding="utf-8")) if Path(path).exists() else None


def corpus_section(docs, page_counts, questions):
    kinds = Counter(doc.kind for doc in docs)
    formats = Counter(doc.format for doc in docs)
    accepted = [q for q in questions if q.review == "ok"]
    counts = visual_testset.counts(questions)
    lines = ["## 1. Corpus y conjunto de prueba", "",
             f"{len(docs)} documentos públicos con {sum(page_counts.values())} páginas, en `corpus_visual/` (fuentes y licencias en "
             "`corpus_visual/SOURCES.md`).", "",
             "| Tipo | Documentos | Páginas |", "|---|---|---|"]
    for kind, n in sorted(kinds.items()):
        lines.append(f"| {kind} | {n} | {sum(page_counts[d.id] for d in docs if d.kind == kind)} |")
    lines += ["", "Formatos: " + ", ".join(f"{fmt} {n}" for fmt, n in sorted(formats.items())) + ".", "",
              f"{counts['total']} preguntas redactadas por agentes separados y revisadas por el asistente con la página a la vista "
              f"(D-033): {counts['accepted']} aceptadas y {counts['total'] - counts['accepted']} descartadas, "
              f"{pct(counts['visual_share'])} visuales.", "",
              "| Tipo | Aceptadas |", "|---|---|"]
    lines += [f"| {TYPE_NAMES[t]} | {counts['by_type'][t]} |" for t in visual_testset.TYPES]
    lines += ["", "Una foto y su página original son relevantes la una para la otra, igual que el folleto de la BNE y la Gaceta de "
              f"1931. De las aceptadas, {sum(len({sha for sha, _ in q.pages}) > 1 for q in accepted)} tienen páginas relevantes "
              "en más de un documento.", ""]
    return lines


def retrieval_section(ablations, decision):
    if ablations is None:
        return ["## 2. Recuperación por página", "", "Pendiente: `run_eval.py visual-retrieve`.", ""]
    lines = ["## 2. Recuperación por página", "",
             f"{ablations['questions']} preguntas respondibles. Recall@5 por página: la parte de las páginas relevantes que "
             "salen entre las 5 primeras.", "",
             "| Modo | px | Tokens | Dim. | Recall@5 visual | MRR visual | Recall@5 de todas | nDCG@10 de todas |", "|---|---|---|---|---|---|---|---|"]
    for r in ablations["results"]:
        v, a = r["summary"].get("visual", {}), r["summary"]["all"]
        lines.append(f"| {r['mode']} | {r['px'] or '—'} | {r['tokens'] or '—'} | {r['dim'] or '—'} | {num(v.get('recall@5'))} | "
                     f"{num(v.get('mrr'))} | {num(a['recall@5'])} | {num(a['ndcg@10'])} |")
    if decision:
        lines += ["", f"Elegido con la regla congelada (R12, D-076): **{decision['px']} px, {decision['tokens']} tokens por imagen y "
                  f"{decision['dim']} dimensiones**, con el Recall@5 visual de «Visual»."]
        rows = [r for r in ablations["results"] if r["mode"] == "texto" or
                (r["px"], r["tokens"], r["dim"]) == (decision["px"], decision["tokens"], decision["dim"])]
        lines += ["", "Por tipo de pregunta, con lo elegido (Recall@5):", "",
                  "| Modo | " + " | ".join(TYPE_NAMES[t] for t in ANSWER_TYPES) + " |", "|---|" + "---|" * len(ANSWER_TYPES)]
        for r in rows:
            lines.append(f"| {r['mode']} | " + " | ".join(num(r["summary"].get(t, {}).get("recall@5")) for t in ANSWER_TYPES) + " |")
        lines += ["", "La RRF de las tres listas («ambos») deja los escaneados y las imágenes donde los deja «Texto»: una página sin texto "
                  "solo está en la lista de páginas, y la RRF premia las que aparecen en varias. Por eso las páginas de la respuesta "
                  "salen de «Visual» (D-076)."]
    return lines + [""]


def answers_section(metrics, manual_rows):
    lines = ["## 3. Respuestas", ""]
    if not metrics:
        return lines + ["Pendiente: `run_eval.py visual-generate`.", ""]
    lines += ["Juez automático de F05 con citas a página (R11), sobre las preguntas aceptadas. Tokens: medianas del texto estimado y "
              "de los visuales; el primer token es la mediana en el escritorio. Gemma corre en la GPU del Mac con activaciones FP32: "
              "con las fp16 por defecto deformaba cifras («20000» por 2020), y esa tanda queda aparte en `build/visual/runs-fp16`.", "",
              "| Variante | Completa | Visuales | " + " | ".join(TYPE_NAMES[t] for t in ANSWER_TYPES) +
              " | Negativa correcta | Negativas indebidas | Citas | Tokens de texto | Tokens visuales | Primer token |",
              "|---|---|---|" + "---|" * len(ANSWER_TYPES) + "---|---|---|---|---|---|"]
    for variant, m in metrics.items():
        s = m["summary"]
        tokens = m.get("tokens") or {}
        lines.append(
            f"| {variant} | {'sí' if m['complete'] else 'no'} | {pct(s.get('visual', {}).get('correct'))} | "
            + " | ".join(pct(s.get(t, {}).get("correct")) for t in ANSWER_TYPES)
            + f" | {pct(s.get('sin_respuesta', {}).get('refusal_correct'))} | {pct(s.get('answerable', {}).get('refusals'))} | "
            f"{pct(s.get('answerable', {}).get('citations_correct'))} | {tokens.get('text', '—')} | {tokens.get('visual', '—')} | "
            f"{num(m.get('ttft_p50_s'), 2)} s |")
    agreement = visual_manual.agreement(manual_rows) if manual_rows else {}
    lines += ["", "### Comprobación del juez", ""]
    if not agreement:
        lines.append("Pendiente: `run_eval.py visual-manual` y la revisión de `reports/visual-manual-judgement.csv`.")
    else:
        lines += ["| Variante | Juzgadas | La regla coincide |", "|---|---|---|"]
        lines += [f"| {v} | {a['judged']} | {pct(a['share'])} |" for v, a in agreement.items()]
    return lines + [""]


def phone_section(parity):
    lines = ["## 4. Paridad con el teléfono y prueba de lectura", ""]
    if parity is None:
        return lines + ["Pendiente de la fase del teléfono (T056).", ""]
    vectors = parity["vectors"]
    same, rendered, reading = vectors["same_bytes"], vectors["rendered_pages"], parity.get("reading")
    lines += [f"- Mismos bytes: {same['n']} vectores, coseno mínimo {num(same['min'], 4)} y medio {num(same['mean'], 4)} "
              f"(umbral {same['threshold']}); por debajo: {', '.join(same['below']) or 'ninguno'}.",
              f"- Páginas rasterizadas en cada lado: {rendered['n']} vectores, coseno mínimo {num(rendered['min'], 4)} y medio "
              f"{num(rendered['mean'], 4)}."]
    if reading:
        lines.append(f"- Lectura: {reading['phone_correct']} de {reading['n']} correctas en el teléfono y {reading['desktop_correct']} en el "
                     f"escritorio; mismo veredicto en {reading['same_verdict']}.")
    return lines + [""]


def decision_section(final, candidates=None, metrics=None):
    lines = ["## 5. Regla de sí o no y parámetros", ""]
    if final is None:
        return lines + ["Pendiente: `run_eval.py visual-decide <informe.json>` tras el teléfono y la revisión decisiva.", ""]
    retrieval = final.get("retrieval") or {}
    lines += [f"Parámetros de recuperación elegidos antes de las respuestas: {retrieval.get('px')} px, {retrieval.get('tokens')} tokens por "
              f"imagen y {retrieval.get('dim')} dimensiones (R12, D-076).", ""]
    if candidates:
        lines += ["Candidatas de la etapa A (primer token compuesto ≤ 8.800 ms): " + ", ".join(f"`{c}`" for c in candidates["candidates"]) + ".", ""]
    names = {"visual_gain": "+15 puntos en las visuales", "text_loss": "−2 puntos como mucho en F05",
             "first_token": "p95 compuesto ≤ 8 s con al menos 1 página", "embedder": "440M: golden ≥ 0,99 e identidad ≥ 0,9999"}
    lines += ["| Condición | Cifra | Cumple |", "|---|---|---|"]
    for key, label in names.items():
        c = final["conditions"][key]
        if key == "embedder":
            value = f"golden {num(c['golden_min'], 5)}, identidad {num(c['identity_min'], 5)}"
        elif key == "first_token":
            value = "ninguna configuración cabe" if c["e2e_ms"] is None else f"{num(c['e2e_ms'], 0)} ms"
        else:
            value = "sin revisión decisiva" if c["points"] is None else f"{c['points']} puntos"
        lines.append(f"| {label} | {value} | {'sí' if c['ok'] else 'no'} |")
    verdict = "**sí**" if final["go"] else "**no**"
    lines += ["", f"Resultado de la regla: {verdict}" + ("; decide el usuario (D-073)." if final["needs_user"] else "."), ""]
    if final.get("decisive") is None and candidates and metrics:
        def visual(key):
            return pct(metrics[key]["summary"]["visual"]["correct"]) if key in metrics else "—"
        lines += ["Sin una configuración que cumpla el primer token no hay configuración elegida que revisar a mano. Con el juez "
                  "automático, las candidatas dan " + ", ".join(f"{visual(c)} (`{c}`)" for c in candidates["candidates"]) +
                  f" en las visuales, frente a {visual('texto')} de «Texto»: ninguna se acerca a los +15 puntos.", ""]
    return lines


def durations_section(metrics):
    lines = ["## 6. Duración de las tandas", "", "| Variante | Respuestas | Segundos en total | Por respuesta |", "|---|---|---|---|"]
    for variant, m in metrics.items():
        seconds = m.get("seconds_total")
        lines.append(f"| {variant} | {m['answers']} | {num(seconds, 0)} | {num(seconds / m['answers'], 2) if seconds and m['answers'] else '—'} |")
    return lines + [""]


def _answer_metrics(variants):
    """metrics.json of each answer run, with the medians of its tokens and the total of its seconds."""
    runs = visual_index.VISUAL_DIR / "runs"
    out = {}
    for variant in variants:
        folder = runs / f"answers-{variant}"
        m = _read(folder / "metrics.json")
        if m is None:
            continue
        rows = [json.loads(line) for line in (folder / "answers.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
        text = sorted(r["tokens"]["text"] for r in rows)
        visual = sorted(r["tokens"]["visual"] for r in rows)
        m["tokens"] = {"text": text[len(text) // 2], "visual": visual[len(visual) // 2]} if rows else {}
        m["seconds_total"] = sum(r["seconds"] for r in rows)
        out[variant] = m
    return out


def build(variants):
    source = corpus.CORPORA["visual"]
    docs = corpus.load_manifest(source.manifest)
    page_counts = {d.id: len(visual_index.page_numbers(d, source.docs)) for d in docs}
    metrics = _answer_metrics(variants)
    lines = ["# Informe de evaluación del RAG visual (F18)", "",
             "Generado con `run_eval.py visual-report`. Regla congelada en `config/visual_rules.json` (D-073, D-075, D-076).", ""]
    lines += corpus_section(docs, page_counts, visual_testset.load())
    lines += retrieval_section(_read(visual_index.VISUAL_DIR / "retrieval-ablations.json"),
                               _read(visual_index.VISUAL_DIR / "decision-retrieval.json"))
    lines += answers_section(metrics, visual_manual.load())
    lines += phone_section(_read(visual_index.VISUAL_DIR / "parity.json"))
    lines += decision_section(_read(visual_index.VISUAL_DIR / "decision-final.json"),
                              _read(visual_index.VISUAL_DIR / "decision-candidates.json"), metrics)
    lines += durations_section(metrics)
    return "\n".join(lines).rstrip("\n") + "\n"

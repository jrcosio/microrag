"""F14 reviews against v4 (specs/014-ondevice-performance, D-088, D-090).

`runtime` regenerates every v4 answer with the installed litert-lm-api, so the levers compare against v4 on the
runtime the app adopts (U8). Each lever then changes the prompt or the context of v4: an answer whose prompt is
the base one keeps the base answer and verdict, the others are generated on the Mac GPU in the order of
pipeline.generate() (follow-ups see this run's previous answers), and the answers that change are judged by hand in
reports/f14-review.csv (D-033). The verdicts already there are kept, so the review can be done in several sittings.
"""

import csv
import dataclasses
import hashlib
import importlib.metadata
import json
from contextlib import ExitStack

from eval import builds, judge, models, report
from eval import template_review as tr
from eval.generate import Item, open_engine
from eval.generate import run as run_generation
from eval.prompt import build_prompt
from eval.selector import ContextBlock

REVIEW_DIR = builds.BUILD / "f14"
REVIEW_CSV = report.MANUAL.parent / "f14-review.csv"
RULES = builds.OFFLINE / "config" / "f14_rules.json"
ORIGINAL_RUNTIME = "0.18.1"
FIELDS = ["id", "variante", "tipo", "pregunta", "respuesta_esperada", "datos_clave", "fuentes", "cambio",
          "respuesta_base", "manual_base", "respuesta_variante", "regla", "regla_citas", "manual", "manual_citas",
          "comentario"]
FIGURES = ("answer_correct", "refusal_correct", "citations_correct", "undue_refusals")


def installed_runtime():
    return importlib.metadata.version("litert-lm-api")


def figures(rows):
    manual = report.manual_metrics(rows)
    return {key: manual[key] for key in FIGURES}


def passes(base, variant, rule):
    """The quality half of the first-token lever rule (D-088, D-090): correct answers at most
    [rule]["max_correct_drop_points"] below the base, refusal and citations over their minimums."""
    eps = 1e-9
    return (variant["answer_correct"] >= base["answer_correct"] - rule["max_correct_drop_points"] / 100 - eps
            and variant["refusal_correct"] >= rule["min_refusal"] - eps
            and variant["citations_correct"] >= rule["min_citations"] - eps)


def existing_rows(path=None):
    path = path or REVIEW_CSV
    if not path.exists():
        return {}
    return {(r["id"], r["variante"]): r for r in csv.DictReader(path.open(encoding="utf-8-sig"))}


def reviewed_of(rows, variant):
    return {qid: {"manual": r["manual"], "manual_citas": r["manual_citas"]}
            for (qid, name), r in rows.items() if name == variant and r["manual"]}


def runtime_name(runtime=None):
    return f"runtime-{runtime or installed_runtime()}"


def base(pipeline):
    """v4 on the installed runtime: the F11 run on 0.18.1, or the complete `runtime` review on any other."""
    config, answers, rules, rows = pipeline._active_template_base()
    runtime = installed_runtime()
    if runtime == ORIGINAL_RUNTIME:
        return config, answers, rules, rows
    name = runtime_name(runtime)
    path = REVIEW_DIR / name / "answers.jsonl"
    if not path.exists():
        raise SystemExit(f"run `f14-review variant=runtime` first: v4 has no answers on {runtime}")
    records = {json.loads(l)["id"]: json.loads(l) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()}
    if set(records) != set(answers):
        raise SystemExit(f"{name}: {len(answers) - len(records)} answers are missing; run it again")
    new_answers = {qid: r["answer"] for qid, r in records.items()}
    new_rules = {qid: "correcta" if r["judge"]["correct"] else "incorrecta" for qid, r in records.items()}
    merged = tr.merged_rows(rows, new_answers, new_rules, reviewed_of(existing_rows(), name))
    if merged is None:
        raise SystemExit(f"{name}: the runtime review has unjudged answers")
    return config, new_answers, new_rules, merged


def review(pipeline, variant, prompt_of, regenerate_all=False):
    """Generates [variant] against [base] and writes its review rows and result.

    [prompt_of](q, row, blocks, previous) returns (prompt, kept_blocks, change), where [change] says what the
    variant did to this prompt (shown to the judge). With [regenerate_all] every answer is generated again,
    even when its prompt is the base one (the runtime review).
    """
    if regenerate_all:
        config, base_answers, base_rules, base_rows = pipeline._active_template_base()
    else:
        config, base_answers, base_rules, base_rows = base(pipeline)
    runtime = installed_runtime()
    ctx = pipeline.load_context(config.build)
    retrieval = pipeline._retrieval_rows(ctx, config)
    by_id = {q.id: q for q in pipeline.live_questions()}
    base_by_id = {r["id"]: r for r in base_rows}
    questions = sorted(pipeline.live_questions(), key=lambda q: (q.type == "followup", q.id))
    folder = REVIEW_DIR / variant
    folder.mkdir(parents=True, exist_ok=True)
    out_path = folder / "answers.jsonl"
    done = {}
    if out_path.exists():
        done = {json.loads(l)["id"]: json.loads(l) for l in out_path.read_text(encoding="utf-8").splitlines() if l.strip()}
    existing = existing_rows()

    answers, rules, review_rows, changed_prompts = {}, {}, [], 0
    with ExitStack() as stack:
        engine = None
        for q in questions:
            row = retrieval[q.id]
            blocks = [ContextBlock(**b) for b in row["blocks"]]
            previous = base_previous = None
            if q.previous in by_id:
                fallback = by_id[q.previous].expected_answer or ""
                previous = (by_id[q.previous].question, answers.get(q.previous, fallback))
                base_previous = (by_id[q.previous].question, base_answers.get(q.previous, fallback))
            prompt, kept, change = prompt_of(q, row, blocks, previous)
            same_prompt = prompt == build_prompt(q.question, blocks, base_previous, config.prompt_template)
            if same_prompt and not regenerate_all:
                answers[q.id], rules[q.id] = base_answers[q.id], base_rules[q.id]
                continue
            if not same_prompt:
                changed_prompts += 1
            digest = hashlib.sha256(prompt.encode("utf-8")).hexdigest()
            record = done.get(q.id)
            if record is not None and (record["prompt_sha256"] != digest or record["runtime"] != runtime):
                raise SystemExit(f"{q.id}: {out_path} was generated with another prompt or runtime; delete it and run again")
            if record is None:
                if engine is None:
                    engine = stack.enter_context(
                        open_engine(models.verify(models.LLMS[config.llm]), "gpu", builds.BUILD / "litert-cache"))

                def on_answer(item, answer, q=q, kept=kept, row=row, digest=digest):
                    verdict = judge.judge_answer(q.type, answer, q.key_facts, kept, pipeline._pairs(ctx, row["relevant"]))
                    return {"type": q.type, "prompt_sha256": digest, "runtime": runtime,
                            "judge": dataclasses.asdict(verdict)}

                run_generation(engine, [Item(q.id, prompt)], out_path, config.max_output_tokens, on_answer)
                record = json.loads(out_path.read_text(encoding="utf-8").splitlines()[-1])
                done[q.id] = record
            answers[q.id] = record["answer"]
            rules[q.id] = "correcta" if record["judge"]["correct"] else "incorrecta"
            if answers[q.id] != base_answers[q.id]:
                old = existing.get((q.id, variant), {})
                judged = record["judge"]
                review_rows.append({
                    "id": q.id, "variante": variant, "tipo": q.type, "pregunta": q.question,
                    "respuesta_esperada": q.expected_answer or "", "datos_clave": " | ".join(q.key_facts),
                    "fuentes": pipeline._describe(kept), "cambio": change,
                    "respuesta_base": base_answers[q.id], "manual_base": base_by_id[q.id]["manual"],
                    "respuesta_variante": answers[q.id], "regla": rules[q.id],
                    "regla_citas": f"{judged['citations_ok']}/{judged['citations_total']}",
                    "manual": old.get("manual", ""), "manual_citas": old.get("manual_citas", ""),
                    "comentario": old.get("comentario", ""),
                })

    reviewed = {r["id"]: {"manual": r["manual"], "manual_citas": r["manual_citas"]} for r in review_rows if r["manual"]}
    merged = tr.merged_rows(base_rows, answers, rules, reviewed)
    # The automatic rule of F05 on the answered questions, as a screen: a lever far below v4 is not judged by hand.
    answered = [r["id"] for r in base_rows if r["tipo"] in ("answerable", "followup")]
    auto = {"base": sum(base_rules[qid] == "correcta" for qid in answered) / len(answered),
            "variant": sum(rules[qid] == "correcta" for qid in answered) / len(answered)}
    rule = json.loads(RULES.read_text(encoding="utf-8"))["first_token_lever"] if RULES.exists() else None
    base_figures = figures(base_rows)
    variant_figures = figures(merged) if merged is not None else None
    result = {
        "variant": variant, "runtime": runtime, "questions": len(answers), "changed_prompts": changed_prompts,
        "changed_answers": len(review_rows), "pending": sum(1 for r in review_rows if not r["manual"]),
        "base": base_figures, "manual": variant_figures, "auto_correct": auto,
        "passes": None if variant_figures is None or rule is None else passes(base_figures, variant_figures, rule),
    }
    kept_rows = [r for (qid, name), r in existing.items() if name != variant]
    REVIEW_CSV.parent.mkdir(parents=True, exist_ok=True)
    with REVIEW_CSV.open("w", encoding="utf-8-sig", newline="") as out:
        writer = csv.DictWriter(out, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(sorted(kept_rows + review_rows, key=lambda r: (r["variante"], r["id"])))
    (folder / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=1))
    return result


def runtime_review(pipeline):
    """v4 regenerated with the installed runtime; the same prompts, every answer generated again (U8)."""
    runtime = installed_runtime()
    if runtime == ORIGINAL_RUNTIME:
        raise SystemExit(f"the installed litert-lm-api is {ORIGINAL_RUNTIME}: there is nothing to compare")

    def prompt_of(q, row, blocks, previous):
        return build_prompt(q.question, blocks, previous, "v4"), blocks, f"runtime {ORIGINAL_RUNTIME} → {runtime}"

    return review(pipeline, runtime_name(runtime), prompt_of, regenerate_all=True)


TOKEN_REVIEW = REVIEW_DIR / "token-review.json"
ANSWER_GOLDEN = builds.OFFLINE / "golden" / "answer_golden.jsonl"
NUMERIC_SHARE = 0.10


def token_review(pipeline):
    """F14 (research R9): the estimate against Gemma's tokenizer over the app's prompts and the corpus chunks.

    Fits (a, b, c), makes it safe on the texts full of figures (p95 of real/estimated at most 1.0, the rule of
    f14_rules.json), and writes the old and new errors per kind of text. The prompts it changes go to f14-review
    (variant token-estimate) with the quality rule.
    """
    import sqlite3

    from eval import token_estimate as te

    from types import SimpleNamespace

    golden = [json.loads(line) for line in ANSWER_GOLDEN.read_text(encoding="utf-8").splitlines() if line.strip()]
    # The golden keeps the whole text of a few prompts only; the others are rebuilt from their blocks, as the app does.
    prompts = [c.get("prompt") or build_prompt(c["question"], [SimpleNamespace(**b) for b in c["blocks"]], tuple(c["previous"]) if c.get("previous") else None,
                                               c["template"]) for c in golden]
    db = sqlite3.connect(str(builds.index_path("ref")))
    try:
        chunks = [row[0] for row in db.execute("SELECT content FROM chunks ORDER BY id")]
    finally:
        db.close()
    with open_engine(models.verify(models.LLMS["E2B"]), "cpu", builds.BUILD / "litert-cache") as engine:
        samples = {
            "prompts": [(t, len(engine.tokenize(t))) for t in prompts],
            "chunks": [(t, len(engine.tokenize(t))) for t in chunks],
        }
    samples["numeric"] = [s for s in samples["prompts"] + samples["chunks"] if te.digit_share(s[0]) >= NUMERIC_SHARE]
    params = json.loads(report.PARAMETERS.read_text(encoding="utf-8"))
    old = (params["chars_per_token"],) * 3
    fitted = te.fit(samples["prompts"] + samples["chunks"])
    rule = json.loads(RULES.read_text(encoding="utf-8"))["token_estimate"]
    safe = te.safe(samples["numeric"], *fitted, ratio=rule["max_p95_real_over_estimated_numeric"]) if samples["numeric"] else fitted
    result = {
        "numeric_share": NUMERIC_SHARE, "old": old, "fitted": [round(x, 4) for x in fitted], "safe": [round(x, 4) for x in safe],
        "errors": {kind: {"old": te.summary(te.ratios(s, *old)), "safe": te.summary(te.ratios(s, *safe))}
                   for kind, s in samples.items() if s},
    }
    result["passes_ratio"] = result["errors"].get("numeric", {}).get("safe", {}).get("p95", 0) <= rule["max_p95_real_over_estimated_numeric"]
    REVIEW_DIR.mkdir(parents=True, exist_ok=True)
    TOKEN_REVIEW.write_text(json.dumps(result, ensure_ascii=False, indent=1))
    return result


def token_estimate_review(pipeline):
    """F14 (research R9): the app's prompt limit of 2,000 with the fitted estimate; the prompts it changes are
    generated and the answers that change judged by hand, against v4 (the quality half of the rule)."""
    from eval import token_estimate as te

    fit_result = json.loads(TOKEN_REVIEW.read_text(encoding="utf-8")) if TOKEN_REVIEW.exists() else None
    if fit_result is None:
        raise SystemExit("run `f14-review variant=token-estimate-fit` first")
    a, b, c = fit_result["safe"]
    params = json.loads(report.PARAMETERS.read_text(encoding="utf-8"))
    limit = params["prompt_limit_tokens"]
    config = pipeline._active_template_base()[0]
    ctx = pipeline.load_context(config.build)

    def prompt_of(q, row, blocks, previous):
        kept, ranks, turn = list(blocks), list(pipeline._block_ranks(ctx, row)), previous
        prompt = build_prompt(q.question, kept, turn, "v4")
        while te.estimate(prompt, a, b, c) > limit:
            if len(kept) > 1:
                worst = ranks.index(max(ranks))
                del kept[worst], ranks[worst]
            elif turn is not None:
                turn = None
            else:
                break
            prompt = build_prompt(q.question, kept, turn, "v4")
        dropped = len(blocks) - len(kept)
        return prompt, kept, f"estimador nuevo: {dropped} bloques fuera" + (" y el turno anterior" if previous and turn is None else "")

    return review(pipeline, "token-estimate", prompt_of)


def blocks_review(pipeline, blocks=3):
    """F14 (research R13): v4 with [blocks] blocks instead of D-034's 4, the same selector otherwise."""
    config = pipeline._active_template_base()[0]
    variant_config = dataclasses.replace(config, max_blocks=blocks, llm=None)
    ctx = pipeline.load_context(config.build)
    pipeline.retrieve(variant_config, ctx)
    rows = pipeline._retrieval_rows(ctx, variant_config)

    def prompt_of(q, row, base_blocks, previous):
        kept = [ContextBlock(**b) for b in rows[q.id]["blocks"]]
        return build_prompt(q.question, kept, previous, "v4"), kept, f"{len(kept)} bloques en lugar de {len(base_blocks)}"

    return review(pipeline, f"blocks-{blocks}", prompt_of)


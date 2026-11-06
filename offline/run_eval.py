"""F05 evaluation pipeline (specs/005-offline-eval-harness/contracts/eval-cli.md).

    offline/.venv/bin/python -I offline/run_eval.py <command> [options]
"""

import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # -I leaves the script directory out of sys.path

COMMANDS = [
    "fetch-corpus", "sources", "build", "validate-testset", "review-sheet", "review-apply", "sample",
    "retrieve", "generate", "ablations", "report", "chunker-golden", "calibrate", "rejudge", "device-extraction",
    "order-review", "template-review", "limit-review", "language-review", "crosslang-review",
    # F14 (specs/014-ondevice-performance/contracts/harness-f14.md): reviews against v4 on the installed runtime.
    "f14-review", "bench-bank", "bench-pages",
]
# F18 visual evaluation (specs/018-visual-rag-feasibility/contracts/visual-harness.md §3), in eval/visual_cli.py.
VISUAL_COMMANDS = [
    "visual-raster", "visual-build", "visual-validate", "visual-review-sheet", "visual-review-apply", "visual-freeze",
    "visual-retrieve", "visual-decide", "visual-generate", "visual-device-set", "visual-parity", "visual-manual",
    "visual-text-loss", "visual-report",
]
COMMANDS += VISUAL_COMMANDS


def corpus_option(options):
    options.add_argument("corpus", nargs="?", default="corpus=es", help="corpus=es (default) or corpus=en (F12)")


def chosen_corpus(value):
    from eval import corpus

    key, _, name = value.partition("=")
    if key != "corpus" or name not in corpus.CORPORA:
        raise SystemExit(f"unknown corpus {value}; use corpus={'|'.join(corpus.CORPORA)}")
    return corpus.CORPORA[name]


def fetch_corpus(args):
    from eval import corpus

    options = argparse.ArgumentParser(prog="fetch-corpus")
    options.add_argument("--record", action="store_true", help="download entries marked pending and record their hash")
    corpus_option(options)
    opts = options.parse_args(args)
    source = chosen_corpus(opts.corpus)
    if opts.record:
        corpus.record(source.manifest, source.docs)
    docs = corpus.load_manifest(source.manifest)
    corpus.fetch(docs, source.docs)
    corpus.verify(docs, source.docs)
    print(f"{len(docs)} documents verified")


def sources(args):
    from eval import corpus

    options = argparse.ArgumentParser(prog="sources")
    corpus_option(options)
    source = chosen_corpus(options.parse_args(args).corpus)
    source.sources.write_text(corpus.sources_markdown(corpus.load_manifest(source.manifest), source.purpose), encoding="utf-8")
    print(f"wrote {source.sources}")


def build(args):
    import json

    from eval import builds, index

    options = argparse.ArgumentParser(prog="build")
    options.add_argument("variants", nargs="+", help="variant names or 'all'")
    names = options.parse_args(args).variants
    names = [n for n in builds.VARIANTS if builds.corpus_of(n).name == "es"] if names == ["all"] else names
    for name in names:
        if name not in builds.VARIANTS:
            raise SystemExit(f"unknown variant {name}; choose from {', '.join(builds.VARIANTS)}")
        builds.build(name)
        db = index.connect(builds.index_path(name))
        info = index.build_info(db)
        info["fingerprint"] = index.fingerprint(db)
        db.close()
        print(json.dumps({"variant": name, **info}, ensure_ascii=False))


def parse_config(pairs):
    """EvalConfig from key=value pairs, e.g. build=chunk250 mode=vector max_blocks=4 neighbors=false llm=E2B."""
    import dataclasses

    from eval.runs import EvalConfig

    fields = {f.name: f for f in dataclasses.fields(EvalConfig)}
    values = {}
    for pair in pairs:
        key, _, raw = pair.partition("=")
        if key not in fields:
            raise SystemExit(f"unknown parameter {key}; choose from {', '.join(fields)}")
        default = getattr(EvalConfig(), key)
        if raw.lower() in ("none", "null"):
            values[key] = None
        elif isinstance(default, bool):
            values[key] = raw.lower() in ("1", "true", "yes", "si", "sí")
        elif isinstance(default, int) or key == "budget_tokens":
            values[key] = int(raw)
        elif key == "rerank_alpha":
            values[key] = float(raw)
        else:
            values[key] = raw
    return EvalConfig(**values)


def sample(args):
    from eval import pipeline

    rows = pipeline.write_sample(pipeline.load_context("ref"))
    print(f"{len(rows)} chunks -> {pipeline.SAMPLE}")


def validate_testset(args):
    from collections import Counter

    from eval import pipeline, testset

    options = argparse.ArgumentParser(prog="validate-testset")
    options.add_argument("--pre-review", action="store_true", help="do not require the reviewed minimum (T3)")
    options.add_argument("--write-relevants", action="store_true", help="store relevant_ref derived from the evidence")
    options.add_argument("set", nargs="?", help="set=crosslang validates the F12 cross-language sets instead")
    opts = options.parse_args(args)
    if opts.set == "set=crosslang":
        return validate_crosslang()
    ctx = pipeline.load_context("ref")
    questions, errors = pipeline.validate_testset(ctx, require_review=not opts.pre_review)
    if opts.write_relevants:
        testset.save(questions)
    live = [q for q in questions if q.review != "discarded"]
    print("types:", dict(Counter(q.type for q in live)), "| review:", dict(Counter(q.review for q in questions)))
    print("content types:", dict(Counter(q.content_type for q in live if q.type == "answerable")))
    for error in errors:
        print(error)
    if errors:
        raise SystemExit(f"{len(errors)} errors")
    print("test set valid")


def validate_crosslang():
    from collections import Counter

    from eval import crosslang, pipeline

    failed = 0
    for combo in crosslang.COMBINATIONS:
        if not crosslang.set_path(combo.set_name).exists():
            print(f"{combo.set_name}: missing")
            continue
        questions, errors = pipeline.validate_crosslang(combo.set_name)
        print(f"{combo.set_name}: {len(questions)} questions, types {dict(Counter(q.type for q in questions))}")
        for error in errors:
            print(f"  {error}")
        failed += len(errors)
    if failed:
        raise SystemExit(f"{failed} errors")
    print("crosslang sets valid")


def review_sheet(args):
    from eval import pipeline, testset

    ctx = pipeline.load_context("ref")
    questions = testset.load()
    by_id = {q.id: q.question for q in questions}
    picked = testset.review_sample(questions)
    testset.write_review_sheet(picked, testset.REVIEW_SHEET, ctx.titles, by_id)
    print(f"{len(picked)} questions -> {testset.REVIEW_SHEET}")


def review_apply(args):
    from eval import testset

    updated, summary = testset.apply_review(testset.load(), testset.REVIEW_SHEET)
    testset.save(updated)
    print("applied:", summary)


def retrieve(args):
    import json

    from eval import pipeline

    print(json.dumps(pipeline.retrieve(parse_config(args)), indent=1))


def generate(args):
    import json

    from eval import pipeline

    options = argparse.ArgumentParser(prog="generate")
    options.add_argument("--backend", default="gpu", choices=("gpu", "cpu"))
    options.add_argument("--ids", help="comma-separated question ids (default: all)")
    options.add_argument("--out", default="answers.jsonl")
    opts, rest = options.parse_known_args(args)
    config = parse_config(rest)
    if config.llm is None:
        raise SystemExit("generate needs llm=E2B or llm=E4B")
    ids = set(opts.ids.split(",")) if opts.ids else None
    print(json.dumps(pipeline.generate(config, opts.backend, ids, opts.out), indent=1))


def ablations(args):
    import dataclasses

    from eval import pipeline

    options = argparse.ArgumentParser(prog="ablations")
    options.add_argument("--base", nargs="*", default=[], help="key=value pairs of the base configuration")
    options.add_argument("--generation", action="store_true",
                         help="E2B answers for 4, 6 and 8 blocks with and without neighbours on the base configuration")
    opts = options.parse_args(args)
    base = parse_config(opts.base)
    if not opts.generation:
        pipeline.run_ablations(pipeline.retrieval_ablations(base))
        return
    variants = []
    for blocks in (4, 6, 8):
        for neighbors in (True, False):
            label = f"E2B, {blocks} bloques{'' if neighbors else ' sin vecinos'}"
            candidate = dataclasses.replace(base, max_blocks=blocks, neighbors=neighbors, budget_tokens=None)
            # Each variant gets the largest budget whose prompts fit the limit (constitution IV, research R6).
            budget, _ = pipeline.calibrate_budget(candidate)
            variants.append((label, dataclasses.replace(candidate, budget_tokens=budget, llm="E2B")))
    pipeline.run_ablations(variants, key="generation")


def calibrate(args):
    import json

    from eval import pipeline

    config = parse_config(args)
    budget, trace = pipeline.calibrate_budget(config)
    print(json.dumps({"budget_tokens": budget, "trace": trace}, indent=1))


def chunker_golden(args):
    from eval import chunker_golden as golden

    options = argparse.ArgumentParser(prog="chunker-golden")
    options.add_argument("--target", type=int)
    options.add_argument("--max", type=int)
    opts = options.parse_args(args)
    target, maximum = (opts.target, opts.max) if opts.target else golden.decided_params()
    cases, chunks = golden.generate(target, maximum)
    print(f"{cases} cases, {chunks} chunks -> {golden.GOLDEN}")


def rejudge(args):
    from eval import pipeline

    print(f"{len(pipeline.rejudge())} runs judged again")


def device_extraction(args):
    """F06: evidence kept by the phone's PDF extraction against pypdf (device-extraction.jsonl)."""
    from eval import device_extraction as module

    options = argparse.ArgumentParser(prog="device-extraction")
    options.add_argument("jsonl", type=Path)
    parsed = options.parse_args(args)
    rows, total = module.coverage(parsed.jsonl)
    print("| Documento | Evidencias | pypdf | Dispositivo | Caracteres pypdf | Caracteres dispositivo |")
    print("|---|---|---|---|---|---|")
    for r in rows + [{"doc": "Total", **total}]:
        print(f"| {r['doc']} | {r['evidences']} | {r['pypdf']} | {r['device']} | {r['pypdf_chars']} | {r['device_chars']} |")


def order_review(args):
    """F10 (D-035): Recall@bloques of the decided configuration with a trigram third list and a rerank."""
    from eval import pipeline

    for r in pipeline.order_review():
        print(f"{r['label']}: blocks_recall={r['blocks_recall']:.3f} hybrid_recall@20={r['hybrid_recall@20']:.3f} "
              f"hybrid_mrr={r['hybrid_mrr']:.3f} questions={r['questions']}")
    print(f"wrote {pipeline.ORDER_REVIEW_FILE}")


def template_review(args):
    """F11 (D-035): the decided configuration with other prompt templates, screened and judged against "informe"."""
    from eval import pipeline

    variants = ("v3", "v4")
    for pair in args:
        key, _, raw = pair.partition("=")
        if key != "variants":
            raise SystemExit(f"unknown parameter {key}; use variants=v3,v4")
        variants = tuple(v for v in raw.split(",") if v)
    for r in pipeline.template_review(variants):
        a = r["auto"]
        print(f"{r['label']}: changed={r['changed']} correct={a['answer_correct']:.3f} refusal={a['refusal_correct']:.3f} "
              f"citations={a['citations_correct']:.3f} screen={r['screen']} pending={r['pending']} adopted={r['adopted']}")
    print(f"wrote {pipeline.TEMPLATE_REVIEW_FILE}")


def limit_review(args):
    """F11 phone phase (SC-004): the decided configuration with a lower prompt limit, judged against v4."""
    from eval import pipeline

    limit = None
    for pair in args:
        key, _, raw = pair.partition("=")
        if key != "limit":
            raise SystemExit(f"unknown parameter {key}; use limit=1500")
        limit = int(raw)
    if limit is None:
        raise SystemExit("limit=N is required")
    r = pipeline.limit_review(limit)
    print(f"limit={r['limit']}: changed_prompts={r['changed_prompts']} dropped_blocks={r['dropped_blocks']} "
          f"dropped_previous={r['dropped_previous']} changed_answers={r['changed_answers']} pending={r['pending']} "
          f"prompt_tokens_est_p95={r['prompt_tokens_est_p95']} max={r['prompt_tokens_est_max']}")
    print(f"v4: {r['v4']}")
    print(f"manual: {r['manual']} meets_thresholds={r['meets_thresholds']}")
    print(f"wrote {pipeline.LIMIT_REVIEW_DIR / str(limit) / 'result.json'}")


def language_review(args):
    """F12 (D-058): «Automático» with the 426 Spanish questions, judged against v4."""
    from eval import pipeline

    if args:
        raise SystemExit("language-review takes no parameters")
    r = pipeline.language_review()
    print(f"auto: changed={r['changed']} pending={r['pending']} rule_auto={r['auto_rule']}")
    print(f"v4: {r['v4']}")
    print(f"manual: {r['manual']} meets_rule={r['meets_rule']}")
    print(f"wrote {pipeline.LANGUAGE_REVIEW_DIR / 'result.json'}")


def crosslang_review(args):
    """F12 (D-058, D-060): documents and questions in different languages, judged by hand."""
    from eval import pipeline

    english_answers = "auto"
    for pair in args:
        key, _, raw = pair.partition("=")
        if key != "english_answers":
            raise SystemExit(f"unknown parameter {key}; use english_answers=auto|es")
        english_answers = raw
    r = pipeline.crosslang_review(english_answers)
    for key, c in r["combinations"].items():
        print(f"{key}: answers={c['answer_language']} questions={c['questions']} pending={c['pending']} "
              f"blocks_hit={c['blocks_hit']} reference={c.get('reference')} ok={c['ok']}")
        print(f"  manual: {c['manual']}")
    print(f"skipped={r['skipped']} keeps_card_text={r['keeps_card_text']}")
    print(f"wrote {pipeline.CROSSLANG_REVIEW_DIR / 'result.json'}")


def f14_review(args):
    """F14 (D-088, D-090): v4 on the installed runtime, then each lever that changes the prompt or the context."""
    from eval import f14_review as module
    from eval import pipeline

    variant = None
    for pair in args:
        key, _, raw = pair.partition("=")
        if key != "variant":
            raise SystemExit(f"unknown parameter {key}; use variant=runtime")
        variant = raw
    if variant != "runtime":
        raise SystemExit("use variant=runtime")
    r = module.runtime_review(pipeline)
    print(f"{r['variant']}: changed_prompts={r['changed_prompts']} changed_answers={r['changed_answers']} "
          f"pending={r['pending']} passes={r['passes']}")
    print(f"  base: {r['base']}")
    print(f"  manual: {r['manual']}")
    print(f"wrote {module.REVIEW_CSV}")


def _key_values(args, allowed):
    values = {}
    for pair in args:
        key, _, raw = pair.partition("=")
        if key not in allowed:
            raise SystemExit(f"unknown parameter {key}; use {', '.join(f'{k}=' for k in allowed)}")
        values[key] = raw
    return values


def bench_bank(args):
    """F14 (R2): a bank of n chunks of the F05 corpus, embedded with the 270M, for the phone's search benches."""
    import time

    from eval import bench_bank as module
    from eval import builds, models, pipeline

    values = _key_values(args, ("n", "seed"))
    n, seed = int(values["n"]), int(values.get("seed", module.SEED))
    sources = module.sentences_by_document(builds.index_path("ref"))
    questions = [(q.id, q.question, next((p.question for p in pipeline.live_questions() if p.id == q.previous), None))
                 for q in pipeline.live_questions()]
    module.BENCH_DIR.mkdir(parents=True, exist_ok=True)
    cache = module.EmbeddingCache(module.BENCH_DIR / "embed-cache.db", builds.Embedder())
    started = time.time()

    def progress(done, total):
        print(f"{done}/{total} documents, {time.time() - started:.0f} s", flush=True)

    spec = models.spec(models.EMBEDDING)
    try:
        manifest = module.build_bank(n, sources, cache, questions, module.BENCH_DIR / f"bank-{n}", spec.file_name, spec.sha256,
                                     seed=seed, progress=progress)
    finally:
        cache.close()
    print(json.dumps({k: v for k, v in manifest.items() if k != "files"}, indent=1))


def bench_pages(args):
    """F14 (R10): a bank of m page vectors from the F18 visual corpus (768 px, 140 tokens, the app's)."""
    from eval import bench_bank as module
    from eval import visual_index

    values = _key_values(args, ("m", "sigma"))
    m, sigma = int(values["m"]), float(values.get("sigma", "0.02"))
    pages = [row.vector for row in visual_index.load_pages(visual_index.pages_path(768, 140), 256)]
    manifest = module.build_pages(m, pages, module.BENCH_DIR / f"pages-{m}", sigma)
    print(json.dumps({k: v for k, v in manifest.items() if k != "files"}, indent=1))


def report(args):
    from eval import report as report_module

    print(f"wrote {report_module.render()}")


HANDLERS = {
    "fetch-corpus": fetch_corpus, "sources": sources, "build": build, "sample": sample,
    "validate-testset": validate_testset, "review-sheet": review_sheet, "review-apply": review_apply,
    "retrieve": retrieve, "generate": generate, "ablations": ablations, "report": report, "calibrate": calibrate, "chunker-golden": chunker_golden, "rejudge": rejudge,
    "device-extraction": device_extraction, "order-review": order_review,
    "template-review": template_review, "limit-review": limit_review, "language-review": language_review,
    "crosslang-review": crosslang_review, "f14-review": f14_review, "bench-bank": bench_bank, "bench-pages": bench_pages,
}


def main(argv=None):
    parser = argparse.ArgumentParser(description="F05 offline evaluation pipeline")
    parser.add_argument("command", choices=COMMANDS)
    parser.add_argument("args", nargs=argparse.REMAINDER)
    options = parser.parse_args(argv)
    if options.command in VISUAL_COMMANDS:
        from eval import visual_cli

        visual_cli.HANDLERS[options.command](options.args)
        return
    handler = HANDLERS.get(options.command)
    if handler is None:
        raise SystemExit(f"{options.command}: not implemented")
    handler(options.args)


if __name__ == "__main__":
    main()

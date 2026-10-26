"""F05 evaluation pipeline (specs/005-offline-eval-harness/contracts/eval-cli.md).

    offline/.venv/bin/python -I offline/run_eval.py <command> [options]
"""

import argparse
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # -I leaves the script directory out of sys.path

COMMANDS = [
    "fetch-corpus", "sources", "build", "validate-testset", "review-sheet", "review-apply", "sample",
    "retrieve", "generate", "ablations", "report", "chunker-golden", "calibrate", "rejudge",
]


def fetch_corpus(args):
    from eval import corpus

    options = argparse.ArgumentParser(prog="fetch-corpus")
    options.add_argument("--record", action="store_true", help="download entries marked pending and record their hash")
    if options.parse_args(args).record:
        corpus.record()
    docs = corpus.load_manifest()
    corpus.fetch(docs)
    corpus.verify(docs)
    print(f"{len(docs)} documents verified")


def sources(args):
    from eval import corpus

    corpus.SOURCES.write_text(corpus.sources_markdown(corpus.load_manifest()), encoding="utf-8")
    print(f"wrote {corpus.SOURCES}")


def build(args):
    import json

    from eval import builds, index

    options = argparse.ArgumentParser(prog="build")
    options.add_argument("variants", nargs="+", help="variant names or 'all'")
    names = options.parse_args(args).variants
    names = list(builds.VARIANTS) if names == ["all"] else names
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
    opts = options.parse_args(args)
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


def report(args):
    from eval import report as report_module

    print(f"wrote {report_module.render()}")


HANDLERS = {
    "fetch-corpus": fetch_corpus, "sources": sources, "build": build, "sample": sample,
    "validate-testset": validate_testset, "review-sheet": review_sheet, "review-apply": review_apply,
    "retrieve": retrieve, "generate": generate, "ablations": ablations, "report": report, "calibrate": calibrate, "chunker-golden": chunker_golden, "rejudge": rejudge,
}


def main(argv=None):
    parser = argparse.ArgumentParser(description="F05 offline evaluation pipeline")
    parser.add_argument("command", choices=COMMANDS)
    parser.add_argument("args", nargs=argparse.REMAINDER)
    options = parser.parse_args(argv)
    handler = HANDLERS.get(options.command)
    if handler is None:
        raise SystemExit(f"{options.command}: not implemented")
    handler(options.args)


if __name__ == "__main__":
    main()

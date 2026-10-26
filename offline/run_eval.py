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
    "retrieve", "generate", "ablations", "report", "chunker-golden",
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


HANDLERS = {"fetch-corpus": fetch_corpus, "sources": sources, "build": build}


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

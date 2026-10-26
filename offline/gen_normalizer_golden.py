"""Builds golden/normalizer_golden.jsonl from golden/normalizer_inputs.txt (F04 rule O2).

Deterministic: same inputs, same bytes. Inputs keep their file order, duplicates are dropped and the
run fails with fewer than MIN_PAIRS pairs.

    offline/.venv/bin/python -I offline/gen_normalizer_golden.py [--inputs PATH] [--out PATH]
"""

import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # -I leaves the script directory out of sys.path

from normalizer import normalize  # noqa: E402

MIN_PAIRS = 300


def read_inputs(path):
    """One input per line; '#' lines are comments. Only '\\n' separates lines."""
    seen, inputs = set(), []
    for line in path.read_text(encoding="utf-8").split("\n"):
        line = line.rstrip("\r")
        if not line.strip() or line.startswith("#") or line in seen:
            continue
        seen.add(line)
        inputs.append(line)
    return inputs


def build(inputs):
    return [{"in": text, "out": normalize(text)} for text in inputs]


def write(pairs, path):
    lines = (json.dumps(pair, ensure_ascii=False) + "\n" for pair in pairs)
    path.write_text("".join(lines), encoding="utf-8", newline="\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--inputs", type=Path, default=HERE / "golden" / "normalizer_inputs.txt")
    parser.add_argument("--out", type=Path, default=HERE / "golden" / "normalizer_golden.jsonl")
    args = parser.parse_args(argv)

    pairs = build(read_inputs(args.inputs))
    if len(pairs) < MIN_PAIRS:
        raise SystemExit(f"Only {len(pairs)} pairs; the golden needs at least {MIN_PAIRS}")
    write(pairs, args.out)
    print(f"{len(pairs)} pairs -> {args.out}")


if __name__ == "__main__":
    main()

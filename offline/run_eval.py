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


def main(argv=None):
    parser = argparse.ArgumentParser(description="F05 offline evaluation pipeline")
    parser.add_argument("command", choices=COMMANDS)
    parser.add_argument("args", nargs=argparse.REMAINDER)
    options = parser.parse_args(argv)
    raise SystemExit(f"{options.command}: not implemented")


if __name__ == "__main__":
    main()

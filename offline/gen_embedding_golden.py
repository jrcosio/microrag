"""Builds golden/embedding_golden.jsonl from golden/embedding_texts.jsonl (F04 rule O3).

Embeds each text on CPU with the same EmbeddingGemma 2 270M file as the app, the same inputs
(prefixes and contextual header) and the same truncation to 256 with L2. EmbeddingGoldenTest
embeds them again on the phone and requires a cosine >= 0.99 per pair.

    offline/.venv/bin/python -I offline/gen_embedding_golden.py [--model PATH] [--out PATH]
"""

import argparse
import hashlib
import importlib.metadata
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # -I leaves the script directory out of sys.path

from embedding_text import DIMENSIONS, document_input, query_input, truncate_and_normalize  # noqa: E402

MODEL_FILE = "embeddinggemma-2-text-270m.litertlm"
# ModelCatalog EG2_270M (D-025); EmbeddingGoldenFileTest checks both stay equal.
MODEL_SHA256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb"
# Same input signature selection as LiteRtTextEmbedder on Android.
MAX_INPUT_LENGTH = 512


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def embedding_input(item):
    if item["kind"] == "query":
        return query_input(item["text"])
    return document_input(item["text"], item.get("header", ""))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--model", type=Path, default=HERE / "models" / MODEL_FILE)
    parser.add_argument("--texts", type=Path, default=HERE / "golden" / "embedding_texts.jsonl")
    parser.add_argument("--out", type=Path, default=HERE / "golden" / "embedding_golden.jsonl")
    args = parser.parse_args(argv)

    found = sha256(args.model)
    if found != MODEL_SHA256:
        raise SystemExit(f"{args.model} is not the verified model: sha256 {found}")

    from litert_lm import Backend, EmbeddingEngine, EmbeddingOptions

    items = [json.loads(line) for line in args.texts.read_text(encoding="utf-8").splitlines() if line.strip()]
    cache = HERE / "build" / "litert-cache"
    cache.mkdir(parents=True, exist_ok=True)

    lines = [{
        "meta": {
            "model_file": MODEL_FILE,
            "model_sha256": MODEL_SHA256,
            "litert_lm_api": importlib.metadata.version("litert-lm-api"),
            "max_input_length": MAX_INPUT_LENGTH,
            "dims": DIMENSIONS,
        }
    }]
    with EmbeddingEngine(str(args.model), backend=Backend.CPU(), cache_dir=str(cache), max_input_length=MAX_INPUT_LENGTH) as engine:
        for item in items:
            text = embedding_input(item)
            raw = engine.compute_embedding(text, EmbeddingOptions(normalize=False)).embedding
            lines.append({"id": item["id"], "kind": item["kind"], "input": text, "v256": truncate_and_normalize(raw)})

    args.out.write_text("".join(json.dumps(line, ensure_ascii=False) + "\n" for line in lines), encoding="utf-8", newline="\n")
    print(f"{len(items)} embeddings -> {args.out}")


if __name__ == "__main__":
    main()

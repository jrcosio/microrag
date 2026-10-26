"""Model files used by the evaluation and their verification (F05 contracts/eval-cli.md E1).

A model is used only after its SHA-256 matches the app catalog (EmbeddingGemma 2, Gemma 4 E2B) or the
value verified on Hugging Face for Gemma 4 E4B (research R7).
"""

import hashlib
import json
from pathlib import Path

from eval import appconfig

OFFLINE = Path(__file__).resolve().parents[1]
MODELS_DIR = OFFLINE / "models"
HASH_CACHE = OFFLINE / "build" / "model_hashes.json"

EMBEDDING = "EG2_270M"
LLMS = {"E2B": "GEMMA4_E2B", "E4B": "GEMMA4_E4B"}

# litert-community/gemma-4-E4B-it-litert-lm @ e6a3892a8c22cf87ff99583996149cf3cb32452b (Apache-2.0)
E4B = appconfig.CatalogModel(
    id="GEMMA4_E4B",
    file_name="gemma-4-E4B-it.litertlm",
    sha256="0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
    size_bytes=3_659_530_240,
)
E4B_URL = (
    "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/"
    "e6a3892a8c22cf87ff99583996149cf3cb32452b/gemma-4-E4B-it.litertlm"
)


def spec(model_id):
    return E4B if model_id == E4B.id else appconfig.model(model_id)


def path(model_id):
    return MODELS_DIR / spec(model_id).file_name


def sha256_file(file):
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify(model_id):
    """Returns the model path once its hash matches; the hash is cached by size and modification time."""
    expected = spec(model_id)
    file = path(model_id)
    if not file.exists():
        raise SystemExit(f"{expected.file_name} is missing in {MODELS_DIR}")
    stat = file.stat()
    key = f"{file.resolve()}|{stat.st_size}|{stat.st_mtime_ns}"
    cache = json.loads(HASH_CACHE.read_text()) if HASH_CACHE.exists() else {}
    found = cache.get(key) or sha256_file(file)
    if found != expected.sha256:
        raise SystemExit(f"{expected.file_name} is not the verified model: sha256 {found}")
    cache[key] = found
    HASH_CACHE.parent.mkdir(parents=True, exist_ok=True)
    HASH_CACHE.write_text(json.dumps(cache, indent=1, sort_keys=True))
    return file

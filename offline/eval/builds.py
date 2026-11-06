"""Evaluation index variants and how each one is built (F05 data-model §3, research R4–R5).

`en` indexes the English corpus of F12 with the chunking of the app (contracts/eval-language.md L9), and `visual-text`
the text of the visual corpus of F18 the same way.
"""

import hashlib
import json
import time
from pathlib import Path

from embedding_text import DOCUMENT, OVERFLOW_ATTEMPTS, overflow_cut
from eval import appconfig, corpus, index, models
from eval.chunker import chunk_params, utf16_len
from eval.extract import RawBlock, extract

OFFLINE = Path(__file__).resolve().parents[1]
BUILD = OFFLINE / "build"
INDEX_DIR = BUILD / "index"
EXTRACT_DIR = BUILD / "extract"
PARAMETERS = OFFLINE / "config" / "rag_parameters.json"

MAX_INPUT_TOKENS = 512        # max_input_length of the embedding model, as in the app
HEADER_ALLOWANCE_TOKENS = 40  # room kept for the contextual header (research R4)

VARIANTS = {
    "ref": {"tokens": 350, "header": True},
    "chunk250": {"tokens": 250, "header": True},
    "chunk500": {"tokens": 500, "header": True},
    "nohdr": {"tokens": 350, "header": False},
    "dim128": {"derive": "dims", "dims": 128},
    "dim512": {"derive": "dims", "dims": 512},
    "dim768": {"derive": "dims", "dims": 768},
    "fts_raw": {"derive": "fts", "fts": "raw"},
    "fts_stop": {"derive": "fts", "fts": "stop"},
    "en": {"tokens": 350, "header": True, "corpus": "en"},
    # F18: the text of the visual corpus, with the app's chunking (research R10). Dense tables of figures need
    # more D-036 cuts than the app's 12 (where that document would fail to index): the evaluation keeps them,
    # which makes «Texto» stronger, never weaker, and counts them in `beyond_app_cuts`.
    "visual-text": {"tokens": 350, "header": True, "corpus": "visual", "overflow_attempts": 40},
}


def corpus_of(variant):
    return corpus.CORPORA[VARIANTS[variant].get("corpus", "es")]


def index_path(variant):
    return INDEX_DIR / f"{variant}.db"


def extracted(doc, docs_dir=corpus.DOCS):
    """Blocks of a corpus document, cached by its hash."""
    cache = EXTRACT_DIR / f"{doc.sha256}.json"
    if cache.exists():
        return [RawBlock(tuple(b["heading_path"]), b["text"], b["page"]) for b in json.loads(cache.read_text(encoding="utf-8"))]
    blocks = extract(docs_dir / doc.file_name, doc.format)
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps([{"heading_path": list(b.heading_path), "text": b.text, "page": b.page} for b in blocks],
                                ensure_ascii=False), encoding="utf-8")
    return blocks


def corpus_fingerprint(docs):
    return hashlib.sha256("|".join(f"{d.id}:{d.sha256}" for d in docs).encode()).hexdigest()


def tokenizer_ratio(docs):
    """Characters per Gemma token over the whole corpus, and the prefix length in tokens (research R4)."""
    cache = BUILD / "chars_per_token.json"
    key = corpus_fingerprint(docs)
    if cache.exists():
        stored = json.loads(cache.read_text())
        if stored.get("corpus") == key:
            return stored["chars_per_token"], stored["prefix_tokens"]
    from litert_lm import Backend, Engine

    chars = tokens = 0
    with Engine(str(models.verify("GEMMA4_E2B")), backend=Backend.CPU(), max_num_tokens=4096) as engine:
        for doc in docs:
            for block in extracted(doc):
                chars += utf16_len(block.text)
                tokens += len(engine.tokenize(block.text))
        prefix_tokens = len(engine.tokenize(DOCUMENT))
    ratio = round(chars / tokens, 3)
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps({"corpus": key, "chars_per_token": ratio, "prefix_tokens": prefix_tokens, "chars": chars, "tokens": tokens}))
    return ratio, prefix_tokens


def ref_build_info():
    path = index_path("ref")
    if not path.exists():
        raise SystemExit("build ref first")
    db = index.connect(path)
    try:
        return index.build_info(db)
    finally:
        db.close()


def chunking(variant, docs):
    """(chars per token, prefix tokens, max input chars, target chars, max chars) of a built variant.

    The Spanish variants measure the tokenizer ratio of their corpus (research R4). Another corpus takes the
    chunking of the app from rag_parameters.json, as the phone would, and the prefix and input limit of ref:
    measuring it again would replace the cache in build/chars_per_token.json (F12 L9).
    """
    spec = VARIANTS[variant]
    if corpus_of(variant).name == "es":
        ratio, prefix_tokens = tokenizer_ratio(docs)
        max_input_chars = round((MAX_INPUT_TOKENS - prefix_tokens - HEADER_ALLOWANCE_TOKENS) * ratio)
        return (ratio, prefix_tokens, max_input_chars, *chunk_params(spec["tokens"], ratio, max_input_chars))
    params = json.loads(PARAMETERS.read_text(encoding="utf-8"))
    ref = ref_build_info()
    return (params["chars_per_token"], ref["prefix_tokens"], ref["max_input_chars"],
            params["chunk_target_chars"], params["chunk_max_chars"])


class Embedder:
    """EmbeddingGemma 2 on CPU, as the golden of F04; returns the raw 768-component vector of a text.

    LiteRT-LM 0.18.1 rejects inputs longer than 512 tokens and its Android API has no overflow strategy,
    so the app needs a rule too: the embedded text is cut to 90 % of its length, at a space, until the
    model accepts it. Only the embedded text is cut, never the stored chunk. [truncated] counts the cuts.
    """

    def __init__(self, attempts=OVERFLOW_ATTEMPTS, gpu=False):
        from litert_lm import Backend, EmbeddingEngine, EmbeddingOptions

        cache = BUILD / "litert-cache"
        cache.mkdir(parents=True, exist_ok=True)
        # [gpu]: only the F14 benchmark banks, as the app's ingest may embed on the GPU (D-024).
        self.engine = EmbeddingEngine(str(models.verify(models.EMBEDDING)), backend=Backend.GPU() if gpu else Backend.CPU(),
                                      cache_dir=str(cache), max_input_length=MAX_INPUT_TOKENS)
        self.options = EmbeddingOptions(normalize=False)
        self.attempts = attempts
        self.truncated = 0
        self.beyond_app = 0

    def __call__(self, text):
        for attempt in range(self.attempts):
            try:
                vector = self.engine.compute_embedding(text, self.options).embedding
                self.truncated += attempt > 0
                self.beyond_app += attempt >= OVERFLOW_ATTEMPTS
                return vector
            except RuntimeError:
                text = overflow_cut(text)
        raise RuntimeError("embedding input still too long after truncation")


def embedder(attempts=OVERFLOW_ATTEMPTS):
    model = Embedder(attempts)
    return model.engine, model


def build(variant):
    spec = VARIANTS[variant]
    INDEX_DIR.mkdir(parents=True, exist_ok=True)
    target = index_path(variant)
    if "derive" in spec:
        source = index_path("ref")
        if not source.exists():
            raise SystemExit("build ref first")
        if spec["derive"] == "dims":
            index.derive_dims(source, target, spec["dims"])
        else:
            index.derive_fts(source, target, spec["fts"])
        db = index.connect(target)
        index._write_build(db, {**index.build_info(db), "variant": variant})  # noqa: SLF001
        db.commit()
        db.close()
        return {"variant": variant, **spec}

    source = corpus_of(variant)
    docs = corpus.load_manifest(source.manifest)
    corpus.verify(docs, source.docs)
    ratio, prefix_tokens, max_input_chars, target_chars, max_chars = chunking(variant, docs)
    documents = []
    for doc in docs:
        blocks = extracted(doc, source.docs)
        pages = max((b.page for b in blocks if b.page), default=None)
        documents.append(index.IndexDocument(doc.id, doc.title, doc.sha256, doc.mime_type,
                                             (source.docs / doc.file_name).stat().st_size, pages, blocks))
    engine, embed = embedder(spec.get("overflow_attempts", OVERFLOW_ATTEMPTS))
    started = time.time()
    try:
        info = index.build_index(
            target, documents, target_chars, max_chars, spec["header"], ratio, embed,
            build_info={
                "variant": variant, "tokens": spec["tokens"], "corpus": corpus_fingerprint(docs),
                "schema_sha256": hashlib.sha256(index.SCHEMA.read_bytes()).hexdigest(),
                "normalizer_version": appconfig.normalizer_version(), "prefix_tokens": prefix_tokens,
                "max_input_chars": max_input_chars,
            },
        )
    finally:
        del engine
    info["seconds"] = round(time.time() - started, 1)
    info["truncated_inputs"] = embed.truncated
    db = index.connect(target)
    index._write_build(db, {**index.build_info(db), "seconds": info["seconds"], "truncated_inputs": embed.truncated,  # noqa: SLF001
                            "beyond_app_cuts": embed.beyond_app})
    db.commit()
    db.close()
    return info

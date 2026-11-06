"""F14 benchmark banks (specs/014-ondevice-performance/research.md R2 and R10, data-model §3, contract H2 and H4).

A bank of N chunks has the shape of the app's index: chunks of real sentences of the F05 corpus, recombined per
document with a fixed seed up to the chunk size of D-034, with their contextual header, really embedded with the
270M on the desktop's CPU (the same vectors as the phone, F04 golden). Document i depends only on the seed and i,
so a bank of 10k is the start of the 50k and 100k ones and the embeddings are computed once (cache by input).
Page banks perturb the real page vectors of the F18 visual corpus. Development tooling: never shipped (D-002).
"""

import gzip
import hashlib
import json
import math
import random
import re
import sqlite3
import struct
from pathlib import Path

from embedding_text import DIMENSIONS, document_input, query_input, truncate_and_normalize
from eval import builds

BENCH_DIR = builds.BUILD / "bench"
SEED = 2026
CHUNKS_PER_DOCUMENT = 25
FOLDERS = 20
EXCLUDED_FRACTION = 0.3
TARGET_CHARS = 1594
MAX_CHARS = 2113
SENTENCE = re.compile(r"(?<=[.;:!?])\s+")


def sentences_by_document(db_path):
    """{(title, [heading paths]): [sentences]} of the F05 reference index, in a stable order."""
    db = sqlite3.connect(str(db_path))
    try:
        rows = db.execute(
            "SELECT d.display_name, c.heading_path, c.content FROM chunks c JOIN documents d ON d.id = c.document_id "
            "ORDER BY d.content_hash, c.ordinal").fetchall()
    finally:
        db.close()
    docs = {}
    for title, heading, content in rows:
        entry = docs.setdefault(title, {"headings": [], "sentences": []})
        if heading and heading not in entry["headings"]:
            entry["headings"].append(heading)
        entry["sentences"] += [s.strip() for s in SENTENCE.split(content) if len(s.strip()) >= 20]
    return [(title, d["headings"], d["sentences"]) for title, d in docs.items() if d["sentences"]]


def chunk_text(rng, sentences, target=TARGET_CHARS, maximum=MAX_CHARS):
    """Sentences drawn with replacement until the target; never over [maximum], cut at a space if one is too long."""
    parts, size = [], 0
    while size < target:
        sentence = rng.choice(sentences)
        room = maximum - size - (1 if parts else 0)
        if len(sentence) > room:
            if parts:
                break
            sentence = sentence[:room].rsplit(" ", 1)[0]
        parts.append(sentence)
        size += len(sentence) + (1 if len(parts) > 1 else 0)
    return " ".join(parts)


def document(i, sources, seed=SEED):
    """Document [i] of every bank: its name, folder, whether it is excluded, and its chunks."""
    rng = random.Random(f"{seed}-{i}")
    title, headings, sentences = sources[rng.randrange(len(sources))]
    name = f"bench-{i:05d}.txt"
    chunks = []
    for ordinal in range(CHUNKS_PER_DOCUMENT):
        heading = rng.choice(headings) if headings and rng.random() < 0.8 else None
        chunks.append({"ordinal": ordinal, "heading_path": heading, "page": None, "text": chunk_text(rng, sentences)})
    return {"doc": i, "name": name, "source": title, "folder": i % FOLDERS, "excluded": rng.random() < EXCLUDED_FRACTION,
            "chunks": chunks}


def header(doc, chunk):
    return " › ".join(x for x in (doc["name"], chunk["heading_path"]) if x)


class EmbeddingCache:
    """Raw 768-component vectors by SHA-256 of the embedding input, so the three banks embed each chunk once."""

    def __init__(self, path, embed):
        self.db = sqlite3.connect(str(path))
        self.db.execute("CREATE TABLE IF NOT EXISTS v (key TEXT PRIMARY KEY, vector BLOB NOT NULL)")
        self.embed = embed

    def __call__(self, text):
        key = hashlib.sha256(text.encode("utf-8")).hexdigest()
        row = self.db.execute("SELECT vector FROM v WHERE key = ?", (key,)).fetchone()
        if row:
            return list(struct.unpack(f"<{len(row[0]) // 4}f", row[0]))
        vector = list(self.embed(text))
        self.db.execute("INSERT INTO v(key, vector) VALUES (?, ?)", (key, struct.pack(f"<{len(vector)}f", *vector)))
        self.db.commit()
        return vector

    def close(self):
        self.db.close()


def warm_cache(start, end, sources, embed, seed=SEED):
    """Embeds the chunks of documents [start, end) into [embed]'s cache, so several processes can share the work."""
    for i in range(start, end):
        doc = document(i, sources, seed)
        for chunk in doc["chunks"]:
            embed(document_input(chunk["text"], header(doc, chunk)))


def merge_caches(target, sources):
    """Copies the vectors of the [sources] cache files into [target]."""
    db = sqlite3.connect(str(target))
    db.execute("CREATE TABLE IF NOT EXISTS v (key TEXT PRIMARY KEY, vector BLOB NOT NULL)")
    for source in sources:
        db.execute("ATTACH DATABASE ? AS other", (str(source),))
        db.execute("INSERT OR IGNORE INTO v SELECT key, vector FROM other.v")
        db.commit()
        db.execute("DETACH DATABASE other")
    db.close()


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def build_bank(n, sources, embed, questions, out_dir, model, model_sha256, seed=SEED, progress=None, compress=True):
    """Writes chunks.jsonl.gz, vectors.f32, questions.jsonl, queries.f32 and manifest.json for a bank of [n] chunks.

    [questions] are (id, question, previous question or None); their query vectors serve the benches without a
    model (the emulator), the phone embeds them again with its own engine.
    """
    if n % CHUNKS_PER_DOCUMENT:
        raise ValueError(f"{n} is not a multiple of {CHUNKS_PER_DOCUMENT}")
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    documents = n // CHUNKS_PER_DOCUMENT
    excluded = 0
    # Uncompressed for an Android asset: the packaging decompresses .gz assets and drops the suffix.
    chunks_name = "chunks.jsonl.gz" if compress else "chunks.jsonl"
    opener = (lambda: gzip.open(out / chunks_name, "wt", encoding="utf-8")) if compress else (lambda: (out / chunks_name).open("w", encoding="utf-8"))
    with opener() as chunks_file, (out / "vectors.f32").open("wb") as vectors:
        for i in range(documents):
            doc = document(i, sources, seed)
            excluded += doc["excluded"]
            for chunk in doc["chunks"]:
                vector = truncate_and_normalize(embed(document_input(chunk["text"], header(doc, chunk))), DIMENSIONS)
                vectors.write(struct.pack(f"<{DIMENSIONS}f", *vector))
                chunks_file.write(json.dumps({"doc": i, "name": doc["name"], "folder": doc["folder"], "excluded": doc["excluded"],
                                              **chunk}, ensure_ascii=False) + "\n")
            if progress and (i + 1) % 100 == 0:
                progress(i + 1, documents)
    with (out / "questions.jsonl").open("w", encoding="utf-8") as q_file, (out / "queries.f32").open("wb") as q_vectors:
        for qid, question, previous in questions:
            q_file.write(json.dumps({"id": qid, "question": question, "previous": previous}, ensure_ascii=False) + "\n")
            vector = truncate_and_normalize(embed(query_input(question)), DIMENSIONS)
            q_vectors.write(struct.pack(f"<{DIMENSIONS}f", *vector))
    files = {name: sha256(out / name) for name in (chunks_name, "vectors.f32", "questions.jsonl", "queries.f32")}
    manifest = {"n": n, "seed": seed, "dims": DIMENSIONS, "model": model, "model_sha256": model_sha256,
                "documents": documents, "chunks_per_document": CHUNKS_PER_DOCUMENT, "folders": FOLDERS,
                "excluded_documents": excluded, "excluded_fraction": EXCLUDED_FRACTION,
                "target_chars": TARGET_CHARS, "max_chars": MAX_CHARS, "questions": len(questions), "files": files}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1, ensure_ascii=False), encoding="utf-8")
    return manifest


def verify(out_dir):
    """The manifest's files with their SHA-256; raises on a missing or changed file."""
    out = Path(out_dir)
    manifest = json.loads((out / "manifest.json").read_text(encoding="utf-8"))
    for name, digest in manifest["files"].items():
        if not (out / name).is_file() or sha256(out / name) != digest:
            raise ValueError(f"{out / name} is missing or changed")
    return manifest


def build_pages(m, page_vectors, out_dir, sigma, seed=SEED):
    """A page bank of [m] vectors: real page vectors with gaussian noise of [sigma] per component, re-normalized."""
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(f"{seed}-pages")
    with (out / "vectors.f32").open("wb") as vectors:
        for i in range(m):
            base = page_vectors[i % len(page_vectors)]
            noisy = [x + rng.gauss(0.0, sigma) for x in base] if i >= len(page_vectors) else list(base)
            norm = math.sqrt(sum(x * x for x in noisy))
            vectors.write(struct.pack(f"<{len(noisy)}f", *(x / norm for x in noisy)))
    manifest = {"m": m, "seed": seed, "sigma": sigma, "source": "corpus_visual", "real_pages": len(page_vectors),
                "dims": len(page_vectors[0]), "files": {"vectors.f32": sha256(out / "vectors.f32")}}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1), encoding="utf-8")
    return manifest

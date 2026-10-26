"""Evaluation indexes with the app artifacts (F05 data-model §3, contracts/eval-cli.md E1–E4).

Same schema.sql, normalizer, embedding inputs, truncation and SQLite-Vector as the app, plus tables
that only the evaluation uses: the full 768-component vectors and the build information.
"""

import hashlib
import importlib.resources
import json
import shutil
import sqlite3
import struct
from dataclasses import dataclass
from pathlib import Path

import normalizer
from embedding_text import document_input, truncate_and_normalize
from eval import appconfig, fts_variants
from eval.chunker import Chunker, token_estimate

SCHEMA = appconfig.REPO / "app/src/main/assets/schema.sql"
EVAL_TABLES = """
CREATE TABLE IF NOT EXISTS eval_full_embeddings (chunk_id INTEGER PRIMARY KEY, v768 BLOB NOT NULL);
CREATE TABLE IF NOT EXISTS eval_build (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""
# Fixed timestamps: the evaluation index must be identical byte for byte in its content (SC-001).
TIMESTAMP_MS = 0


@dataclass(frozen=True)
class IndexDocument:
    doc_id: str
    title: str
    sha256: str
    mime_type: str
    size_bytes: int
    page_count: int | None
    blocks: list


def extension_path():
    return str(importlib.resources.files("sqlite_vector") / "binaries" / "vector")


def connect(path, dims=None):
    """Opens an index with SQLite-Vector and its vector_init, as the app does on every connection."""
    db = sqlite3.connect(str(path))
    db.enable_load_extension(True)
    db.load_extension(extension_path(), entrypoint="sqlite3_vector_init")
    db.enable_load_extension(False)
    db.execute("PRAGMA foreign_keys = ON")
    if dims is None:
        row = db.execute("SELECT value FROM rag_meta WHERE key = 'embedding_dims'").fetchone() if _has_table(db, "rag_meta") else None
        dims = int(row[0]) if row else appconfig.dimensions()
    if _has_table(db, "chunks"):
        db.execute(f"SELECT vector_init('chunks', 'embedding', 'dimension={dims},type=FLOAT32,distance=COSINE,normalized=1')")
    return db


def _has_table(db, name):
    return db.execute("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", (name,)).fetchone() is not None


def _blob(vector):
    return struct.pack(f"<{len(vector)}f", *vector)


def build_index(path, documents, target_chars, max_chars, contextual_header, chars_per_token, embed, build_info):
    """Builds an index from extracted documents; [embed] returns the raw 768-component vector of a text."""
    path = Path(path)
    path.unlink(missing_ok=True)
    db = sqlite3.connect(str(path))
    db.executescript(SCHEMA.read_text(encoding="utf-8"))
    db.executescript(EVAL_TABLES)
    db.executemany("INSERT INTO rag_meta(key, value) VALUES (?, ?)", appconfig.expected_meta().items())
    db.commit()
    db.close()

    db = connect(path)
    chunker = Chunker(target_chars, max_chars)
    counts = {"documents": 0, "chunks": 0, "too_long": 0}
    sizes = []
    for doc in documents:
        chunks = chunker.chunk(doc.blocks)
        cur = db.execute(
            "INSERT INTO documents(source, display_name, mime_type, content_hash, status, chunk_count, size_bytes, "
            "page_count, local_path, created_at_ms, updated_at_ms) VALUES ('user', ?, ?, ?, 'done', ?, ?, ?, ?, ?, ?)",
            (doc.title, doc.mime_type, doc.sha256, len(chunks), doc.size_bytes, doc.page_count,
             f"corpus/docs/{doc.doc_id}", TIMESTAMP_MS, TIMESTAMP_MS),
        )
        document_id = cur.lastrowid
        counts["documents"] += 1
        for chunk in chunks:
            header = " › ".join(x for x in (doc.title, chunk.heading_path) if x)
            full = embed(document_input(chunk.content, header if contextual_header else ""))
            vector = truncate_and_normalize(full, appconfig.dimensions())
            tokens = token_estimate(chunk.content, chars_per_token)
            cur = db.execute(
                "INSERT INTO chunks(document_id, ordinal, heading_path, page, content, title_norm, body_norm, "
                "token_estimate, embedding) VALUES (?, ?, ?, ?, ?, ?, ?, ?, vector_as_f32(?))",
                (document_id, chunk.ordinal, chunk.heading_path or None, chunk.page, chunk.content,
                 normalizer.normalize(header), normalizer.normalize(chunk.content), tokens, _blob(vector)),
            )
            db.execute("INSERT INTO eval_full_embeddings(chunk_id, v768) VALUES (?, ?)", (cur.lastrowid, _blob(full)))
            counts["chunks"] += 1
            sizes.append(len(chunk.content))
            if len(chunk.content) > max_chars:
                counts["too_long"] += 1
    info = {
        **build_info, "target_chars": target_chars, "max_chars": max_chars, "contextual_header": contextual_header,
        "chars_per_token": chars_per_token, "dims": appconfig.dimensions(), "fts": "v1", **counts,
        "chunk_chars_min": min(sizes, default=0), "chunk_chars_max": max(sizes, default=0),
        "chunk_chars_mean": round(sum(sizes) / len(sizes), 1) if sizes else 0,
    }
    _write_build(db, info)
    db.commit()
    db.close()
    return info


def _write_build(db, info):
    db.executemany(
        "INSERT OR REPLACE INTO eval_build(key, value) VALUES (?, ?)",
        [(key, json.dumps(value, ensure_ascii=False)) for key, value in sorted(info.items())],
    )


def build_info(db):
    return {key: json.loads(value) for key, value in db.execute("SELECT key, value FROM eval_build")}


def derive_dims(source, target, dims):
    """Copy of [source] whose embeddings are the full vectors truncated to [dims] and re-normalized."""
    shutil.copyfile(source, target)
    db = connect(target, dims=dims)
    rows = db.execute("SELECT chunk_id, v768 FROM eval_full_embeddings").fetchall()
    for chunk_id, blob in rows:
        full = struct.unpack(f"<{len(blob) // 4}f", blob)
        db.execute("UPDATE chunks SET embedding = vector_as_f32(?) WHERE id = ?", (_blob(truncate_and_normalize(list(full), dims)), chunk_id))
    db.execute("UPDATE rag_meta SET value = ? WHERE key = 'embedding_dims'", (str(dims),))
    _write_build(db, {**build_info(db), "dims": dims, "derived_from": Path(source).stem})
    db.commit()
    db.close()


def derive_fts(source, target, variant):
    """Copy of [source] whose lexical columns use another normalization; the triggers rebuild the FTS."""
    shutil.copyfile(source, target)
    db = connect(target)
    rows = db.execute(
        "SELECT c.id, d.display_name, c.heading_path, c.content FROM chunks c JOIN documents d ON d.id = c.document_id"
    ).fetchall()
    for chunk_id, name, heading, content in rows:
        header = " › ".join(x for x in (name, heading) if x)
        db.execute(
            "UPDATE chunks SET title_norm = ?, body_norm = ? WHERE id = ?",
            (fts_variants.normalize(variant, header), fts_variants.normalize(variant, content), chunk_id),
        )
    _write_build(db, {**build_info(db), "fts": variant, "derived_from": Path(source).stem})
    db.commit()
    db.close()


def fingerprint(db):
    """SHA-256 of what the index holds: chunks with their vectors and the full vectors (SC-001)."""
    digest = hashlib.sha256()
    query = (
        "SELECT d.content_hash, c.ordinal, c.heading_path, c.page, c.content, c.title_norm, c.body_norm, "
        "c.token_estimate, c.embedding, e.v768 FROM chunks c JOIN documents d ON d.id = c.document_id "
        "JOIN eval_full_embeddings e ON e.chunk_id = c.id ORDER BY d.content_hash, c.ordinal"
    )
    for row in db.execute(query):
        digest.update(repr(row).encode("utf-8"))
    digest.update(json.dumps(dict(db.execute("SELECT key, value FROM rag_meta")), sort_keys=True).encode("utf-8"))
    return digest.hexdigest()

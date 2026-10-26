"""Hybrid retrieval as F10 will do it in the app (F05 research R6, contracts/eval-cli.md E5).

Exact vector search and FTS5 with BM25 (title 3, body 1), 50 candidates each, fused with RRF (k = 60);
follow-up questions without an LLM: a short question, or one that starts with a connector, is searched
together with the previous question.
"""

import struct
from dataclasses import dataclass

import normalizer
from eval import fts_variants

CONNECTORS = ("y ", "¿y ", "pero ", "entonces", "¿y si", "y si ")
FOLLOW_UP_MAX_TOKENS = 8
FUSED_LIMIT = 30  # the report keeps the top 30 of the fusion for the selector


def is_follow_up(question):
    text = question.strip().lower()
    return len(normalizer.tokens(question)) < FOLLOW_UP_MAX_TOKENS or any(text.startswith(c) for c in CONNECTORS)


def search_text(question, previous, follow_up_rule):
    if follow_up_rule and previous and is_follow_up(question):
        return f"{previous} {question}"
    return question


def rrf(lists, k=60):
    """Reciprocal Rank Fusion; ties go to the better single rank, then to the smaller id."""
    scores, best = {}, {}
    for ranked in lists:
        for position, item in enumerate(ranked, start=1):
            scores[item] = scores.get(item, 0.0) + 1.0 / (k + position)
            best[item] = min(best.get(item, position), position)
    return sorted(scores, key=lambda item: (-scores[item], best[item], item))


@dataclass(frozen=True)
class RetrievalResult:
    search_text: str
    vector_ids: list
    fts_ids: list
    fused_ids: list


class Retriever:
    """[embed_query] gives the query vector already truncated and normalized to the index dimension."""

    def __init__(self, db, embed_query, fts_variant="v1"):
        self.db = db
        self.embed_query = embed_query
        self.fts_variant = fts_variant

    def vector(self, text, k):
        vector = self.embed_query(text)
        blob = struct.pack(f"<{len(vector)}f", *vector)
        rows = self.db.execute(
            "SELECT rowid, distance FROM vector_full_scan('chunks', 'embedding', ?, ?) ORDER BY distance", (blob, k)
        ).fetchall()
        return [row[0] for row in rows]

    def fts(self, text, k):
        query = fts_variants.query(self.fts_variant, text)
        if not query:
            return []
        rows = self.db.execute(
            "SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts, 3.0, 1.0) LIMIT ?", (query, k)
        ).fetchall()
        return [row[0] for row in rows]

    def retrieve(self, question, previous, config):
        text = search_text(question, previous, config.follow_up_rule)
        vector_ids = self.vector(text, config.candidates) if config.mode in ("vector", "hybrid") else []
        fts_ids = self.fts(text, config.candidates) if config.mode in ("fts", "hybrid") else []
        if config.mode == "vector":
            fused = vector_ids
        elif config.mode == "fts":
            fused = fts_ids
        else:
            fused = rrf([vector_ids, fts_ids], config.rrf_k)
        return RetrievalResult(text, vector_ids, fts_ids, fused)

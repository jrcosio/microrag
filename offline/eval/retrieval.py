"""Hybrid retrieval as F10 will do it in the app (F05 research R6, contracts/eval-cli.md E5).

Exact vector search and FTS5 with BM25 (title 3, body 1), 50 candidates each, fused with RRF (k = 60);
follow-up questions without an LLM: a short question, or one that starts with a connector, is searched
together with the previous question.

F10 order review (D-035), evaluation only: a trigram third list for the fusion and a rerank of the
head of the fusion by the lists' own scores (specs/010-hybrid-retrieval/contracts/eval-retrieval.md).
"""

import json
import struct
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

import normalizer
from eval import fts_variants

PARAMETERS = Path(__file__).resolve().parents[1] / "config" / "rag_parameters.json"
CONNECTORS = ("y ", "¿y ", "pero ", "entonces", "¿y si", "y si ")
FOLLOW_UP_MAX_TOKENS = 8
# The report keeps the top 30 of the fusion for the selector; shared with the app since F10 (D-050).
FUSED_LIMIT = json.loads(PARAMETERS.read_text(encoding="utf-8"))["fused_limit"]


def is_follow_up(question):
    text = question.strip().lower()
    return len(normalizer.tokens(question)) < FOLLOW_UP_MAX_TOKENS or any(text.startswith(c) for c in CONNECTORS)


def search_text(question, previous, follow_up_rule):
    if follow_up_rule and previous and is_follow_up(question):
        return f"{previous} {question}"
    return question


def trigram_query(text, max_terms=16):
    """FTS5 query for the trigram list: terms of 3 or more characters (a trigram needs them), lowercase,
    without accents or stopwords, distinct, at most [max_terms], quoted and joined with OR."""
    plain = unicodedata.normalize("NFD", text.lower())
    plain = "".join(c for c in plain if unicodedata.category(c) != "Mn")
    terms = [t for t in normalizer._scan(plain) if t not in normalizer.STOP and len(t) >= 3]  # noqa: SLF001
    return " OR ".join('"' + t.replace('"', '""') + '"' for t in list(dict.fromkeys(terms))[:max_terms])


def _min_max(values):
    if not values:
        return {}
    low, high = min(values.values()), max(values.values())
    return {item: (value - low) / (high - low) if high > low else 1.0 for item, value in values.items()}


def rerank(fused, vector_scores, text_scores, alpha, top):
    """The first [top] fused ids reordered by alpha * cosine similarity + (1 - alpha) * BM25 relevance, each
    min-max normalized over its list and 0 for an id the list lacks; ties and the rest keep the fusion order.
    [vector_scores] maps id -> cosine distance and [text_scores] id -> bm25() (lower is better)."""
    similarity = _min_max({item: 1.0 - distance for item, distance in vector_scores.items()})
    relevance = _min_max({item: -score for item, score in text_scores.items()})
    head = sorted(fused[:top], key=lambda item: -(alpha * similarity.get(item, 0.0) + (1 - alpha) * relevance.get(item, 0.0)))
    return head + list(fused[top:])


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
    trigram_ids: list = field(default_factory=list)


# The trigram table lives in an in-memory database attached to the connection: the index never changes (rule G3).
TRIGRAM_SCHEMA = "eval_trigram"


class Retriever:
    """[embed_query] gives the query vector already truncated and normalized to the index dimension."""

    def __init__(self, db, embed_query, fts_variant="v1"):
        self.db = db
        self.embed_query = embed_query
        self.fts_variant = fts_variant

    def vector_scored(self, text, k):
        """(id, cosine distance), best first."""
        vector = self.embed_query(text)
        blob = struct.pack(f"<{len(vector)}f", *vector)
        return self.db.execute(
            "SELECT rowid, distance FROM vector_full_scan('chunks', 'embedding', ?, ?) ORDER BY distance", (blob, k)
        ).fetchall()

    def vector(self, text, k):
        return [row[0] for row in self.vector_scored(text, k)]

    def fts_scored(self, text, k):
        """(id, bm25), best first (lower is better)."""
        query = fts_variants.query(self.fts_variant, text)
        if not query:
            return []
        return self.db.execute(
            "SELECT rowid, bm25(chunks_fts, 3.0, 1.0) AS score FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY score LIMIT ?",
            (query, k),
        ).fetchall()

    def fts(self, text, k):
        return [row[0] for row in self.fts_scored(text, k)]

    def trigram(self, text, k):
        """Ids by BM25 over a trigram FTS5 table of the chunks' content, built once per connection."""
        query = trigram_query(text)
        if not query:
            return []
        if TRIGRAM_SCHEMA not in {row[1] for row in self.db.execute("PRAGMA database_list")}:
            self.db.execute(f"ATTACH ':memory:' AS {TRIGRAM_SCHEMA}")
            self.db.execute(f"CREATE VIRTUAL TABLE {TRIGRAM_SCHEMA}.chunks_trigram USING fts5(content, "
                            "tokenize='trigram case_sensitive 0 remove_diacritics 1')")
            self.db.execute(f"INSERT INTO {TRIGRAM_SCHEMA}.chunks_trigram(rowid, content) SELECT id, content FROM main.chunks")
            self.db.commit()
        rows = self.db.execute(
            f"SELECT rowid FROM {TRIGRAM_SCHEMA}.chunks_trigram WHERE chunks_trigram MATCH ? "
            "ORDER BY bm25(chunks_trigram) LIMIT ?", (query, k)
        ).fetchall()
        return [row[0] for row in rows]

    def retrieve(self, question, previous, config):
        text = search_text(question, previous, config.follow_up_rule)
        vector = self.vector_scored(text, config.candidates) if config.mode in ("vector", "hybrid") else []
        fts = self.fts_scored(text, config.candidates) if config.mode in ("fts", "hybrid") else []
        vector_ids, fts_ids = [row[0] for row in vector], [row[0] for row in fts]
        trigram_ids = self.trigram(text, config.candidates) if config.third_list == "trigram" else []
        if config.mode == "vector":
            fused = vector_ids
        elif config.mode == "fts":
            fused = fts_ids
        else:
            fused = rrf([vector_ids, fts_ids] + ([trigram_ids] if config.third_list else []), config.rrf_k)
        if config.rerank_alpha is not None:
            fused = rerank(fused, dict(vector), dict(fts), config.rerank_alpha, config.rerank_top)
        return RetrievalResult(text, vector_ids, fts_ids, fused, trigram_ids)

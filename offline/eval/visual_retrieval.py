"""Page-level retrieval of F18 (research R10).

The page is the common unit of the three lists: the chunk vector and FTS lists of F05 (50 candidates each)
become page lists, each page at the best rank of its chunks, and the page-vector list ranks the pages by the
cosine of the 440M query vector. «Texto» fuses the two chunk lists, «Visual» is the page list and «Ambos» fuses
the three, all with the RRF of constitution III (k = 60).
"""

import json
import sqlite3
from pathlib import Path

from embedding_text import query_input, truncate_and_normalize
from eval import builds, metrics
from eval.retrieval import rrf
from eval.visual_testset import VISUAL_TYPES

MODES = ("texto", "visual", "ambos")
CANDIDATES = 50
RRF_K = 60
CUTOFFS = (1, 3, 5, 10)
QUERY_CACHE = builds.BUILD / "visual" / "query_embeddings.json"


def to_pages(chunk_ids, chunk_pages, chunk_doc):
    """(doc sha, page) in the order of [chunk_ids]; a page appears once, at its best chunk."""
    out, seen = [], set()
    for chunk_id in chunk_ids:
        for page in chunk_pages.get(chunk_id, []):
            key = (chunk_doc[chunk_id], page)
            if key not in seen:
                seen.add(key)
                out.append(key)
    return out


def rank_pages(query_vector, rows, k=CANDIDATES):
    """The [k] pages whose vector is closest to [query_vector] (both normalized: the dot product is the cosine)."""
    scored = sorted((-sum(a * b for a, b in zip(query_vector, row.vector)), index) for index, row in enumerate(rows))
    return [(rows[index].doc_sha, rows[index].page) for _, index in scored[:k]]


def fuse(mode, vector_pages, fts_pages, page_list, k=RRF_K):
    if mode == "texto":
        return rrf([vector_pages, fts_pages], k)
    if mode == "visual":
        return list(page_list)
    if mode == "ambos":
        return rrf([vector_pages, fts_pages, page_list], k)
    raise ValueError(f"unknown mode {mode}")


def page_metrics(ranked, relevant):
    relevant = set(relevant)
    out = {f"recall@{k}": metrics.recall_at(ranked, relevant, k) for k in CUTOFFS}
    out["mrr"] = metrics.reciprocal_rank(ranked, relevant)
    out["ndcg@10"] = metrics.ndcg_at(ranked, relevant, 10)
    return out


def summarize(rows):
    """Means per question type, for the visual types together and for all the answerable questions."""
    groups = {}
    for row in rows:
        groups.setdefault(row["type"], []).append(row["metrics"])
        if row["type"] in VISUAL_TYPES:
            groups.setdefault("visual", []).append(row["metrics"])
        groups.setdefault("all", []).append(row["metrics"])
    return {name: {**{key: sum(m[key] for m in items) / len(items) for key in items[0]}, "n": len(items)}
            for name, items in groups.items()}


class QueryEmbedder:
    """Raw 768 query vectors of the 440M on the CPU, cached: its text vectors are the 270M's (D-025, R5)."""

    def __init__(self):
        self.engine = None
        self.cache = json.loads(QUERY_CACHE.read_text()) if QUERY_CACHE.exists() else {}

    def raw(self, text):
        vector = self.cache.get(text)
        if vector is None:
            if self.engine is None:
                from litert_lm import Backend, EmbeddingEngine

                from eval import models

                self.engine = EmbeddingEngine(str(models.verify(models.VISUAL_EMBEDDING)), backend=Backend.CPU(),
                                              cache_dir=str(builds.BUILD / "litert-cache"), max_input_length=builds.MAX_INPUT_TOKENS)
            from litert_lm import EmbeddingOptions

            vector = list(self.engine.compute_embedding(query_input(text), EmbeddingOptions(normalize=False)).embedding)
            self.cache[text] = vector
        return vector

    def truncated(self, text, dims):
        return truncate_and_normalize(self.raw(text), dims)

    def save(self):
        QUERY_CACHE.parent.mkdir(parents=True, exist_ok=True)
        QUERY_CACHE.write_text(json.dumps(self.cache))


def chunk_documents(index_path):
    """{chunk id: document sha} of the text index."""
    db = sqlite3.connect(str(index_path))
    try:
        return dict(db.execute("SELECT c.id, d.content_hash FROM chunks c JOIN documents d ON d.id = c.document_id"))
    finally:
        db.close()


def write_json(path, data):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")

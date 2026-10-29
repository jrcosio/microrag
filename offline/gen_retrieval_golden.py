"""Writes golden/retrieval_golden.jsonl and golden/retrieval_parity.jsonl (F10 contracts/eval-retrieval.md).

retrieval_golden: for every live question of the test set, the follow-up rule, the search text, the
vector and FTS lists of the reference index, their RRF fusion and the blocks the context selector picks
with D-034 and with the report's starting values. RetrievalGoldenTest replays it with the app's port.

retrieval_parity: a fixed sample of 50 scored questions (40 answerable, 10 follow-ups) with the desktop
hit under D-034, for RetrievalParityTest on the phone.

Query vectors come from the F05 cache (build/query_embeddings.json); the model, checked by its hash, is
loaded only for a search text the cache lacks.

    offline/.venv/bin/python -I offline/gen_retrieval_golden.py [--check]
"""

import json
import random
import sys
from pathlib import Path

OFFLINE = Path(__file__).resolve().parent
sys.path.insert(0, str(OFFLINE))  # -I leaves the script directory out of sys.path

from embedding_text import query_input, truncate_and_normalize  # noqa: E402
from eval import builds, corpus, index, metrics  # noqa: E402
from eval.pipeline import _chunk_views, _neighbor, _pairs, live_questions, load_context, relevant_ids  # noqa: E402
from eval.retrieval import FUSED_LIMIT, PARAMETERS, Retriever, is_follow_up  # noqa: E402
from eval.runs import EvalConfig  # noqa: E402
from eval.selector import ContextSelector  # noqa: E402

GOLDEN = OFFLINE / "golden" / "retrieval_golden.jsonl"
PARITY = OFFLINE / "golden" / "retrieval_parity.jsonl"
CACHE = builds.BUILD / "query_embeddings.json"
# The report's starting values (blocks, per document, budget, neighbours), to exercise the neighbour expansion.
REPORT_CONFIG = (6, 3, 2200, 2)
PARITY_SEED = 20261029
PARITY_SIZES = {"answerable": 40, "followup": 10}


class CachedQueryEmbedder:
    """Query vectors from the F05 cache, truncated to the index dimension; the model only on a miss."""

    def __init__(self, dims):
        self.dims = dims
        self.cache = json.loads(CACHE.read_text()) if CACHE.exists() else {}
        self.model = None
        self.added = False

    def __call__(self, text):
        full = self.cache.get(text)
        if full is None:
            if self.model is None:
                self.model = builds.Embedder()  # models.verify checks the file hash first
            full = list(self.model(query_input(text)))
            self.cache[text] = full
            self.added = True
        return truncate_and_normalize(full, self.dims)

    def save(self):
        if self.added:
            CACHE.write_text(json.dumps(self.cache))


def configurations(params):
    return {
        "d034": (params["max_blocks"], params["max_per_document"], params["budget_tokens"], params["expand_top"]),
        "report": REPORT_CONFIG,
    }


def next_id(db, document_id, ordinal):
    row = db.execute("SELECT id FROM chunks WHERE document_id = ? AND ordinal = ?", (document_id, ordinal + 1)).fetchone()
    return row[0] if row else None


def describe(db, ids):
    """id → [document_id, ordinal, token_estimate, next_id] for [ids] and the chunk after each one."""
    views = _chunk_views(db, ids)
    nexts = {i: next_id(db, v.document_id, v.ordinal) for i, v in views.items()}
    views.update(_chunk_views(db, [n for n in nexts.values() if n is not None and n not in views]))
    return {i: [v.document_id, v.ordinal, v.token_estimate, nexts[i] if i in nexts else next_id(db, v.document_id, v.ordinal)]
            for i, v in sorted(views.items())}


def block_rows(blocks, chunks):
    by_position = {(doc, ordinal): i for i, (doc, ordinal, _, _) in chunks.items()}
    return [[b.document_id, b.first_ordinal, b.last_ordinal, b.tokens,
             [by_position[(b.document_id, o)] for o in range(b.first_ordinal, b.last_ordinal + 1)]] for b in blocks]


def build(ctx, embedder, params):
    config = EvalConfig(follow_up_rule=params["follow_up_rule"], candidates=params["candidates"], rrf_k=params["rrf_k"])
    retriever = Retriever(ctx.db, embedder, index.build_info(ctx.db).get("fts", "v1"))
    selectors = {name: ContextSelector(*values) for name, values in configurations(params).items()}
    neighbor = _neighbor(ctx.db)
    questions = live_questions()
    by_id = {q.id: q for q in questions}
    golden, hits = [], {}
    for q in sorted(questions, key=lambda q: q.id):
        previous = by_id[q.previous].question if q.previous in by_id else None
        result = retriever.retrieve(q.question, previous, config)
        fused = result.fused_ids[:FUSED_LIMIT]
        views = _chunk_views(ctx.db, fused)
        ranked = [views[i] for i in fused if i in views]
        blocks = {name: selector.select(ranked, neighbor) for name, selector in selectors.items()}
        chunks = describe(ctx.db, fused)
        golden.append({
            "id": q.id, "question": q.question, "previous": previous, "follow_up": is_follow_up(q.question),
            "search_text": result.search_text, "vector": result.vector_ids, "fts": result.fts_ids, "fused": fused,
            "chunks": {str(i): row for i, row in chunks.items()},
            "blocks": {name: block_rows(b, chunks) for name, b in blocks.items()},
        })
        relevant = relevant_ids(ctx, q)
        if q.type != "unanswerable" and relevant:
            hits[q.id] = int(metrics.blocks_hit(blocks["d034"], _pairs(ctx, relevant)))
    return golden, hits


def parity_sample(hits):
    """Fixed sample of scored questions (rule G4), in id order, with the desktop hit under D-034."""
    questions = live_questions()
    by_id = {q.id: q for q in questions}
    files = {d.id: d.file_name for d in corpus.load_manifest()}
    rng = random.Random(PARITY_SEED)
    picked = []
    for kind, size in PARITY_SIZES.items():
        pool = sorted((q for q in questions if q.type == kind and q.id in hits), key=lambda q: q.id)
        picked += rng.sample(pool, size)
    return [{
        "id": q.id, "type": q.type, "question": q.question,
        "previous": by_id[q.previous].question if q.previous in by_id else None,
        "doc": q.doc, "file": files[q.doc], "evidence": q.evidence, "desktop_hit": hits[q.id],
    } for q in sorted(picked, key=lambda q: q.id)]


def render(rows):
    return "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows)


def main(argv=None):
    check = "--check" in (sys.argv[1:] if argv is None else argv)
    params = json.loads(PARAMETERS.read_text(encoding="utf-8"))
    ctx = load_context("ref")
    embedder = CachedQueryEmbedder(index.build_info(ctx.db)["dims"])
    golden, hits = build(ctx, embedder, params)
    outputs = {GOLDEN: render(golden), PARITY: render(parity_sample(hits))}
    embedder.save()
    ctx.db.close()
    if check:
        stale = [path.name for path, text in outputs.items() if not path.exists() or path.read_text(encoding="utf-8") != text]
        if stale:
            raise SystemExit(f"out of date: {', '.join(stale)}")
        print("retrieval golden and parity sample up to date")
        return
    for path, text in outputs.items():
        path.write_text(text, encoding="utf-8", newline="\n")
        print(f"wrote {text.count(chr(10))} rows -> {path}")
    scored = len(hits)
    print(f"desktop Recall@bloques (D-034): {sum(hits.values()) / scored:.3f} over {scored} questions")


if __name__ == "__main__":
    main()

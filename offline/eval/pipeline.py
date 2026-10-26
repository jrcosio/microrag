"""Evaluation steps behind the CLI: sampling, test set checks, retrieval and generation runs.

F05 contracts/eval-cli.md. Results go to offline/build/runs/<fingerprint>/ (data-model §6).
"""

import dataclasses
import hashlib
import json
import math
import random
import struct
import time
from collections import defaultdict

from embedding_text import query_input, truncate_and_normalize
from eval import builds, corpus, index, judge, metrics, models, testset
from eval.extract import document_text
from eval.generate import Item, open_engine
from eval.generate import run as run_generation
from eval.prompt import build_prompt
from eval.retrieval import Retriever
from eval.runs import EvalConfig, run_dir
from eval.selector import ChunkView, ContextSelector

SAMPLE = builds.BUILD / "sample.jsonl"
SAMPLE_SIZE = 320
SAMPLE_SEED = 20261026
HEADER_TOKENS_PER_BLOCK = 15  # "[n] documento › ruta (p. N)" line
QUESTION_TOKENS = 40
BUDGET_MARGIN = 0.95          # token_estimate is an estimate (research R6)


# --- context ---------------------------------------------------------------------------------------

@dataclasses.dataclass
class Context:
    variant: str
    db: object
    docs: list
    doc_sha: dict
    doc_texts: dict
    titles: dict
    chunks_by_sha: dict
    doc_id_by_sha: dict


def load_context(variant="ref"):
    path = builds.index_path(variant)
    if not path.exists():
        raise SystemExit(f"index {variant} is missing: run_eval.py build {variant}")
    db = index.connect(path)
    docs = corpus.load_manifest()
    chunks = defaultdict(list)
    for sha, ordinal, content in db.execute(
            "SELECT d.content_hash, c.ordinal, c.content FROM chunks c JOIN documents d ON d.id = c.document_id ORDER BY c.id"):
        chunks[sha].append((ordinal, content))
    return Context(
        variant=variant, db=db, docs=docs, doc_sha={d.id: d.sha256 for d in docs},
        doc_texts={d.id: document_text(builds.extracted(d)) for d in docs}, titles={d.id: d.title for d in docs},
        chunks_by_sha=dict(chunks),
        doc_id_by_sha=dict(db.execute("SELECT content_hash, id FROM documents").fetchall()),
    )


# --- sampling and test set -------------------------------------------------------------------------

def _looks_like_toc(content):
    return content.count(".....") >= 2 or content.count(".") > len(content) * 0.2


def sample_chunks(ctx, size=SAMPLE_SIZE, seed=SAMPLE_SEED):
    """Chunks to write questions from: stratified by document, capped per document (rule T9)."""
    rows = ctx.db.execute(
        "SELECT d.content_hash, c.ordinal, c.heading_path, c.page, c.content FROM chunks c "
        "JOIN documents d ON d.id = c.document_id ORDER BY c.id").fetchall()
    by_doc = defaultdict(list)
    sha_to_doc = {sha: doc for doc, sha in ctx.doc_sha.items()}
    for sha, ordinal, heading, page, content in rows:
        if len(content) >= 300 and not _looks_like_toc(content):
            by_doc[sha_to_doc[sha]].append({"doc": sha_to_doc[sha], "ordinal": ordinal, "heading_path": heading,
                                            "page": page, "content": content})
    total = sum(len(v) for v in by_doc.values())
    cap = math.floor(size * 0.13)
    rng = random.Random(seed)
    picked = []
    for doc in sorted(by_doc):
        quota = min(cap, max(4, round(size * len(by_doc[doc]) / total)), len(by_doc[doc]))
        picked += sorted(rng.sample(by_doc[doc], quota), key=lambda r: r["ordinal"])
    return picked


def write_sample(ctx):
    rows = sample_chunks(ctx)
    for row in rows:
        row["title"] = ctx.titles[row["doc"]]
    SAMPLE.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
    return rows


def validate_testset(ctx, require_review=True):
    questions = testset.with_relevants(testset.load(), ctx.doc_sha, ctx.chunks_by_sha)
    errors = testset.validate(questions, ctx.doc_texts, ctx.doc_sha, ctx.chunks_by_sha,
                              min_reviewed=testset.MIN_REVIEWED if require_review else 0)
    return questions, errors


# --- retrieval -------------------------------------------------------------------------------------

def budget_tokens(config, chars_per_token):
    """Context budget so that the whole prompt fits config.prompt_limit_tokens (research R6)."""
    if config.budget_tokens is not None:
        return config.budget_tokens
    template = len(build_prompt("", [], None))
    overhead = math.ceil(template / chars_per_token) + config.max_blocks * HEADER_TOKENS_PER_BLOCK + QUESTION_TOKENS
    return int((config.prompt_limit_tokens - overhead) * BUDGET_MARGIN)


class QueryEmbedder:
    """EmbeddingGemma 2 on CPU for queries (D-024), truncated to the index dimension, with a cache."""

    def __init__(self, dims):
        self.dims = dims
        self.engine, self.embed = builds.embedder()
        self.cache_path = builds.BUILD / "query_embeddings.json"
        self.cache = json.loads(self.cache_path.read_text()) if self.cache_path.exists() else {}

    def __call__(self, text):
        full = self.cache.get(text)
        if full is None:
            full = list(self.embed(query_input(text)))
            self.cache[text] = full
        return truncate_and_normalize(full, self.dims)

    def save(self):
        self.cache_path.write_text(json.dumps(self.cache))


def _chunk_views(db, ids):
    if not ids:
        return {}
    marks = ",".join("?" * len(ids))
    rows = db.execute(
        "SELECT c.id, c.document_id, c.ordinal, c.heading_path, c.page, c.content, c.token_estimate, d.display_name "
        f"FROM chunks c JOIN documents d ON d.id = c.document_id WHERE c.id IN ({marks})", list(ids)).fetchall()
    return {row[0]: ChunkView(*row) for row in rows}


def _neighbor(db):
    def load(document_id, ordinal):
        row = db.execute("SELECT id FROM chunks WHERE document_id = ? AND ordinal = ?", (document_id, ordinal)).fetchone()
        return _chunk_views(db, [row[0]])[row[0]] if row else None
    return load


def fingerprints(ctx, config):
    db = ctx.db
    index_fp = index.fingerprint(db) + json.dumps(index.build_info(db), sort_keys=True)
    testset_fp = hashlib.sha256(testset.TESTSET.read_bytes()).hexdigest() if testset.TESTSET.exists() else ""
    model_ids = [models.EMBEDDING] + ([models.LLMS[config.llm]] if config.llm else [])
    models_fp = "|".join(models.spec(m).sha256 for m in model_ids)
    return hashlib.sha256(index_fp.encode()).hexdigest(), testset_fp, models_fp


def relevant_ids(ctx, question):
    """Relevant chunk ids of [question] in this index, derived from its evidence."""
    sha = ctx.doc_sha.get(question.doc)
    if not question.has_answer or sha is None:
        return set()
    ordinals = testset.derive_relevant(question.evidence, ctx.chunks_by_sha.get(sha, []))
    document_id = ctx.doc_id_by_sha[sha]
    rows = ctx.db.execute(
        f"SELECT id FROM chunks WHERE document_id = ? AND ordinal IN ({','.join('?' * len(ordinals))})",
        [document_id, *ordinals]).fetchall() if ordinals else []
    return {row[0] for row in rows}


def live_questions():
    return [q for q in testset.load() if q.review != "discarded"]


def retrieve(config, ctx=None):
    ctx = ctx or load_context(config.build)
    info = index.build_info(ctx.db)
    fts_variant = info.get("fts", "v1")
    embedder = QueryEmbedder(info["dims"])
    retriever = Retriever(ctx.db, embedder, fts_variant)
    selector = ContextSelector(config.max_blocks, config.max_per_document, budget_tokens(config, info["chars_per_token"]),
                               2 if config.neighbors else 0)
    questions = live_questions()
    by_id = {q.id: q for q in questions}
    fingerprint = EvalConfig.fingerprint(config, *fingerprints(ctx, config))
    folder = run_dir(fingerprint)
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "config.json").write_text(json.dumps({"config": dataclasses.asdict(config), "fingerprint": fingerprint,
                                                    "budget_tokens": selector.budget_tokens}, indent=1))
    started, rows = time.time(), []
    neighbor = _neighbor(ctx.db)
    for q in questions:
        previous = by_id.get(q.previous).question if q.previous in by_id else None
        result = retriever.retrieve(q.question, previous, config)
        candidates = result.fused_ids[:30]
        views = _chunk_views(ctx.db, candidates)
        blocks = selector.select([views[i] for i in candidates if i in views], neighbor)
        relevant = relevant_ids(ctx, q)
        rows.append({
            "id": q.id, "type": q.type, "search_text": result.search_text, "vector": result.vector_ids,
            "fts": result.fts_ids, "fused": result.fused_ids, "relevant": sorted(relevant),
            "blocks": [dataclasses.asdict(b) for b in blocks],
        })
    embedder.save()
    seconds = round(time.time() - started, 1)
    with (folder / "retrieval.jsonl").open("w", encoding="utf-8") as out:
        for row in rows:
            out.write(json.dumps(row, ensure_ascii=False) + "\n")
    result = {"config": dataclasses.asdict(config), "fingerprint": fingerprint, "seconds": seconds,
              "budget_tokens": selector.budget_tokens, **retrieval_metrics(ctx, rows)}
    (folder / "metrics.json").write_text(json.dumps(result, indent=1))
    return result


def _pairs(ctx, ids):
    if not ids:
        return set()
    rows = ctx.db.execute(f"SELECT document_id, ordinal FROM chunks WHERE id IN ({','.join('?' * len(ids))})", list(ids)).fetchall()
    return set(rows)


def retrieval_metrics(ctx, rows):
    from eval.selector import ContextBlock

    scored = [r for r in rows if r["type"] != "unanswerable" and r["relevant"]]
    out = {"questions": len(scored), "no_relevant": sum(1 for r in rows if r["type"] != "unanswerable" and not r["relevant"])}
    for name in ("vector", "fts", "fused"):
        label = "hybrid" if name == "fused" else name
        if not any(r[name] for r in scored):
            continue  # this list was not used by the retrieval mode
        lists = [(r[name], set(r["relevant"])) for r in scored]
        out[f"{label}_recall@20"] = metrics.mean(metrics.recall_at(l, rel, 20) for l, rel in lists)
        out[f"{label}_mrr"] = metrics.mean(metrics.reciprocal_rank(l, rel) for l, rel in lists)
        out[f"{label}_ndcg@10"] = metrics.mean(metrics.ndcg_at(l, rel, 10) for l, rel in lists)
    out["blocks_recall"] = metrics.mean(
        metrics.blocks_hit([ContextBlock(**b) for b in r["blocks"]], _pairs(ctx, r["relevant"])) for r in scored)
    follow = [r for r in scored if r["type"] == "followup"]
    if follow:
        out["followup_recall@20"] = metrics.mean(metrics.recall_at(r["fused"], set(r["relevant"]), 20) for r in follow)
    out["blocks_mean"] = metrics.mean(len(r["blocks"]) for r in rows)
    out["context_tokens_mean"] = metrics.mean(sum(b["tokens"] for b in r["blocks"]) for r in rows)
    return out


# --- generation ------------------------------------------------------------------------------------

def generate(config, backend="gpu", ids=None, out_name="answers.jsonl"):
    """Generates and judges the answers of a retrieval run (the same config without the LLM)."""
    ctx = load_context(config.build)
    retrieval_config = dataclasses.replace(config, llm=None)
    retrieval_fp = EvalConfig.fingerprint(retrieval_config, *fingerprints(ctx, retrieval_config))
    retrieval_path = run_dir(retrieval_fp) / "retrieval.jsonl"
    if not retrieval_path.exists():
        retrieve(retrieval_config, ctx)
    rows = {json.loads(line)["id"]: json.loads(line) for line in retrieval_path.read_text(encoding="utf-8").splitlines()}
    questions = [q for q in live_questions() if ids is None or q.id in ids]
    # Previous turns go first, so follow-ups see the generated previous answer.
    questions.sort(key=lambda q: (q.type == "followup", q.id))
    by_id = {q.id: q for q in live_questions()}
    fingerprint = EvalConfig.fingerprint(config, *fingerprints(ctx, config))
    folder = run_dir(fingerprint)
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "config.json").write_text(json.dumps({"config": dataclasses.asdict(config), "fingerprint": fingerprint,
                                                    "retrieval": retrieval_fp, "backend": backend}, indent=1))
    out_path = folder / out_name
    answers = {}
    if out_path.exists():
        answers = {json.loads(l)["id"]: json.loads(l)["answer"] for l in out_path.read_text(encoding="utf-8").splitlines()}

    from eval.selector import ContextBlock

    prepared = [(q, [ContextBlock(**b) for b in rows[q.id]["blocks"]], _pairs(ctx, rows[q.id]["relevant"])) for q in questions]
    lookup = {q.id: (q, blocks, pairs) for q, blocks, pairs in prepared}

    def on_answer(item, answer):
        q, blocks, pairs = lookup[item.id]
        verdict = judge.judge_answer(q.type, answer, q.key_facts, blocks, pairs)
        answers[item.id] = answer
        return {"type": q.type, "judge": dataclasses.asdict(verdict)}

    model_path = models.verify(models.LLMS[config.llm])
    started = time.time()
    generated = 0
    with open_engine(model_path, backend, builds.BUILD / "litert-cache") as engine:
        # One item at a time: a follow-up uses the previous answer generated earlier in this same run.
        for q, blocks, _ in prepared:
            previous = None
            if q.previous in by_id:
                previous = (by_id[q.previous].question, answers.get(q.previous, by_id[q.previous].expected_answer or ""))
            item = Item(q.id, build_prompt(q.question, blocks, previous))
            generated += run_generation(engine, [item], out_path, config.max_output_tokens, on_answer)
    seconds = round(time.time() - started, 1)
    return generation_metrics(config, fingerprint, out_path, seconds, generated)


def generation_metrics(config, fingerprint, out_path, seconds, generated):
    lines = [json.loads(l) for l in out_path.read_text(encoding="utf-8").splitlines()]
    verdicts = [judge.Judgement(**l["judge"]) for l in lines]
    summary = judge.summarize([l["type"] for l in lines], verdicts)
    tokens = [l["prompt_tokens"] for l in lines]
    result = {"config": dataclasses.asdict(config), "fingerprint": fingerprint, "generated_now": generated,
              "seconds": seconds, "answers": len(lines), **summary,
              "prompt_tokens_median": metrics.percentile(tokens, 50), "prompt_tokens_p95": metrics.percentile(tokens, 95),
              "answer_seconds_mean": metrics.mean(l["seconds"] for l in lines)}
    (out_path.parent / f"metrics_{out_path.stem}.json").write_text(json.dumps(result, indent=1))
    return result


# --- ablations -------------------------------------------------------------------------------------

ABLATIONS_FILE = builds.BUILD / "ablations.json"


def retrieval_ablations(base=None):
    """FR-012: each variant changes one parameter of the reference (contracts/eval-cli.md)."""
    base = base or EvalConfig()
    variants = [
        ("referencia", base),
        ("solo vector", dataclasses.replace(base, mode="vector")),
        ("solo FTS", dataclasses.replace(base, mode="fts")),
        ("FTS sin normalizar", dataclasses.replace(base, build="fts_raw")),
        ("FTS solo palabras vacías", dataclasses.replace(base, build="fts_stop")),
        ("128 dimensiones", dataclasses.replace(base, build="dim128")),
        ("512 dimensiones", dataclasses.replace(base, build="dim512")),
        ("768 dimensiones", dataclasses.replace(base, build="dim768")),
        ("sin cabecera contextual", dataclasses.replace(base, build="nohdr")),
        ("fragmentos de 250 tokens", dataclasses.replace(base, build="chunk250")),
        ("fragmentos de 500 tokens", dataclasses.replace(base, build="chunk500")),
        ("sin regla de seguimiento", dataclasses.replace(base, follow_up_rule=False)),
    ]
    for blocks in (4, 6, 8):
        for neighbors in (True, False):
            if (blocks, neighbors) != (base.max_blocks, base.neighbors):
                label = f"{blocks} bloques{'' if neighbors else ' sin vecinos'}"
                variants.append((label, dataclasses.replace(base, max_blocks=blocks, neighbors=neighbors)))
    return variants


def run_ablations(variants, key="retrieval"):
    stored = json.loads(ABLATIONS_FILE.read_text()) if ABLATIONS_FILE.exists() else {}
    results = []
    for label, config in variants:
        result = retrieve(config) if config.llm is None else generate(config)
        results.append({"label": label, **result})
        print(f"{label}: {json.dumps({k: v for k, v in result.items() if k not in ('config',)}, ensure_ascii=False)}")
    stored[key] = results
    ABLATIONS_FILE.write_text(json.dumps(stored, ensure_ascii=False, indent=1))
    return results

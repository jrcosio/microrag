"""F14 page follow-ups (research R10, contract G8, SC-007): which page a follow-up gets with each variant of the rule.

F20 searches the pages of a follow-up with the vector of the previous question joined with the new one, so a short
question about another document can drag the previous page (D-087). For each pair of testset/visual/followups.jsonl
the top page (the one that enters the answer) is compared with the expected page:
- joined: F20's vector, `previous question + question`;
- new-only: the new question alone;
- best-of-both: both searches, each page keeping its closest.

The text search of a follow-up keeps the joined text in every variant, so its Recall@20 does not change by
construction. The rule is f14_rules.json's `page_follow_up`, fixed before evaluating.
"""

import json
from pathlib import Path

from eval import builds
from eval.retrieval import search_text

FOLLOWUPS = builds.OFFLINE / "testset" / "visual" / "followups.jsonl"
VARIANTS = ("joined", "new-only", "best-of-both")
RESULT = builds.BUILD / "f14" / "visual-followup.json"


def load_pairs(path=FOLLOWUPS):
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def expected(pair):
    pages = {(pair["expected_page"]["doc"], pair["expected_page"]["page"])}
    pages |= {(p["doc"], p["page"]) for p in pair.get("also_pages", [])}
    return pages


def top_page(vectors, rows):
    """The page closest to any of [vectors] (each page keeps its best similarity): the one that enters the answer."""
    best, best_key = None, None
    for row in rows:
        similarity = max(sum(a * b for a, b in zip(v, row.vector)) for v in vectors)
        key = (row.doc_id, row.page)
        if best is None or similarity > best or (similarity == best and key < best_key):
            best, best_key = similarity, key
    return best_key


def vectors_of(variant, pair, embed):
    joined = search_text(pair["second"], pair["first"], True)
    if variant == "joined":
        return [embed(joined)]
    if variant == "new-only":
        return [embed(pair["second"])]
    return [embed(joined), embed(pair["second"])]


def evaluate(pairs, rows, embed):
    """Per variant: the hits of the switch pairs and of the true follow-ups, and each pair's top page."""
    out = {}
    for variant in VARIANTS:
        per_pair = []
        for pair in pairs:
            page = top_page(vectors_of(variant, pair, embed), rows)
            per_pair.append({"id": pair["id"], "kind": pair["kind"], "page": list(page), "hit": page in expected(pair)})
        kinds = {kind: [p["hit"] for p in per_pair if p["kind"] == kind] for kind in ("switch", "follow")}
        out[variant] = {
            "switch_hit": sum(kinds["switch"]) / len(kinds["switch"]) if kinds["switch"] else None,
            "follow_hit": sum(kinds["follow"]) / len(kinds["follow"]) if kinds["follow"] else None,
            "pairs": per_pair,
        }
    return out


def decide(results, rule):
    """The variants that meet the rule, and the winner: the most true follow-ups among them, JOINED on a tie."""
    base_follow = results["joined"]["follow_hit"]
    passing = [v for v in VARIANTS if results[v]["switch_hit"] is not None
               and results[v]["switch_hit"] >= rule["min_switch_hit"] - 1e-9
               and results[v]["follow_hit"] >= base_follow - rule["max_follow_drop_points"] / 100 - 1e-9]
    winner = max(passing, key=lambda v: (results[v]["follow_hit"], v == "joined"), default=None)
    return passing, winner


def run(rule):
    """The review on the F18 visual corpus at the app's parameters (768 px, 140 tokens, 256D), with the 440M."""
    from eval import visual_index
    from eval.visual_retrieval import QueryEmbedder

    rows = visual_index.load_pages(visual_index.pages_path(768, 140), 256)
    embedder = QueryEmbedder()
    try:
        results = evaluate(load_pairs(), rows, lambda text: embedder.truncated(text, 256))
    finally:
        embedder.save()
    passing, winner = decide(results, rule)
    summary = {v: {k: results[v][k] for k in ("switch_hit", "follow_hit")} for v in VARIANTS}
    out = {"rule": rule, "summary": summary, "passing": passing, "winner": winner, "results": results}
    RESULT.parent.mkdir(parents=True, exist_ok=True)
    RESULT.write_text(json.dumps(out, ensure_ascii=False, indent=1))
    return out

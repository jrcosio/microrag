"""The frozen go rule of F18 and the parameter choice (D-073, D-075, research R12).

config/visual_rules.json is committed before any ablation. require_frozen() refuses to run when the
test set's hash is missing or different, or when the file differs from its version in HEAD; every run
stores the file's git hash, so a rule edited after seeing results cannot pass unnoticed (FR-025).
"""

import hashlib
import json
import subprocess
from pathlib import Path

OFFLINE = Path(__file__).resolve().parents[1]
RULES = OFFLINE / "config" / "visual_rules.json"


def load(path=RULES):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def file_sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def _git(path, *args):
    folder = Path(path).resolve().parent
    result = subprocess.run(["git", "-C", str(folder), *args], capture_output=True, text=True)
    return result.stdout.strip() if result.returncode == 0 else None


def git_hash(path):
    return _git(path, "hash-object", str(Path(path).resolve()))


def head_hash(path):
    """Blob hash of [path] in HEAD, or None if it is not committed."""
    top = _git(path, "rev-parse", "--show-toplevel")
    if top is None:
        return None
    relative = Path(path).resolve().relative_to(Path(top).resolve()).as_posix()
    return _git(path, "rev-parse", f"HEAD:{relative}")


def freeze(rules_path, testset_path):
    """Writes the test set's hash once; another set after that is refused."""
    data = load(rules_path)
    digest = file_sha256(testset_path)
    if data["testset_sha256"] not in (None, digest):
        raise SystemExit("visual_rules.json already freezes another test set; it cannot change (FR-025)")
    data["testset_sha256"] = digest
    Path(rules_path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return digest


def require_frozen(rules_path, testset_path):
    """The stamp every ablation run stores; refuses when the rule or the set is not frozen as committed."""
    data = load(rules_path)
    if data["testset_sha256"] is None:
        raise SystemExit("run `run_eval.py visual-freeze` and commit visual_rules.json before any ablation")
    if data["testset_sha256"] != file_sha256(testset_path):
        raise SystemExit("the visual test set changed after it was frozen (FR-025)")
    current, committed = git_hash(rules_path), head_hash(rules_path)
    if committed is None or current != committed:
        raise SystemExit("visual_rules.json differs from HEAD: commit the rule before running, never after (FR-025)")
    return {"rules_git_hash": current, "testset_sha256": data["testset_sha256"]}


def e2e_ms(rules, ttft_p95_ms, query_440_p95_ms, query_270_p95_ms):
    """Composed end-to-end first token (research R4): F17's retrieval, the page list's bound, the 440M's extra
    query time (never negative) and the first token."""
    formula = rules["e2e_formula"]
    return (formula["retrieval_p95_ms"] + formula["page_search_bound_ms"]
            + max(0.0, query_440_p95_ms - query_270_p95_ms) + ttft_p95_ms)


def choose_retrieval(rows, rules):
    """Resolution, tokens and dimension by the visual Recall@5 of the rule's mode, preferring the cheaper within the margin."""
    selection = rules["selection"]
    metric, mode = selection["retrieval"]["metric"], selection["retrieval"]["mode"]
    dimension = selection["dimension"]
    by_config = {}
    for row in rows:
        if row["mode"] == mode:
            by_config.setdefault((row["px"], row["tokens"]), {})[row["dim"]] = row[metric]
    scored = []
    for (px, tokens), dims in by_config.items():
        base = dims.get(dimension["default"])
        alternative = dims.get(dimension["alternative"])
        if base is None:
            continue
        if alternative is not None and alternative - base >= dimension["min_gain"]:
            scored.append((alternative, px, tokens, dimension["alternative"]))
        else:
            scored.append((base, px, tokens, dimension["default"]))
    best = max(score for score, *_ in scored)
    close = [entry for entry in scored if entry[0] >= best - selection["retrieval"]["cheaper_within"]]
    _, px, tokens, dim = min(close, key=lambda entry: (entry[1], entry[2], -entry[0]))
    return {"px": px, "tokens": tokens, "dim": dim}


def _mix(answers, rules):
    family = rules["selection"]["answer"]["family"]
    return [row for row in answers if row["answer"] == family]


def candidates(answers, stage_a, queries, rules):
    """Up to N mix variants by desktop visual quality whose composed stage-A first token fits, with margin."""
    limit = rules["go_rule"]["e2e_p95_ms"] * rules["stage_a"]["margin"]
    fitting = [row for row in _mix(answers, rules)
               if row["variant"] in stage_a and e2e_ms(rules, stage_a[row["variant"]], *queries) <= limit]
    fitting.sort(key=lambda row: (-row["visual_correct"], stage_a[row["variant"]]))
    return [row["variant"] for row in fitting[: rules["stage_b"]["max_candidates"]]]


def choose_final(answers, stage_b, queries, rules):
    """The best mix variant whose composed stage-B p95 fits; within the margin, the faster one. None if none fits."""
    limit = rules["go_rule"]["e2e_p95_ms"]
    fitting = []
    for row in _mix(answers, rules):
        if row["variant"] in stage_b:
            total = e2e_ms(rules, stage_b[row["variant"]], *queries)
            if total <= limit:
                fitting.append((row["visual_correct"], total, row["variant"]))
    if not fitting:
        return None
    best = max(quality for quality, *_ in fitting)
    close = [entry for entry in fitting if entry[0] >= best - rules["selection"]["answer"]["cheaper_within_points"]]
    quality, total, variant = min(close, key=lambda entry: (entry[1], -entry[0]))
    return {"variant": variant, "e2e_ms": round(total, 1), "visual_correct": quality}


def decide(rules, final, decisive, golden):
    """The go rule on the decisive manual figures; the user decides when no page fits or the 440M fails."""
    rule = rules["go_rule"]
    golden_ok = golden["golden_min"] >= rule["golden_min_cosine"] and golden["identity_min"] >= rule["identity_min_cosine"]
    gain = decisive["visual"]["ambos"] - decisive["visual"]["texto"]
    loss = decisive["f05"]["texto"] - decisive["f05"]["ambos"]
    conditions = {
        "visual_gain": {"points": round(gain, 1), "ok": gain >= rule["visual_gain_points"]},
        "text_loss": {"points": round(loss, 1), "ok": loss <= rule["text_loss_points"]},
        "first_token": {"e2e_ms": None if final is None else final["e2e_ms"], "ok": final is not None},
        "embedder": {**golden, "ok": golden_ok},
    }
    needs_user = final is None or not golden_ok
    return {"go": all(item["ok"] for item in conditions.values()), "needs_user": needs_user,
            "variant": None if final is None else final["variant"], "conditions": conditions}

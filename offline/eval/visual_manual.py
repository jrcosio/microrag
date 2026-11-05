"""The manual judgement of the visual answers (F18 research R11, D-033, D-075).

Two samples share `reports/visual-manual-judgement.csv`:
- «comprobacion»: the same 30 questions for each main variant («Texto» and the best of «Páginas» and «Mezcla» by
  the automatic judge), to check that judge;
- «decisiva»: every accepted question for «Texto» and for the chosen configuration; its figures decide the rule.

A verdict stays while its answer does not change, so the review can be done in several sittings.
"""

import csv
import random
from pathlib import Path

from eval.visual_testset import VISUAL_TYPES

OFFLINE = Path(__file__).resolve().parents[1]
REVIEW = OFFLINE / "reports" / "visual-manual-judgement.csv"
FIELDS = ["muestra", "variante", "id", "tipo", "pregunta", "respuesta_esperada", "datos_clave", "fuentes",
          "respuesta_modelo", "regla", "regla_citas", "manual", "manual_citas", "comentario"]
VERDICTS = ("correcta", "parcial", "incorrecta")
CHECK, DECISIVE = "comprobacion", "decisiva"


def main_variants(metrics):
    """«Texto» and the best complete «Páginas» and «Mezcla» by visual correct answers; [metrics] is {variant: metrics.json}.

    A tie goes to the variant listed first, which is the cheaper one in the default order."""
    chosen = ["texto"] if metrics.get("texto", {}).get("complete") else []
    for family in ("paginas", "mezcla"):
        complete = [(key, m) for key, m in metrics.items() if key.startswith(family + "-") and m.get("complete")]
        if complete:
            chosen.append(max(complete, key=lambda item: (item[1]["summary"]["visual"]["correct"], -list(metrics).index(item[0])))[0])
    return chosen


def sample_ids(ids, size, seed):
    """The same [size] question ids for every variant, reproducible by [seed]."""
    ids = sorted(ids)
    return sorted(random.Random(seed).sample(ids, min(size, len(ids))))


def row(kind, variant, question, answer, judgement, sources):
    return {
        "muestra": kind, "variante": variant, "id": question.id, "tipo": question.type, "pregunta": question.question,
        "respuesta_esperada": question.expected_answer, "datos_clave": " | ".join(question.key_facts),
        "fuentes": " || ".join(f"[{n}] {s['label']}" for n, s in enumerate(sources, start=1)), "respuesta_modelo": answer,
        "regla": "correcta" if judgement["correct"] else "incorrecta",
        "regla_citas": f"{judgement['citations_ok']}/{judgement['citations_total']}",
        "manual": "", "manual_citas": "", "comentario": "",
    }


def load(path=REVIEW):
    return list(csv.DictReader(Path(path).open(encoding="utf-8-sig"))) if Path(path).exists() else []


def export(rows, path=REVIEW):
    """Writes [rows], keeping the verdicts already given to the same answer; rows of other samples or variants stay."""
    existing = {(r["muestra"], r["variante"], r["id"]): r for r in load(path)}
    fresh = {(r["muestra"], r["variante"], r["id"]) for r in rows}
    out = [r for key, r in existing.items() if key not in fresh]
    for r in rows:
        old = existing.get((r["muestra"], r["variante"], r["id"]))
        if old and old["respuesta_modelo"] == r["respuesta_modelo"]:
            r = {**r, "manual": old["manual"], "manual_citas": old["manual_citas"], "comentario": old["comentario"]}
        out.append(r)
    out.sort(key=lambda r: (r["muestra"], r["variante"], r["id"]))
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with Path(path).open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(out)
    return out


def _check(rows):
    for r in rows:
        if r["manual"] and r["manual"] not in VERDICTS:
            raise SystemExit(f"{r['muestra']} {r['variante']} {r['id']}: unknown verdict {r['manual']!r}; use {', '.join(VERDICTS)}")


def agreement(rows):
    """Per variant of the check sample: judged answers and the share where the rule and the manual verdict agree."""
    _check(rows)
    out = {}
    for r in rows:
        if r["muestra"] != CHECK or not r["manual"]:
            continue
        entry = out.setdefault(r["variante"], {"judged": 0, "agree": 0})
        entry["judged"] += 1
        entry["agree"] += r["regla"] == ("correcta" if r["manual"] == "correcta" else "incorrecta")
    return {v: {**e, "share": e["agree"] / e["judged"]} for v, e in out.items()}


def figures(rows, variant, kind=DECISIVE):
    """Manual figures of [variant] in [kind]; None while an answer is unjudged. A partial answer is not correct."""
    _check(rows)
    mine = [r for r in rows if r["muestra"] == kind and r["variante"] == variant]
    if not mine or any(not r["manual"] for r in mine):
        return None
    visual = [r for r in mine if r["tipo"] in VISUAL_TYPES]
    answerable = [r for r in mine if r["tipo"] != "sin_respuesta"]
    unanswerable = [r for r in mine if r["tipo"] == "sin_respuesta"]
    cited = [tuple(map(int, r["manual_citas"].split("/"))) for r in answerable if r["manual_citas"]]

    def share(items):
        return 100 * sum(r["manual"] == "correcta" for r in items) / len(items) if items else None

    return {"n": len(mine), "visual_correct": share(visual), "answerable_correct": share(answerable),
            "refusal_correct": share(unanswerable),
            "by_type": {t: share([r for r in mine if r["tipo"] == t]) for t in sorted({r["tipo"] for r in mine})},
            "citations_correct": 100 * sum(ok for ok, _ in cited) / sum(t for _, t in cited) if cited and sum(t for _, t in cited) else None}

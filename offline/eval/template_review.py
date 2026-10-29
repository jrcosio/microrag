"""Template review of F11 (D-035, D-053; specs/011-chat-sources/contracts/eval-answer.md §2).

The decided configuration is generated again with other prompt templates. A variant reaches the manual
review only when the automatic rule screens it in against the report template, measured the same way
(research R4). The manual verdicts of F05 carry over to the answers a variant leaves unchanged; the
changed ones are judged by hand. The adoption rule uses the manual judgement.
"""

SCREEN_CORRECT_POINTS = 1.0   # the rule's correct answers must rise this much over "informe"
SCREEN_CITATIONS_DROP = 2.0   # the rule's citations may fall this much at most: it is stricter than the manual judgement
REFUSAL_MIN = 0.85            # report thresholds (§Evaluación)
CITATIONS_MIN = 0.90
ADOPTION_POINTS = 2.0         # clarify of F11 (D-053): manual correct answers over "informe"
EPS = 1e-9

REVIEW_FIELDS = ["id", "variante", "tipo", "pregunta", "respuesta_esperada", "datos_clave", "fuentes",
                 "respuesta_informe", "respuesta_variante", "regla", "regla_citas", "manual", "manual_citas", "comentario"]


def passes_screen(base, variant):
    """[base] and [variant] are judge.summarize() figures of the automatic rule."""
    return (variant["answer_correct"] >= base["answer_correct"] + SCREEN_CORRECT_POINTS / 100 - EPS
            and variant["refusal_correct"] >= REFUSAL_MIN - EPS
            and variant["citations_correct"] >= base["citations_correct"] - SCREEN_CITATIONS_DROP / 100 - EPS)


def changed_ids(reference_rows, answers):
    """Ids whose variant answer differs from the manually judged one, in the reference order."""
    return [r["id"] for r in reference_rows if answers[r["id"]] != r["respuesta_modelo"]]


def merged_rows(reference_rows, answers, rules, reviewed):
    """Manual rows of a variant: unchanged answers inherit their verdict, changed ones take the review.

    [reviewed] maps an id to its manual and manual_citas; None while a changed answer is still unjudged.
    """
    rows = []
    for ref in reference_rows:
        answer = answers[ref["id"]]
        if answer == ref["respuesta_modelo"]:
            rows.append(dict(ref))
            continue
        verdict = reviewed.get(ref["id"])
        if not verdict or not verdict.get("manual"):
            return None
        rows.append({**ref, "respuesta_modelo": answer, "regla": rules[ref["id"]],
                     "manual": verdict["manual"], "manual_citas": verdict["manual_citas"]})
    return rows


def adopts(base_manual, variant_manual):
    return (variant_manual["answer_correct"] >= base_manual["answer_correct"] + ADOPTION_POINTS / 100 - EPS
            and variant_manual["refusal_correct"] >= REFUSAL_MIN - EPS
            and variant_manual["citations_correct"] >= CITATIONS_MIN - EPS)


def winner(results):
    """The adopted variant with most correct answers; a tie goes to the shorter rules."""
    adopted = [r for r in results if r.get("adopted")]
    if not adopted:
        return None
    return max(adopted, key=lambda r: (r["manual"]["answer_correct"], -r["rules_chars"]))

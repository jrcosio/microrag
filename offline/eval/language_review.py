"""«Automático» review of F12 (D-058, D-060; specs/012-settings-languages/contracts/eval-language.md L5–L6).

The decided configuration with v4 is generated again with the answer language «auto». The answers equal
to the v4 ones keep their manual verdict; the others are judged by hand in reports/language-review.csv.
«Automático» is offered only when the Spanish questions do not fall below v4 and the English questions
over the Spanish documents (crosslang ES·EN) meet the report floors and stay within 10 points of the
same questions in Spanish. The rule was fixed before generating.
"""

from eval import template_review as tr

REVIEW_FIELDS = ["id", "tipo", "pregunta", "respuesta_esperada", "datos_clave", "fuentes", "respuesta_v4", "manual_v4",
                 "respuesta_auto", "regla", "regla_citas", "manual", "manual_citas", "comentario"]
FIGURES = ("answer_correct", "refusal_correct", "citations_correct")
CROSS_POINTS = 10.0  # D-060: correct answers in another language may fall this much below their reference


def check_blocks(reference, variant):
    """G8: the language changes only the prompt, so retrieval must give the same blocks."""
    if reference != variant:
        differing = sorted(qid for qid in reference if reference[qid] != variant.get(qid))
        raise ValueError(f"the blocks differ from the v4 run in {len(differing)} questions, e.g. {differing[:3]}")


def meets_rule(v4, auto):
    """L6: no figure below v4, and the report floors for refusals and citations."""
    return (all(auto[key] >= v4[key] - tr.EPS for key in FIGURES)
            and auto["refusal_correct"] >= tr.REFUSAL_MIN - tr.EPS
            and auto["citations_correct"] >= tr.CITATIONS_MIN - tr.EPS)


def within_points(value, reference, points=CROSS_POINTS):
    return value >= reference - points / 100 - tr.EPS


def meets_floors(manual):
    return manual["refusal_correct"] >= tr.REFUSAL_MIN - tr.EPS and manual["citations_correct"] >= tr.CITATIONS_MIN - tr.EPS


def adopts_automatic(rule_ok, es_en, es_en_reference):
    """FR-024 with D-060: [es_en] are the manual figures of the English questions over the Spanish documents,
    [es_en_reference] the correct answers of their Spanish source questions with v4."""
    return bool(rule_ok) and meets_floors(es_en) and within_points(es_en["answer_correct"], es_en_reference)


def review_row(question, sources, v4_answer, v4_manual, answer, rule, judged, old):
    """One changed answer for reports/language-review.csv; [old] keeps the verdict already written there."""
    return {
        "id": question.id, "tipo": question.type, "pregunta": question.question,
        "respuesta_esperada": question.expected_answer or "", "datos_clave": " | ".join(question.key_facts),
        "fuentes": sources, "respuesta_v4": v4_answer, "manual_v4": v4_manual, "respuesta_auto": answer,
        "regla": rule, "regla_citas": f"{judged['citations_ok']}/{judged['citations_total']}",
        "manual": old.get("manual", ""), "manual_citas": old.get("manual_citas", ""), "comentario": old.get("comentario", ""),
    }

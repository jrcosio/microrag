"""The F05 judge applied to answers with page sources (F18 research R11).

Correctness and refusals are F05's rules (key facts, the fixed refusal). A citation [n] is correct when source n is
a relevant page, or a text block whose pages include a relevant one; an answer without citations counts as one
wrong citation, as in F05.
"""

from eval.judge import Judgement, citations, has_key_facts, is_refusal
from eval.visual_testset import VISUAL_TYPES


def judge(question_type, answer, key_facts, sources, relevant):
    refusal = is_refusal(answer)
    cited = citations(answer)
    if question_type == "sin_respuesta":
        return Judgement(refusal, refusal, cited, 0, 0)
    if refusal:
        return Judgement(False, True, cited, 0, 0)
    correct = has_key_facts(answer, key_facts)
    if not cited:
        return Judgement(correct, False, cited, 0, 1)
    ok = sum(1 for number in cited if 1 <= number <= len(sources) and sources[number - 1].pages & set(relevant))
    return Judgement(correct, False, cited, ok, len(cited))


def summarize(types, judgements):
    """Shares per question type, the visual types together and every answerable question."""
    groups = {}
    for type_, j in zip(types, judgements):
        names = [type_] + (["visual"] if type_ in VISUAL_TYPES else []) + (["answerable"] if type_ != "sin_respuesta" else [])
        for name in names:
            groups.setdefault(name, []).append(j)
    out = {}
    for name, items in groups.items():
        if name == "sin_respuesta":
            out[name] = {"refusal_correct": sum(j.correct for j in items) / len(items), "n": len(items)}
            continue
        total = sum(j.citations_total for j in items)
        out[name] = {"correct": sum(j.correct for j in items) / len(items),
                     "refusals": sum(j.refusal for j in items) / len(items),
                     "citations_correct": sum(j.citations_ok for j in items) / total if total else None, "n": len(items)}
    return out

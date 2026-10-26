"""Automatic judgement of generated answers (F05 research R9, D-032).

- An answer is correct when it is not a refusal and contains every key fact of the expected answer.
- A refusal is the fixed sentence, after normalizing case, accents, punctuation and spaces.
- A citation [n] is correct when block n holds a relevant chunk; an answer without citations counts
  as one wrong citation.
"""

import re
import unicodedata
from dataclasses import dataclass

from eval.prompt import REFUSAL

CITATION = re.compile(r"\[(\d+(?:\s*,\s*\d+)*)\]")
THOUSANDS = re.compile(r"(?<=\d)[.\s](?=\d{3}\b)")


def norm(text):
    plain = THOUSANDS.sub("", text)
    plain = unicodedata.normalize("NFD", plain.lower())
    plain = "".join(c for c in plain if unicodedata.category(c) != "Mn")
    return " ".join(re.sub(r"[^\w]+", " ", plain).split())


def has_key_facts(answer, facts):
    padded = f" {norm(answer)} "
    return all(f" {norm(fact)} " in padded for fact in facts)


def is_refusal(answer):
    return norm(answer) == norm(REFUSAL)


def citations(answer):
    return [int(n) for group in CITATION.findall(answer) for n in group.split(",")]


@dataclass(frozen=True)
class Judgement:
    correct: bool
    refusal: bool
    citations: list
    citations_ok: int
    citations_total: int


def judge_answer(question_type, answer, key_facts, blocks, relevant_pairs):
    refusal = is_refusal(answer)
    cited = citations(answer)
    if question_type == "unanswerable":
        return Judgement(refusal, refusal, cited, 0, 0)
    correct = not refusal and has_key_facts(answer, key_facts)
    if refusal:
        return Judgement(False, True, cited, 0, 0)
    if not cited:
        return Judgement(correct, False, cited, 0, 1)
    ok = 0
    for number in cited:
        if 1 <= number <= len(blocks):
            block = blocks[number - 1]
            ok += any(block.document_id == d and block.first_ordinal <= o <= block.last_ordinal for d, o in relevant_pairs)
    return Judgement(correct, False, cited, ok, len(cited))


def summarize(types, judgements):
    answered = [j for t, j in zip(types, judgements) if t != "unanswerable"]
    unanswerable = [j for t, j in zip(types, judgements) if t == "unanswerable"]
    total_citations = sum(j.citations_total for j in answered)
    return {
        "answer_correct": sum(j.correct for j in answered) / len(answered) if answered else None,
        "refusal_correct": sum(j.correct for j in unanswerable) / len(unanswerable) if unanswerable else None,
        "citations_correct": sum(j.citations_ok for j in answered) / total_citations if total_citations else None,
        "answered": len(answered), "unanswerable": len(unanswerable), "citations": total_citations,
    }

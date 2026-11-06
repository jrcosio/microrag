"""F14 figures (research R8, contract H4): the figures of an answer that are not in its sources nor its question.

fp16 activations on a GPU can turn «2020» into «20000» or «36,4» into «366,4» in contexts full of numbers (D-077).
The detector reads figures with Spanish separators (9.453 and 36,4) and with plain digits, normalizes them to one
form, and flags an answer whose figures do not all appear in what Gemma was given. Citation markers such as [2] are
not figures. A flagged answer is reviewed by hand (D-033); the detector only finds candidates.
"""

import re

CITATION = re.compile(r"\[\d+(?:\s*[,–-]\s*\d+)*\]")
# A thousands-grouped number (9.453 or 1.234.567, optionally with decimals), or plain digits with optional decimals.
FIGURE = re.compile(r"(?<![\d.,])(\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?)(?![\d])")


def normalize(figure):
    """One form for a figure: thousands dots out, the decimal comma as a point, no trailing decimal zeros."""
    text = figure
    if re.fullmatch(r"\d{1,3}(?:\.\d{3})+(?:,\d+)?", text):
        text = text.replace(".", "").replace(",", ".")
    else:
        text = text.replace(",", ".")
    if "." in text:
        whole, _, decimals = text.partition(".")
        decimals = decimals.rstrip("0")
        text = whole.lstrip("0") or "0"
        if decimals:
            text += "." + decimals
    else:
        text = text.lstrip("0") or "0"
    return text


def figures(text):
    """The normalized figures of [text], without citation markers."""
    return {normalize(m.group(1)) for m in FIGURE.finditer(CITATION.sub(" ", text))}


def deformed(answer, sources, question=""):
    """The figures of [answer] that are neither in [sources] (texts) nor in [question], sorted."""
    given = set()
    for source in list(sources) + [question]:
        given |= figures(source)
    return sorted(figures(answer) - given)

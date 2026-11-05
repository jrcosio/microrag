"""The v5-visual template (F18 research R11), evaluated only: the app keeps v4.

It is v4 with every source numbered [n]; a page source is its label («[n] Documento, página p») followed by
its image (or its two tiles) in the same message. The rules to cite and to refuse do not change; one rule says
that some sources are page images. The mix keeps the D-034 text blocks except those whose chunks lie on a page
that enters as an image (duplicate rule); the next blocks fill the gap.
"""

from dataclasses import dataclass

from eval.prompt import build_prompt, estimated_tokens
from eval.vision_runtime import Image, Text

TEMPLATE = "v4"
PAGE_RULE = "- Algunas FUENTES son imágenes de páginas: léelas y cítalas igual que las demás."


@dataclass(frozen=True)
class Source:
    kind: str           # "text" or "page"
    label: str
    pages: frozenset    # (doc sha, page) the source covers
    text: str = ""
    images: tuple = ()  # encoded images of a page source: the page, or its two tiles


def _head_and_tail(question, previous=None):
    """v4's text before the sources (the page rule after the others, then the previous turn) and after them."""
    head, tail = build_prompt(question, [], previous, TEMPLATE).split("FUENTES:\n", 1)
    rules_end = build_prompt(question, [], None, TEMPLATE).split("FUENTES:\n", 1)[0].rstrip("\n")
    conversation = head[len(rules_end):].lstrip("\n")
    return f"{rules_end}\n{PAGE_RULE}\n\n{conversation}FUENTES:\n", tail


def build_parts(question, sources, previous=None):
    """The ordered parts of the message: text with the numbered sources, each page label before its image.

    [previous] is the (question, answer) of the previous turn for a follow-up, as in v4."""
    head, tail = _head_and_tail(question, previous)
    parts, buffer = [], [head]
    for number, source in enumerate(sources, start=1):
        if source.kind == "text":
            buffer.append(f"[{number}] {source.label}\n{source.text.strip()}\n\n")
        else:
            buffer.append(f"[{number}] {source.label}\n")
            parts.append(Text("".join(buffer)))
            buffer = []
            parts.extend(Image(image) for image in source.images)
            buffer.append("\n")
    buffer.append(tail.lstrip("\n"))
    parts.append(Text("".join(buffer)))
    return _merged(parts)


def _merged(parts):
    out = []
    for part in parts:
        if isinstance(part, Text) and out and isinstance(out[-1], Text):
            out[-1] = Text(out[-1].text + part.text)
        else:
            out.append(part)
    return out


def tokens(parts, budget, chars_per_token):
    """Estimated text tokens (as the app counts them), visual tokens (images × budget) and their sum."""
    text = estimated_tokens("".join(p.text for p in parts if isinstance(p, Text)), chars_per_token)
    visual = sum(isinstance(p, Image) for p in parts) * budget
    return {"text": text, "visual": visual, "total": text + visual}


def without_pages(ranked_chunks, chunk_pages, chunk_doc, pages):
    """[ranked_chunks] without the chunks that lie on one of [pages], which enter as images (duplicate rule)."""
    return [chunk for chunk in ranked_chunks
            if not any((chunk_doc[chunk], page) in pages for page in chunk_pages.get(chunk, []))]

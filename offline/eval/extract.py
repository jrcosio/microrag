"""Text extraction of the corpus formats into the app's block structure (F05 research R3).

Every format becomes a list of blocks `(heading path, text, page)`, as the report's parsers do: Markdown
headings form the path, TXT has none, and PDF gives one block per page without path.
"""

import re
from dataclasses import dataclass

from pypdf import PdfReader

HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")
BULLET = re.compile(r"^([-•*·▪–]|\d+[.)º]|[a-z][.)])\s")
SENTENCE_END = (".", ":", ";", "?", "!")


@dataclass(frozen=True)
class RawBlock:
    heading_path: tuple
    text: str
    page: int | None


def extract(path, fmt):
    if fmt == "md":
        return extract_markdown(path.read_text(encoding="utf-8"))
    if fmt == "txt":
        return extract_text(path.read_text(encoding="utf-8"))
    if fmt == "pdf":
        return extract_pdf(path)
    raise ValueError(f"Unsupported format {fmt}")


def _paragraph_text(lines):
    """Lines of one block: paragraphs separated by blank lines, outer blank lines dropped."""
    text = "\n".join(line.rstrip() for line in lines).strip("\n")
    return re.sub(r"\n{3,}", "\n\n", text)


def extract_markdown(text):
    blocks, stack, lines = [], [], []

    def flush():
        body = _paragraph_text(lines)
        if body.strip():
            blocks.append(RawBlock(tuple(title for _, title in stack), body, None))
        lines.clear()

    for line in text.splitlines():
        match = HEADING.match(line)
        if match:
            flush()
            level = len(match.group(1))
            stack = [(lvl, title) for lvl, title in stack if lvl < level] + [(level, match.group(2).strip())]
        else:
            lines.append(line)
    flush()
    return blocks


def extract_text(text):
    body = _paragraph_text(text.splitlines())
    return [RawBlock((), body, None)] if body.strip() else []


def pdf_paragraphs(lines):
    """Joins PDF lines into paragraphs: a blank line, a bullet, or a sentence end followed by an
    uppercase start opens a new paragraph. Hyphenated line ends are joined; bare page numbers dropped."""
    paragraphs, current = [], ""
    for raw in lines:
        line = " ".join(raw.split())
        if not line or line.isdigit():
            if not line and current:
                paragraphs.append(current)
                current = ""
            continue
        starts_new = bool(BULLET.match(line)) or (
            current.endswith(SENTENCE_END) and (line[0].isupper() or line[0].isdigit())
        )
        if not current:
            current = line
        elif starts_new:
            paragraphs.append(current)
            current = line
        elif current.endswith("-") and len(current) > 1 and current[-2].isalpha() and line[0].islower():
            current = current[:-1] + line
        else:
            current = current + " " + line
    if current:
        paragraphs.append(current)
    return paragraphs


def extract_pdf(path):
    blocks = []
    for number, page in enumerate(PdfReader(str(path)).pages, start=1):
        paragraphs = pdf_paragraphs((page.extract_text() or "").splitlines())
        if paragraphs:
            blocks.append(RawBlock((), "\n\n".join(paragraphs), number))
    return blocks


def document_text(blocks):
    """The extracted text of a document, where evidence is looked up."""
    return "\n\n".join(block.text for block in blocks)

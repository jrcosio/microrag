"""Literal port of the report's Kotlin Chunker (§Parsing y chunking), F05 research R4.

F06 implements the same algorithm in Kotlin and checks it against golden/chunker_golden.jsonl, so the
Kotlin details are kept: String.length counts UTF-16 units, trim() uses Kotlin's isWhitespace, `\\s` in
the Java regexes is ASCII only, and sentence pieces keep their trailing space except the last one.
"""

import math
import re
import unicodedata
from dataclasses import dataclass

PARAGRAPH_BREAK = re.compile(r"\n[ \t\n\x0b\f\r]*\n")
SENTENCE_BREAK = re.compile(r"(?<=[.!?;:])[ \t\n\x0b\f\r]+")
_KOTLIN_CONTROL_SPACES = set("\t\n\x0b\f\r\x1c\x1d\x1e\x1f")


@dataclass(frozen=True)
class Chunk:
    ordinal: int
    heading_path: str
    content: str
    page: int | None


def utf16_len(text):
    return len(text.encode("utf-16-le")) // 2


def _kt_is_whitespace(char):
    """Kotlin Char.isWhitespace(): Character.isWhitespace or isSpaceChar (Zs, Zl, Zp)."""
    return char in _KOTLIN_CONTROL_SPACES or unicodedata.category(char) in ("Zs", "Zl", "Zp")


def kt_trim(text):
    start, end = 0, len(text)
    while start < end and _kt_is_whitespace(text[start]):
        start += 1
    while end > start and _kt_is_whitespace(text[end - 1]):
        end -= 1
    return text[start:end]


def chunk_params(tokens, chars_per_token, max_input_chars):
    """Target and maximum chunk size in characters for a size in tokens (research R4)."""
    target = round(tokens * chars_per_token)
    return target, min(round(target * 10 / 7), max_input_chars)


def token_estimate(content, chars_per_token):
    return math.ceil(utf16_len(content) / chars_per_token)


class Chunker:
    def __init__(self, target_chars=1_400, max_chars=2_000):
        self.target_chars = target_chars
        self.max_chars = max_chars

    def chunk(self, blocks):
        out = []
        buf = []  # pieces of the Kotlin StringBuilder
        buf_len = 0
        path = ""
        page = None
        last_paragraph = ""

        def flush(carry):
            nonlocal buf, buf_len
            text = kt_trim("".join(buf))
            if text:
                out.append(Chunk(len(out), path, text, page))
            buf, buf_len = [], 0
            if carry and utf16_len(last_paragraph) < self.target_chars // 3:
                buf, buf_len = [last_paragraph, "\n\n"], utf16_len(last_paragraph) + 2

        for block in blocks:
            block_path = " › ".join(block.heading_path)
            if block_path != path:
                flush(carry=False)
                path = block_path
                page = block.page
            for paragraph in self._split_paragraphs(block.text, self.max_chars):
                length = utf16_len(paragraph)
                if buf_len + length > self.target_chars and buf_len > 0:
                    flush(carry=True)
                if buf_len == 0:
                    page = block.page
                buf.extend([paragraph, "\n\n"])
                buf_len += length + 2
                last_paragraph = paragraph
        flush(carry=False)
        return out

    def _split_paragraphs(self, text, maximum):
        paragraphs = [kt_trim(p) for p in PARAGRAPH_BREAK.split(text)]
        out = []
        for p in paragraphs:
            if not p:
                continue
            out.extend([p] if utf16_len(p) <= maximum else self._split_by_sentence(p, maximum))
        return out

    @staticmethod
    def _split_by_sentence(paragraph, maximum):
        parts, cur = [], ""
        for sentence in SENTENCE_BREAK.split(paragraph):
            if utf16_len(cur) + utf16_len(sentence) > maximum and cur:
                parts.append(cur)
                cur = ""
            cur += sentence + " "
        if cur:
            parts.append(kt_trim(cur))
        return parts

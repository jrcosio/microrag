"""Lexical normalization variants for the FTS ablation (F05 FR-012). Evaluation only.

- `v1`: SpanishText v1 (normalizer.py), the app's normalizer.
- `stop`: the same tokens without the light stemmer.
- `raw`: the original text; FTS5's unicode61 tokenizer does the rest (lowercase, no diacritics).
"""

import re
import unicodedata

import normalizer

VARIANTS = ("v1", "stop", "raw")
_RAW_TOKEN = re.compile(r"\w+")


def _tokens_without_stem(text):
    plain = unicodedata.normalize("NFD", text.lower())
    plain = "".join(c for c in plain if unicodedata.category(c) != "Mn")
    return [t for t in normalizer._scan(plain) if t not in normalizer.STOP and normalizer._utf16_len(t) > 1]  # noqa: SLF001


def normalize(variant, text):
    if variant == "v1":
        return normalizer.normalize(text)
    if variant == "stop":
        return " ".join(_tokens_without_stem(text))
    if variant == "raw":
        return text
    raise ValueError(f"Unknown FTS variant {variant}")


def query(variant, user_query, max_terms=16):
    if variant == "v1":
        return normalizer.fts_query(user_query, max_terms)
    tokens = _tokens_without_stem(user_query) if variant == "stop" else _RAW_TOKEN.findall(user_query.lower())
    distinct = list(dict.fromkeys(tokens))[:max_terms]
    return " OR ".join('"' + t.replace('"', '""') + '"' for t in distinct)

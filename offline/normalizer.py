"""Exact port of SpanishText v1 (app/src/main/java/com/lisdatasolutions/microrag/rag/text/SpanishText.kt).

Both sides must give the same output for every input: golden/normalizer_golden.jsonl checks it in
Python, on the JVM and on the device. Kotlin details replicated on purpose (F04 research R4):
- String.length counts UTF-16 code units;
- Char.isDigit() walks UTF-16 code units, so only BMP characters of category Nd are digits;
- the token regex [\\p{L}\\p{N}]+(?:[-_./][\\p{L}\\p{N}]+)* is replaced by an equivalent scanner;
- lowercase() (Locale.ROOT) then NFD, dropping the Mn marks.

Changing the output requires bumping VERSION here and in SpanishText.VERSION.
"""

import unicodedata

VERSION = 1

STOP = frozenset(
    """
    a al algo ante asi como con contra cual cuales cuando de del
    desde donde durante e el ella ellas ellos en entre era es esa
    esas ese eso esos esta estas este esto estos fue ha han hay
    la las le les lo los mas me mi muy ni no nos o os para
    pero por que quien se sea ser si sin sobre su sus tambien
    te tiene u un una uno unos unas y ya yo cuanto cuanta
    """.split()
)

SEPARATORS = "-_./"


def _utf16_len(text):
    return len(text.encode("utf-16-le")) // 2


def _is_alnum(char):
    return unicodedata.category(char)[0] in ("L", "N")


def _is_kotlin_digit(char):
    return ord(char) <= 0xFFFF and unicodedata.category(char) == "Nd"


def _scan(text):
    """Non-overlapping, leftmost, greedy matches of the Kotlin token regex."""
    i, n = 0, len(text)
    while i < n:
        if not _is_alnum(text[i]):
            i += 1
            continue
        j = i
        while j < n and _is_alnum(text[j]):
            j += 1
        while j + 1 < n and text[j] in SEPARATORS and _is_alnum(text[j + 1]):
            j += 1
            while j < n and _is_alnum(text[j]):
                j += 1
        yield text[i:j]
        i = j


def light_stem(token):
    """Simplified equivalent of Lucene's SpanishLightStemmer, as SpanishText.lightStem."""
    if _utf16_len(token) < 5 or any(_is_kotlin_digit(c) for c in token):
        return token
    last = token[-1]
    if last in ("o", "a", "e"):
        return token[:-1]
    if last == "s":
        if token.endswith("eses"):
            return token[:-2]
        if token[-2] == "e" and token[-3] == "c":
            return token[:-3] + "z"
        if token[-2] in ("o", "a", "e"):
            return token[:-2]
    return token


def tokens(text):
    plain = unicodedata.normalize("NFD", text.lower())
    plain = "".join(c for c in plain if unicodedata.category(c) != "Mn")
    return [light_stem(t) for t in _scan(plain) if t not in STOP and _utf16_len(t) > 1]


def normalize(text):
    return " ".join(tokens(text))


def fts_query(user_query, max_terms=16):
    """FTS5 query: normalized tokens, quoted, joined with OR."""
    distinct = list(dict.fromkeys(tokens(user_query)))[:max_terms]
    return " OR ".join('"' + t.replace('"', '""') + '"' for t in distinct)

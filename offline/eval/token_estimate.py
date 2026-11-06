"""F14 token estimate (research R9, contract G6): tokens ≈ ⌈letters/a + digits/b + rest/c⌉, by code points.

With a = b = c it is F11's ⌈code points / chars_per_token⌉ (D-034). Digits cost more tokens than letters in Gemma's
tokenizer, so a text full of figures went over the estimate about 2.2 times (D-077). Kotlin's
PromptBuilder.estimatedTokens is the same computation; TokenEstimateTest and this module's test share their cases.
"""

import math


def classes(text):
    """(letters, digits, rest) of [text], by code points, as Kotlin's Character.isLetter and isDigit."""
    letters = digits = rest = 0
    for ch in text:
        if ch.isalpha():
            letters += 1
        elif ch.isdigit():
            digits += 1
        else:
            rest += 1
    return letters, digits, rest


def estimate(text, a, b, c):
    letters, digits, rest = classes(text)
    return math.ceil(letters / a + digits / b + rest / c)


def fit(samples):
    """Least squares of real tokens on (letters, digits, rest) through the origin; returns (a, b, c) as chars per token.

    [samples] are (text, real tokens). Solves the 3×3 normal equations directly, without numpy.
    """
    rows = [(classes(text), tokens) for text, tokens in samples]
    m = [[0.0] * 3 for _ in range(3)]
    v = [0.0] * 3
    for x, y in rows:
        for i in range(3):
            v[i] += x[i] * y
            for j in range(3):
                m[i][j] += x[i] * x[j]
    w = _solve(m, v)
    return tuple(1.0 / max(wi, 1e-9) for wi in w)


def safe(samples, a, b, c, ratio=1.0):
    """Scales the digits' and the rest's ratios (b, c) down until the p95 of real/estimated over [samples] is at most
    [ratio] (the safe side). The letters' ratio stays: text without figures keeps its estimate, so its prompts keep
    their blocks."""
    scale = 1.0
    while True:
        ratios = sorted(tokens / max(1, estimate(text, a, b * scale, c * scale)) for text, tokens in samples)
        p95 = ratios[max(0, math.ceil(0.95 * len(ratios)) - 1)]
        if p95 <= ratio or scale < 0.2:
            return a, b * scale, c * scale
        scale *= 0.99


def _solve(m, v):
    n = len(v)
    a = [row[:] + [v[i]] for i, row in enumerate(m)]
    for col in range(n):
        pivot = max(range(col, n), key=lambda r: abs(a[r][col]))
        a[col], a[pivot] = a[pivot], a[col]
        for r in range(n):
            if r != col and a[col][col]:
                f = a[r][col] / a[col][col]
                a[r] = [x - f * y for x, y in zip(a[r], a[col])]
    return [a[i][n] / a[i][i] if a[i][i] else 0.0 for i in range(n)]


def ratios(samples, a, b, c):
    """real / estimated per sample."""
    return [tokens / max(1, estimate(text, a, b, c)) for text, tokens in samples]


def summary(values):
    ordered = sorted(values)
    p = lambda q: ordered[max(0, math.ceil(q * len(ordered)) - 1)]  # noqa: E731 (nearest rank, as the app)
    return {"n": len(ordered), "mean": round(sum(ordered) / len(ordered), 3), "p50": round(p(0.5), 3),
            "p95": round(p(0.95), 3), "max": round(ordered[-1], 3)}


def digit_share(text):
    letters, digits, rest = classes(text)
    total = letters + digits + rest
    return digits / total if total else 0.0

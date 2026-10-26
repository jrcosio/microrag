"""Writes golden/embedding_overflow.jsonl: the successive D-036 cuts of each text, exactly as
embedding_text.overflow_cut makes them, for EmbeddingOverflowTest in the app (F07 research R6).
Usage: gen_overflow_cases.py [--check].
"""

import json
import sys
from pathlib import Path

OFFLINE = Path(__file__).resolve().parent
sys.path.insert(0, str(OFFLINE))  # -I leaves the script directory out of sys.path

from embedding_text import DOCUMENT, OVERFLOW_ATTEMPTS, document_input, overflow_cut  # noqa: E402

TARGET = OFFLINE / "golden" / "embedding_overflow.jsonl"

LONG_SPANISH = (
    "El plazo para solicitar la devolución es de treinta días naturales desde la entrega del producto. "
    "Transcurrido ese plazo, la empresa podrá rechazar la solicitud salvo que el defecto sea de fabricación. "
) * 12

CASES = [
    ("long_spanish_with_header", document_input(LONG_SPANISH, "Manual de garantías › Devoluciones › Plazos")),
    ("accents_and_enye", DOCUMENT + "Año pequeño, señal añeja: ñandú, cigüeña y pingüino. " * 20),
    ("emoji_outside_bmp", DOCUMENT + "Resumen 😀 con emoji 🚀 fuera del plano básico 🧭 y texto. " * 15),
    ("no_spaces", DOCUMENT.replace(" ", "") + "abcdefghijklmnopqrstuvwxyz0123456789" * 30),
    # 100 code points, so the first cut keeps 90: the space is the last kept one, or the first dropped one.
    ("space_is_the_last_kept", "x" * 89 + " " + "y" * 10),
    ("space_right_after_the_cut", "x" * 90 + " " + "y" * 9),
    ("single_word", "palabra"),
    ("empty", ""),
]


def steps(text):
    """The texts tried after each failed attempt: OVERFLOW_ATTEMPTS - 1 cuts."""
    out = []
    for _ in range(OVERFLOW_ATTEMPTS - 1):
        text = overflow_cut(text)
        out.append(text)
    return out


def render():
    lines = [json.dumps({"case": name, "text": text, "steps": steps(text)}, ensure_ascii=False) for name, text in CASES]
    return "\n".join(lines) + "\n"


def main(argv):
    content = render()
    if "--check" in argv:
        if TARGET.read_text(encoding="utf-8") != content:
            print(f"{TARGET} is out of date; run gen_overflow_cases.py")
            return 1
        print(f"{TARGET} is up to date")
        return 0
    TARGET.write_text(content, encoding="utf-8")
    print(f"wrote {len(CASES)} cases to {TARGET}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

"""Writes golden/extraction_golden.jsonl: how the app must decode TXT and Markdown, extract their
blocks and join PDF lines into paragraphs, exactly as offline/eval/extract.py does
(F06 contracts/extraction-golden.md). Usage: gen_extraction_golden.py [--check].
"""

import base64
import json
import sys
from pathlib import Path

OFFLINE = Path(__file__).resolve().parent
sys.path.insert(0, str(OFFLINE))  # -I leaves the script directory out of sys.path

from pypdf import PdfReader  # noqa: E402

from eval import corpus  # noqa: E402
from eval.extract import decode_text, extract_markdown, extract_text, pdf_paragraphs  # noqa: E402

TARGET = OFFLINE / "golden" / "extraction_golden.jsonl"
EXCERPT_CHARS = 8_000
PDF_PAGES = 10

DECODE = [
    ("decode_utf8", "Año 2024 · 1.250 € — «ñandú»".encode("utf-8")),
    ("decode_utf8_bom", b"\xef\xbb\xbf" + "Título\ncon BOM".encode("utf-8")),
    ("decode_cp1252", b"A\xf1o \x80 \xbfQu\xe9? \x93comillas\x94"),
    ("decode_cp1252_undefined", b"\x81\x8d\x8f\x90\x9d y \xf1"),
    ("decode_invalid_in_the_middle", "Hola ".encode("utf-8") + b"\xff" + " mundo ñ".encode("utf-8")),
    ("decode_overlong", b"barra \xc0\xaf"),
    ("decode_surrogate", b"\xed\xa0\x80 fin"),
    ("decode_bom_then_invalid", b"\xef\xbb\xbfA\xf1o"),
    ("decode_crlf", b"uno\r\ndos\r\n\r\ntres"),
    ("decode_lone_cr", b"uno\rdos\r\rtres"),
    ("decode_mixed_newlines", b"a\r\nb\rc\nd\r"),
    ("decode_empty", b""),
]

TEXT = [
    ("text_empty", ""),
    ("text_only_spaces", "   \n \xa0 \u3000\n\t\n"),
    ("text_paragraphs", "Primera línea\nsegunda línea\n\n\n\nOtro párrafo  \n"),
    ("text_trailing_spaces", "línea con espacios   \t\n\nOtra\xa0\n"),
    ("text_indent", "  sangría inicial\n  segunda línea\n\n    otro párrafo"),
    ("text_separators", "a\x0bb\x0cc\x1cd\x1de\x1ef\x85g\u2028h\u2029i\n\nj"),
    ("text_blank_lines_with_spaces", "uno\n \t \ndos\n\u2003\ntres"),
    ("text_leading_blank_lines", "\n\n\nTexto tras líneas vacías\n\n"),
]

MARKDOWN = [
    ("md_levels", "# Uno\nA\n## Dos\nB\n### Tres\nC\n#### Cuatro\nD\n##### Cinco\nE\n###### Seis\nF\n"),
    ("md_seven_hashes", "####### No es encabezado\ntexto"),
    ("md_no_space", "#SinEspacio\n# Con espacio\ncuerpo"),
    ("md_nbsp_after_hashes", "#\xa0Título con NBSP\ncuerpo"),
    ("md_tab_after_hashes", "#\tTítulo con tab\ncuerpo"),
    ("md_closing_hashes", "# Título ##\ncuerpo\n## Otro #  \nmás"),
    ("md_empty_title", "#   \ncuerpo bajo título vacío"),
    ("md_single_space_after_hash", "# \ncuerpo: con un solo espacio no hay encabezado"),
    ("md_two_spaces_after_hash", "#  \ncuerpo: con dos espacios, título vacío"),
    ("md_hash_title", "# #\ncuerpo"),
    ("md_hashes_only_title", "## ###\ncuerpo"),
    ("md_inner_spaces", "##  Doble  espacio  \ncuerpo"),
    ("md_up_and_down", "# A\na\n### C\nc\n## B\nb\n# D\nd"),
    ("md_empty_section", "# Vacía\n\n\n# Llena\ntexto"),
    ("md_text_before_heading", "Preámbulo sin encabezado.\n\n# Primero\ncuerpo"),
    ("md_heading_at_end", "texto\n# Final"),
    ("md_paragraphs", "# Sección\n\nPárrafo uno\ncontinúa.\n\n\n\nPárrafo dos.   \n\n"),
    ("md_separators", "# Sección\nuno\x85dos\u2028tres\n\ncuatro"),
]

PDF_PAGE = [
    ("pdf_hyphen_lowercase", "La configura-\nción del equipo"),
    ("pdf_hyphen_uppercase", "Sistema X-\nY de prueba"),
    ("pdf_hyphen_alone", "-\nminúscula sigue"),
    ("pdf_hyphen_digit", "Año 2024-\n2025 vigente"),
    ("pdf_page_number", "Texto de la página.\n12\n"),
    ("pdf_superscript_alone", "Nota al pie\n²\nsigue el texto"),
    ("pdf_bullets", "Lista:\n- uno\n• dos\n· tres\n▪ cuatro\n– cinco\n1. seis\n2) siete\n3º ocho\na) nueve\nb. diez"),
    ("pdf_not_bullets", "-sin espacio\n10.5 por ciento\nA) mayúscula\nab) dos letras"),
    ("pdf_sentence_end", "Fin de frase.\nMayúscula abre párrafo.\n2024 empieza por dígito.\nminúscula sigue: tras dos puntos\nOtra; Y punto y coma\n¿Pregunta?\nRespuesta! Final"),
    ("pdf_blank_lines", "Uno\n\n\nDos\n \nTres"),
    ("pdf_unicode_spaces", "a\u2003b\xa0c  d\n\u3000inicio"),
    ("pdf_non_bmp", "Frase.\n𝐀lfa matemática\n𝟏 dígito\nSigue"),
    ("pdf_accented_uppercase", "Termina.\nÉsta empieza con tilde.\nñandú minúscula"),
    ("pdf_crlf", "Línea uno\r\nlínea dos.\r\nLínea tres\r\n"),
    ("pdf_digits_only", "12\n345\n"),
    ("pdf_empty", ""),
]


def _excerpt(text):
    if len(text) <= EXCERPT_CHARS:
        return text
    cut = text.rfind("\n\n", 0, EXCERPT_CHARS)
    return text[: cut if cut > 0 else EXCERPT_CHARS]


def _blocks(blocks):
    return [{"heading_path": list(b.heading_path), "text": b.text, "page": b.page} for b in blocks]


def cases():
    out = []
    for name, data in DECODE:
        out.append({"case": name, "kind": "decode", "input_b64": base64.b64encode(data).decode("ascii"), "text": decode_text(data)})
    docs = corpus.load_manifest()
    texts = TEXT + [(f"corpus:{d.id}", _excerpt(decode_text((corpus.DOCS / d.file_name).read_bytes())))
                    for d in docs if d.format == "txt"]
    for name, text in texts:
        out.append({"case": name, "kind": "text", "input": text, "blocks": _blocks(extract_text(text))})
    markdowns = MARKDOWN + [(f"corpus:{d.id}", _excerpt(decode_text((corpus.DOCS / d.file_name).read_bytes())))
                            for d in docs if d.format == "md"]
    for name, text in markdowns:
        out.append({"case": name, "kind": "markdown", "input": text, "blocks": _blocks(extract_markdown(text))})
    pages = list(PDF_PAGE)
    for doc in (d for d in docs if d.format == "pdf"):
        for number, page in enumerate(PdfReader(str(corpus.DOCS / doc.file_name)).pages[:PDF_PAGES], start=1):
            pages.append((f"corpus:{doc.id}:p{number}", page.extract_text() or ""))
    for name, text in pages:
        paragraphs = pdf_paragraphs(text.splitlines())
        out.append({"case": name, "kind": "pdf_page", "input": text, "text": "\n\n".join(paragraphs),
                    "paragraphs": len(paragraphs)})
    return out


# Raw line separators inside JSON strings would break line-based readers (Python splitlines,
# editors); JSON allows escaping them, and json.dumps leaves them raw with ensure_ascii=False.
LINE_SEPARATOR_ESCAPES = {"\x85": "\\u0085", "\u2028": "\\u2028", "\u2029": "\\u2029"}


def render():
    lines = []
    for case in cases():
        line = json.dumps(case, ensure_ascii=False)
        for raw, escaped in LINE_SEPARATOR_ESCAPES.items():
            line = line.replace(raw, escaped)
        lines.append(line + "\n")
    return "".join(lines)


def main():
    text = render()
    if "--check" in sys.argv:
        if not TARGET.exists() or TARGET.read_text(encoding="utf-8") != text:
            raise SystemExit(f"{TARGET} is out of date")
        print("extraction golden up to date")
        return
    TARGET.write_text(text, encoding="utf-8")
    print(f"wrote {text.count(chr(10))} cases -> {TARGET}")


if __name__ == "__main__":
    main()

"""Writes the PDF fixtures of the app's instrumented tests (F06 research R12, contracts/extraction-golden.md).

The files are deterministic: objects written by hand with the standard Helvetica font and
WinAnsiEncoding, no dates, and pypdf's RC4-128 encryption, whose identifier is a checksum of the
content. Usage: gen_pdf_fixtures.py [--check].
"""

import io
import random
import sys
import zlib
from pathlib import Path

from pypdf import PdfReader, PdfWriter

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/androidTest/assets/pdf"

PAGE_1 = [
    "Manual de prueba",
    "Este documento sirve para comprobar la extracción de texto.",
    "La configura-",
    "ción del equipo se describe en esta página. El texto sigue",
    "en la línea siguiente sin punto final",
    "y termina aquí.",
    "Requisitos:",
    "- Un teléfono con Android 12 o superior.",
    "• Espacio libre suficiente.",
    "",
    "1",
]
PAGE_2 = [
    "Instalación",
    "Copia el archivo en la carpeta de descargas. Después ábrelo",
    "desde la aplicación. ¿Funciona? ¡Sí!",
    "1. Abre los ajustes.",
    "2) Elige la opción de importar.",
    "a) Confirma la selección.",
    "",
    "2",
]
PAGE_3 = [
    "Preguntas frecuentes",
    "El precio es de 1.250 euros con el IVA incluido. Las",
    "dudas se resuelven en el plazo de 30 días.",
    "",
    "3",
]
TEXT_PAGES = [PAGE_1, PAGE_2, PAGE_3]
WORDS = ("documento sistema usuario archivo texto página párrafo índice búsqueda consulta modelo "
         "respuesta fuente capítulo artículo plazo factura cliente contrato servicio equipo red").split()


def _text_stream(lines):
    ops = ["BT", "/F1 11 Tf", "72 750 Td", "14 TL"]
    for line in lines:
        escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        ops.append(f"({escaped}) Tj T*")
    ops.append("ET")
    return "\n".join(ops).encode("cp1252")


def _image_stream():
    return b"q 200 0 0 200 150 400 cm /Im1 Do Q"


def build_pdf(pages, compress=False):
    """[pages] holds a list of lines (text page) or None (page with only an image)."""
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        None,
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    ]
    pixels = bytes((x * 16 + y) % 256 for y in range(16) for x in range(16))
    objects.append(b"<< /Type /XObject /Subtype /Image /Width 16 /Height 16 /ColorSpace /DeviceGray "
                   b"/BitsPerComponent 8 /Length %d >>\nstream\n" % len(pixels) + pixels + b"\nendstream")
    image_id = len(objects)
    kids = []
    for lines in pages:
        content = _image_stream() if lines is None else _text_stream(lines)
        if compress:
            content = zlib.compress(content, 9)
            header = b"<< /Length %d /Filter /FlateDecode >>" % len(content)
        else:
            header = b"<< /Length %d >>" % len(content)
        objects.append(header + b"\nstream\n" + content + b"\nendstream")
        content_id = len(objects)
        objects.append(
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            b"/Resources << /Font << /F1 3 0 R >> /XObject << /Im1 %d 0 R >> >> /Contents %d 0 R >>"
            % (image_id, content_id)
        )
        kids.append(len(objects))
    objects[1] = b"<< /Type /Pages /Kids [%s] /Count %d >>" % (b" ".join(b"%d 0 R" % k for k in kids), len(kids))
    out, offsets = bytearray(b"%PDF-1.4\n"), []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % number + body + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1)
    out += b"".join(b"%010d 00000 n \n" % offset for offset in offsets)
    out += (b"trailer\n<< /Size %d /Root 1 0 R /ID [<4d6963726f524147> <4d6963726f524147>] >>\nstartxref\n%d\n%%%%EOF\n"
            % (len(objects) + 1, xref))
    return bytes(out)


def _encrypt(data, user_password, owner_password):
    writer = PdfWriter(clone_from=PdfReader(io.BytesIO(data)))
    writer.encrypt(user_password=user_password, owner_password=owner_password, algorithm="RC4-128")
    buffer = io.BytesIO()
    writer.write(buffer)
    return buffer.getvalue()


def _long_document(pages=200, lines=40, seed=20261026):
    rng = random.Random(seed)
    out = []
    for number in range(1, pages + 1):
        page = []
        for _ in range(lines):
            words = [rng.choice(WORDS) for _ in range(rng.randint(6, 11))]
            sentence = " ".join(words)
            page.append(sentence[0].upper() + sentence[1:] + ("." if rng.random() < 0.3 else ""))
        page += ["", str(number)]
        out.append(page)
    return out


def fixtures():
    text = build_pdf(TEXT_PAGES)
    return {
        "text_3_pages.pdf": text,
        "scanned.pdf": build_pdf([None, None]),
        "partially_scanned.pdf": build_pdf([PAGE_1, None, PAGE_3]),
        "encrypted.pdf": _encrypt(text, "secreto", "propietario"),
        "restricted.pdf": _encrypt(text, "", "propietario"),
        "truncated.pdf": text[: len(text) * 6 // 10],
        "fake.pdf": "Esto no es un PDF, es un texto con extensión .pdf.\n".encode("utf-8"),
        "text_200_pages.pdf": build_pdf(_long_document(), compress=True),
    }


def main():
    files = fixtures()
    if "--check" in sys.argv:
        stale = [name for name, data in files.items() if not (TARGET / name).exists() or (TARGET / name).read_bytes() != data]
        if stale:
            raise SystemExit(f"out of date: {', '.join(stale)}")
        print(f"{len(files)} fixtures up to date")
        return
    TARGET.mkdir(parents=True, exist_ok=True)
    for name, data in files.items():
        (TARGET / name).write_bytes(data)
    print(f"wrote {len(files)} fixtures, {sum(map(len, files.values()))} bytes -> {TARGET}")


if __name__ == "__main__":
    main()

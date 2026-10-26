"""Text extraction and BOE conversion (F05 T009, research R3)."""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval.boe import boe_to_markdown  # noqa: E402
from eval.extract import RawBlock, extract, extract_markdown, extract_text, pdf_paragraphs  # noqa: E402


def minimal_pdf(pages):
    """A tiny valid PDF with one text line per entry of each page (Helvetica, WinAnsi)."""
    objects = ["<< /Type /Catalog /Pages 2 0 R >>", None, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"]
    kids = []
    for lines in pages:
        ops = ["BT", "/F1 11 Tf", "72 750 Td", "14 TL"]
        for line in lines:
            escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            ops.append(f"({escaped}) Tj T*")
        ops.append("ET")
        stream = "\n".join(ops).encode("cp1252")
        objects.append(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")
        content_id = len(objects)
        objects.append(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents {content_id} 0 R >>")
        kids.append(len(objects))
    objects[1] = "<< /Type /Pages /Kids [%s] /Count %d >>" % (" ".join(f"{k} 0 R" for k in kids), len(kids))
    out, offsets = bytearray(b"%PDF-1.4\n"), []
    for i, obj in enumerate(objects, start=1):
        offsets.append(len(out))
        body = obj if isinstance(obj, bytes) else obj.encode("latin-1")
        out += b"%d 0 obj\n" % i + body + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1)
    out += b"".join(b"%010d 00000 n \n" % o for o in offsets)
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objects) + 1, xref)
    return bytes(out)


class MarkdownTest(unittest.TestCase):
    def test_headings_build_the_path_and_paragraphs_stay(self):
        blocks = extract_markdown("Intro.\n\n# Título\n\nUno.\n\nDos.\n\n## Sección\nTres.\n### Sub\n\nCuatro.\n# Otro\n\nCinco.\n")
        self.assertEqual(blocks, [
            RawBlock((), "Intro.", None),
            RawBlock(("Título",), "Uno.\n\nDos.", None),
            RawBlock(("Título", "Sección"), "Tres.", None),
            RawBlock(("Título", "Sección", "Sub"), "Cuatro.", None),
            RawBlock(("Otro",), "Cinco.", None),
        ])

    def test_text_has_no_path(self):
        self.assertEqual(extract_text("Uno.\n\nDos.\n"), [RawBlock((), "Uno.\n\nDos.", None)])


class PdfTest(unittest.TestCase):
    def test_paragraph_heuristic(self):
        lines = ["El plazo de solicitud", "es de 30 días.", "Después se resuelve", "en un mes.", "", "- primer punto", "- segundo pun-", "to final.", "12"]
        self.assertEqual(pdf_paragraphs(lines), [
            "El plazo de solicitud es de 30 días.",
            "Después se resuelve en un mes.",
            "- primer punto",
            "- segundo punto final.",
        ])

    def test_pdf_pages_become_blocks_with_page_numbers(self):
        pdf = minimal_pdf([["Primera línea", "del documento."], ["Segunda página."]])
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "doc.pdf")
            path.write_bytes(pdf)
            blocks = extract(path, "pdf")
        self.assertEqual(blocks, [RawBlock((), "Primera línea del documento.", 1), RawBlock((), "Segunda página.", 2)])


class BoeTest(unittest.TestCase):
    XML = """<?xml version="1.0" encoding="utf-8"?>
<response><status><code>200</code></status><data><texto>
<bloque id="preambulo" tipo="preambulo"><version id_norma="BOE-A-1" fecha_publicacion="19941125" fecha_vigencia="19950101">
<p class="parrafo">Exposición de motivos.</p></version></bloque>
<bloque id="ti" tipo="encabezado" titulo="TÍTULO I"><version id_norma="BOE-A-1" fecha_publicacion="19941125" fecha_vigencia="19950101">
<p class="titulo_num">TÍTULO I</p><p class="titulo_tit">Ámbito</p></version></bloque>
<bloque id="ci" tipo="encabezado" titulo="CAPÍTULO I"><version id_norma="BOE-A-1" fecha_publicacion="19941125" fecha_vigencia="19950101">
<p class="capitulo_num">CAPÍTULO I</p><p class="capitulo_tit">Normas generales</p></version></bloque>
<bloque id="a1" tipo="precepto" titulo="Artículo 1">
<version id_norma="BOE-A-1" fecha_publicacion="19941125" fecha_vigencia="19950101"><p class="articulo">Artículo 1. Objeto.</p><p class="parrafo">Texto antiguo.</p></version>
<version id_norma="BOE-A-2" fecha_publicacion="20190305" fecha_vigencia="20190306"><p class="articulo">Artículo 1. Objeto.</p><p class="parrafo">Texto   vigente
 en dos líneas.</p><blockquote><p class="nota_pie">Se modifica por el art. 2.</p></blockquote>
<table><tr><td>Plazo</td><td>30 días</td></tr></table></version></bloque>
<bloque id="firma" tipo="firma"><version id_norma="BOE-A-1"><p class="firma_rey">JUAN CARLOS R.</p></version></bloque>
</texto></data></response>"""

    def test_structure_latest_version_and_no_notes(self):
        md = boe_to_markdown(self.XML, boe_id="BOE-A-1", updated="2019-03-06")
        self.assertIn("texto consolidado", md.splitlines()[0].lower())
        body = md.split("\n\n", 1)[1]
        self.assertEqual(body, "\n\n".join([
            "# Preámbulo", "Exposición de motivos.",
            "# TÍTULO I. Ámbito",
            "## CAPÍTULO I. Normas generales",
            "#### Artículo 1. Objeto.", "Texto vigente en dos líneas.", "Plazo | 30 días",
        ]) + "\n")

    def test_markdown_extracts_the_article_path(self):
        md = boe_to_markdown(self.XML, boe_id="BOE-A-1", updated="2019-03-06")
        paths = [b.heading_path for b in extract_markdown(md)]
        self.assertIn(("TÍTULO I. Ámbito", "CAPÍTULO I. Normas generales", "Artículo 1. Objeto."), paths)


if __name__ == "__main__":
    unittest.main()

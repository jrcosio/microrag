"""BOE consolidated legislation (open data XML) to Markdown (F05 research R2–R3).

The current text is the last version of each block. Modification notes are left out, titles and
chapters become `#` and `##`, articles `####`, and tables one row per line. The first paragraph states
that it is an informative consolidated text and cites the source, as the BOE reuse conditions require.
"""

import re
import xml.etree.ElementTree as ET

API = "https://www.boe.es/datosabiertos/api/legislacion-consolidada/id/{boe_id}/texto"

LEVELS = {"titulo": 1, "libro": 1, "capitulo": 2, "seccion": 3}
SKIPPED_BLOCKS = {"firma", "nota_inicial"}
SKIPPED_CLASSES = ("nota_pie",)


def _text(element):
    return " ".join("".join(element.itertext()).split())


def _heading_level(css):
    for prefix, level in LEVELS.items():
        if css.startswith(prefix):
            return level
    return None


def _version_paragraphs(version, preamble):
    out = ["# Preámbulo"] if preamble else []
    pending = None  # (level, number) waiting for its title line
    for element in version:
        if element.tag == "blockquote":
            continue
        if element.tag == "table":
            rows = [" | ".join(_text(cell) for cell in row) for row in element.iter("tr")]
            rows = [row for row in rows if row.strip(" |")]
            if rows:
                out.append("\n".join(rows))
            continue
        if element.tag != "p":
            continue
        css, text = element.get("class", ""), _text(element)
        if not text or css.startswith(SKIPPED_CLASSES):
            continue
        level = _heading_level(css)
        if level is not None and css.endswith("_num"):
            pending = (level, text)
            continue
        if level is not None:
            number = pending[1] if pending and pending[0] == level else None
            heading = f"{number.rstrip('.')}. {text}" if number else text
            out.append("#" * level + " " + heading)
            pending = None
            continue
        if pending:
            out.append("#" * pending[0] + " " + pending[1])
            pending = None
        out.append(("#### " + text) if css == "articulo" else text)
    if pending:
        out.append("#" * pending[0] + " " + pending[1])
    return out


def boe_to_markdown(xml_text, boe_id, updated):
    root = ET.fromstring(xml_text)
    texto = root.find("data/texto")
    if texto is None:
        raise ValueError(f"{boe_id}: unexpected BOE response")
    paragraphs = []
    for bloque in texto.findall("bloque"):
        if bloque.get("tipo") in SKIPPED_BLOCKS:
            continue
        versions = bloque.findall("version")
        if versions:
            paragraphs.extend(_version_paragraphs(versions[-1], bloque.get("tipo") == "preambulo"))
    notice = (
        f"Texto consolidado de carácter informativo ({boe_id}), actualizado a {updated}. "
        "Basado en datos de la Agencia Estatal Boletín Oficial del Estado (www.boe.es); "
        "se omiten las notas de modificación."
    )
    body = "\n\n".join(p for p in paragraphs if p.strip())
    return notice + "\n\n" + re.sub(r"\n{3,}", "\n\n", body) + "\n"

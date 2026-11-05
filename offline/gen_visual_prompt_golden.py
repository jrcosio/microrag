"""Writes golden/visual_prompt_golden.jsonl (F20 research R4): the v5-visual prompt the desktop builds.

Fixed, hand-written cases (no corpus): the question, the previous turn, the text blocks and the page that
enters as an image, with the parts visual_prompt.build_parts makes of them. An image is kept as its
marker {"image": "<id>"}, never as bytes. VisualPromptGoldenTest replays every case with the app's port.

    offline/.venv/bin/python -I offline/gen_visual_prompt_golden.py [--check]
"""

import json
import sys
from pathlib import Path

OFFLINE = Path(__file__).resolve().parent
sys.path.insert(0, str(OFFLINE))  # -I leaves the script directory out of sys.path

from eval import visual_prompt  # noqa: E402
from eval.vision_runtime import Image, Text  # noqa: E402

GOLDEN = OFFLINE / "golden" / "visual_prompt_golden.jsonl"

LONG_ANSWER = ("La tabla de la página 4 recoge los importes por trimestre 🧾. " * 12).strip()

BLOCKS = [
    {"document_name": "informe-ine.pdf", "heading_path": "Resultados › Empleo", "page": 4,
     "text": "  La tasa de paro bajó al 11,2 % en el tercer trimestre.\n"},
    {"document_name": "manual.md", "heading_path": None, "page": None,
     "text": "El plazo de solicitud es de 15 días hábiles."},
    {"document_name": "folleto.pdf", "heading_path": "Horarios", "page": None,
     "text": "Abierto de lunes a viernes, de 9:00 a 14:00."},
    {"document_name": "informe-ine.pdf", "heading_path": "Resultados › Salarios", "page": 7,
     "text": "El salario medio fue de 2.128 euros brutos al mes."},
]

PDF_PAGE = {"document_name": "informe-ine.pdf", "page": 4, "image": "p1"}
IMAGE_PAGE = {"document_name": "cartel.jpg", "page": None, "image": "p2"}

CASES = [
    {"id": "v1-page-only", "question": "¿Qué dice el cartel?", "previous": None, "blocks": [], "page": IMAGE_PAGE},
    {"id": "v2-pdf-page-only", "question": "¿Cuál es la tasa de paro?", "previous": None, "blocks": [], "page": PDF_PAGE},
    {"id": "v3-one-block", "question": "¿Cuál es la tasa de paro?", "previous": None, "blocks": BLOCKS[:1], "page": PDF_PAGE},
    {"id": "v4-four-blocks", "question": " ¿Y los salarios? ", "previous": None, "blocks": BLOCKS, "page": PDF_PAGE},
    {"id": "v5-null-parts", "question": "¿Qué plazo hay?", "previous": None, "blocks": BLOCKS[1:3], "page": IMAGE_PAGE},
    {"id": "v6-follow-up", "question": "¿Y en el segundo?", "previous": ["¿Cuál fue la tasa de paro en el tercer trimestre?", "Fue del 11,2 % [1]."],
     "blocks": BLOCKS[:2], "page": PDF_PAGE},
    {"id": "v7-long-previous", "question": "¿Y el total?", "previous": ["¿Qué importes hay? " * 30, LONG_ANSWER],
     "blocks": BLOCKS[3:], "page": PDF_PAGE},
    {"id": "v8-follow-up-page-only", "question": "¿Y la letra pequeña?", "previous": ["¿Qué dice el cartel?", "Anuncia las fiestas [1]."],
     "blocks": [], "page": IMAGE_PAGE},
    {"id": "v9-same-page-block", "question": "Resume la página 4", "previous": None, "blocks": [BLOCKS[0], BLOCKS[3]], "page": PDF_PAGE},
    {"id": "v10-three-blocks-image", "question": "¿Horario y plazo?", "previous": None, "blocks": BLOCKS[:3], "page": IMAGE_PAGE},
    {"id": "v11-unicode", "question": "¿Qué significa «año» en el título?", "previous": None,
     "blocks": [{"document_name": "glosario.txt", "heading_path": "Ñ", "page": None, "text": "Año: periodo de 365 días."}], "page": PDF_PAGE},
    {"id": "v12-empty-previous-answer", "question": "¿Seguro?", "previous": ["¿Hay tablas?", ""], "blocks": BLOCKS[:1], "page": PDF_PAGE},
]


def text_label(block):
    """The label of a text source, as visual_cli._sources_and_parts writes it."""
    where = " › ".join(x for x in (block["document_name"], block["heading_path"]) if x is not None)
    page = f" (p. {block['page']})" if block["page"] is not None else ""
    return f"{where}{page}"


def page_label(page):
    """«Nombre, página p» for a PDF page; only the name for a loose image (visual_cli label)."""
    return f"{page['document_name']}, página {page['page']}" if page["page"] is not None else page["document_name"]


def parts_of(case):
    sources = [visual_prompt.Source("text", text_label(b), frozenset(), text=b["text"]) for b in case["blocks"]]
    page = case["page"]
    sources.append(visual_prompt.Source("page", page_label(page), frozenset(), images=(page["image"].encode("ascii"),)))
    previous = tuple(case["previous"]) if case["previous"] is not None else None
    parts = visual_prompt.build_parts(case["question"], sources, previous)
    return [{"text": p.text} if isinstance(p, Text) else {"image": p.data.decode("ascii")} for p in parts
            if isinstance(p, (Text, Image))]


def render():
    rows = [{**case, "parts": parts_of(case)} for case in CASES]
    return "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows)


def main(argv=None):
    check = "--check" in (sys.argv[1:] if argv is None else argv)
    text = render()
    if check:
        if not GOLDEN.exists() or GOLDEN.read_text(encoding="utf-8") != text:
            raise SystemExit(f"out of date: {GOLDEN.name}")
        print("visual prompt golden up to date")
        return
    GOLDEN.write_text(text, encoding="utf-8", newline="\n")
    print(f"wrote {text.count(chr(10))} rows -> {GOLDEN}")


if __name__ == "__main__":
    main()

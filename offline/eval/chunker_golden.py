"""golden/chunker_golden.jsonl: input blocks and expected chunks for F06's Kotlin Chunker (research R4).

Synthetic edge cases plus the first blocks of 20 corpus documents, chunked with the decided target and
maximum. F06 must reproduce every chunk exactly, as F04 did with the normalizer golden.
"""

import json
from pathlib import Path

from eval import builds, corpus
from eval.chunker import Chunker
from eval.extract import RawBlock

OFFLINE = Path(__file__).resolve().parents[1]
GOLDEN = OFFLINE / "golden" / "chunker_golden.jsonl"
CONFIG = OFFLINE / "config" / "rag_parameters.json"
CORPUS_DOCS = 20
CHARS_PER_DOC = 8_000


def synthetic_cases(target, maximum):
    long_sentence = "Una frase larga sin puntos que no se puede dividir " * (maximum // 40)
    sentences = " ".join(f"Frase número {i} del párrafo largo." for i in range(maximum // 20))
    short = "Párrafo corto."
    return {
        "heading_change": [RawBlock(("A",), "Uno.", None), RawBlock(("A", "B"), "Dos.\n\nTres.", None), RawBlock(("C",), "Cuatro.", 4)],
        "overlap_short_last_paragraph": [RawBlock((), "\n\n".join(["x" * (target // 4)] * 6), 1)],
        "no_overlap_long_last_paragraph": [RawBlock((), "\n\n".join(["y" * (target // 2)] * 4), 1)],
        "split_by_sentence": [RawBlock(("Largo",), sentences, 2)],
        "unsplittable_sentence": [RawBlock((), long_sentence.strip(), None)],
        "pages_and_blank_lines": [RawBlock((), f"{short}\n \n\n{short}", 1), RawBlock((), f"\t{short}  ", 2), RawBlock((), "", 3)],
        "unicode_and_whitespace": [RawBlock(("Título › con símbolo",), "\xa0Texto con NBSP y emoji 😀.\n\n\u2003Otro párrafo\u2003", None)],
    }


def corpus_cases():
    cases = {}
    for doc in corpus.load_manifest()[:CORPUS_DOCS]:
        blocks, size = [], 0
        for block in builds.extracted(doc):
            room = CHARS_PER_DOC - size
            if room <= 0:
                break
            text = block.text
            if len(text) > room:
                cut = text.rfind("\n\n", 0, room)
                text = text[:cut] if cut > 0 else text[:room]
            blocks.append(RawBlock(block.heading_path, text, block.page))
            size += len(text)
        cases[f"corpus:{doc.id}"] = blocks
    return cases


def generate(target, maximum, path=GOLDEN):
    chunker = Chunker(target, maximum)
    lines = []
    for name, blocks in {**synthetic_cases(target, maximum), **corpus_cases()}.items():
        chunks = chunker.chunk(blocks)
        lines.append({
            "case": name, "target_chars": target, "max_chars": maximum,
            "blocks": [{"heading_path": list(b.heading_path), "text": b.text, "page": b.page} for b in blocks],
            "chunks": [{"ordinal": c.ordinal, "heading_path": c.heading_path, "content": c.content, "page": c.page} for c in chunks],
        })
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(line, ensure_ascii=False) + "\n" for line in lines), encoding="utf-8")
    return len(lines), sum(len(line["chunks"]) for line in lines)


def decided_params():
    params = json.loads(CONFIG.read_text(encoding="utf-8"))
    return params["chunk_target_chars"], params["chunk_max_chars"]

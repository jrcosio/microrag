"""How much of the evaluation's evidence the phone's PDF extraction keeps (F06 T068, research R12).

The app extracts PDF text with the system reader (PDFium), the evaluation with pypdf. For the PDFs
of the corpus, the share of test-set evidences found in each extraction says how representative
the F05 figures are for the app. Input: the JSONL written by CorpusPdfExtractionTest, one line per
page with the file name and the page block of the app ("block", or null for a page without text).
"""

import json
from collections import defaultdict

from eval import builds, corpus, testset
from eval.extract import document_text


def load_device_text(path):
    pages = defaultdict(list)
    for line in path.read_text(encoding="utf-8").split("\n"):
        if line.strip():
            row = json.loads(line)
            pages[row["file"]].append((row["page"], row["block"]))
    return {name: "\n\n".join(block for _, block in sorted(rows) if block) for name, rows in pages.items()}


def coverage(device_path):
    """Per PDF document: evidences, found with pypdf, found on the device, and the text sizes."""
    device = load_device_text(device_path)
    docs = {d.id: d for d in corpus.load_manifest() if d.format == "pdf"}
    evidences = defaultdict(list)
    for q in testset.load():
        if q.review != "discarded" and q.has_answer and q.doc in docs and q.evidence:
            evidences[q.doc].append(testset.norm_ws(q.evidence))
    rows = []
    for doc_id, doc in sorted(docs.items()):
        if doc.file_name not in device:
            continue
        desktop = testset.norm_ws(document_text(builds.extracted(doc)))
        phone = testset.norm_ws(device[doc.file_name])
        wanted = evidences.get(doc_id, [])
        rows.append({
            "doc": doc_id,
            "evidences": len(wanted),
            "pypdf": sum(e in desktop for e in wanted),
            "device": sum(e in phone for e in wanted),
            "pypdf_chars": len(desktop),
            "device_chars": len(phone),
        })
    total = {key: sum(r[key] for r in rows) for key in ("evidences", "pypdf", "device", "pypdf_chars", "device_chars")}
    return rows, total

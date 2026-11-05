"""Visual indexes of F18 (research R10, data-model §1.4).

- The text index of the visual corpus is F05's construction with the app's chunking (`build visual-text`).
- Chunks are mapped to the pages their text covers, found in the per-page extraction blocks: the `page` stored
  on a chunk is the page where its buffer started and goes stale after an overlap.
- Page vectors: each page or image, rendered at a resolution and encoded as JPEG (LiteRT-LM does not take WebP),
  embedded with the 440M at a number of tokens per image. The raw 768 vector is stored; truncation and L2 are
  done when loading, so 256 and 512 come from the same build.
"""

import json
import re
import sqlite3
import struct
from dataclasses import dataclass
from pathlib import Path

from embedding_text import truncate_and_normalize
from eval import builds, corpus, raster

VISUAL_DIR = builds.BUILD / "visual"
CHUNK_PAGES = VISUAL_DIR / "chunk-pages.json"
# F05's corpus for the text loss (research R11): only its 6 PDFs gain pages; MD and TXT stay text only.
F05_DIR = VISUAL_DIR / "f05"
F05_CHUNK_PAGES = F05_DIR / "chunk-pages.json"
SNIPPET = 40


def _squash(text):
    return re.sub(r"\s+", " ", text).strip()


def pages_of(content, blocks, fallback=None):
    """Pages of [blocks] that the chunk [content] covers: from the page holding its start to the one holding its end."""
    pages = [(block.page, _squash(block.text)) for block in blocks if block.page]
    text = _squash(content)
    head, tail = text[:SNIPPET], text[-SNIPPET:]
    first = next((i for i, (_, page) in enumerate(pages) if head in page), None)
    if first is None:
        # A chunk can start at the very end of a page.
        first = next((i for i in range(len(pages) - 1) if head in pages[i][1] + " " + pages[i + 1][1]), None)
    if first is None:
        return [fallback] if fallback else []
    for last in range(first, len(pages)):
        if tail in " ".join(page for _, page in pages[first:last + 1]):
            return [number for number, _ in pages[first:last + 1]]
    return [pages[first][0]]


def chunk_pages(index_path, docs, docs_dir, out=CHUNK_PAGES):
    """{chunk_id: [pages]} for the text index at [index_path], written to [out]."""
    from eval import index

    by_sha = {doc.sha256: doc for doc in docs}
    db = index.connect(index_path)
    try:
        rows = db.execute(
            "SELECT c.id, c.content, c.page, d.content_hash FROM chunks c JOIN documents d ON d.id = c.document_id ORDER BY c.id"
        ).fetchall()
    finally:
        db.close()
    blocks = {}
    mapping = {}
    for chunk_id, content, page, sha in rows:
        if sha not in blocks:
            blocks[sha] = builds.extracted(by_sha[sha], docs_dir)
        mapping[chunk_id] = pages_of(content, blocks[sha], fallback=page)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_text(json.dumps({str(k): v for k, v in mapping.items()}), encoding="utf-8")
    return mapping


def load_chunk_pages(path=CHUNK_PAGES):
    return {int(k): v for k, v in json.loads(Path(path).read_text(encoding="utf-8")).items()}


@dataclass(frozen=True)
class PageRow:
    doc_id: str
    doc_sha: str
    page: int
    vector: list


class PageStore:
    """Raw page vectors in SQLite; resumable, one row per (document, page)."""

    def __init__(self, path):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(str(path))
        self.db.execute("CREATE TABLE IF NOT EXISTS pages (doc_id TEXT NOT NULL, doc_sha TEXT NOT NULL, page INTEGER NOT NULL, "
                        "full BLOB NOT NULL, PRIMARY KEY (doc_id, page))")

    def keys(self):
        return set(self.db.execute("SELECT doc_id, page FROM pages").fetchall())

    def has(self, doc_id, page):
        return self.db.execute("SELECT 1 FROM pages WHERE doc_id = ? AND page = ?", (doc_id, page)).fetchone() is not None

    def prune(self, current):
        """Drops the rows of documents no longer in the corpus or whose bytes changed; [current] is {doc_id: sha}."""
        stale = [(doc_id, page) for doc_id, sha, page in self.db.execute("SELECT doc_id, doc_sha, page FROM pages")
                 if current.get(doc_id) != sha]
        self.db.executemany("DELETE FROM pages WHERE doc_id = ? AND page = ?", stale)
        self.db.commit()
        return len(stale)

    def add(self, doc_id, doc_sha, page, vector):
        self.db.execute("INSERT OR REPLACE INTO pages VALUES (?, ?, ?, ?)",
                        (doc_id, doc_sha, page, struct.pack(f"<{len(vector)}f", *vector)))
        self.db.commit()

    def close(self):
        self.db.close()


def load_pages(path, dims):
    db = sqlite3.connect(str(path))
    try:
        rows = db.execute("SELECT doc_id, doc_sha, page, full FROM pages ORDER BY doc_id, page").fetchall()
    finally:
        db.close()
    return [PageRow(doc_id, sha, page, truncate_and_normalize(list(struct.unpack(f"<{len(blob) // 4}f", blob)), dims))
            for doc_id, sha, page, blob in rows]


def page_bytes(doc, docs_dir, page, px):
    """The page (or the image) as the models receive it: rendered at [px] on its long side, JPEG at quality 85.

    LiteRT-LM 0.18.1 does not decode WebP input images (checked on the desktop: PNG and JPEG embed, WebP fails),
    so WebP is only measured as a storage size (research R6)."""
    path = docs_dir / doc.file_name
    image = raster.render_page(path, page, px) if doc.format == "pdf" else raster.scale_image(path, px)
    return raster.encode(image, "jpeg")


def page_numbers(doc, docs_dir):
    return range(1, raster.page_count(docs_dir / doc.file_name) + 1) if doc.format == "pdf" else range(1, 2)


def embed_pages(pages, embed, path):
    """Embeds (doc_id, doc_sha, page, bytes) not yet in [path]; returns how many were embedded."""
    store = PageStore(path)
    done = 0
    try:
        for doc_id, sha, page, data in pages:
            if store.has(doc_id, page):
                continue
            store.add(doc_id, sha, page, embed(data))
            done += 1
    finally:
        store.close()
    return done


def pages_path(px, tokens, corpus_name="visual"):
    folder = VISUAL_DIR if corpus_name == "visual" else F05_DIR
    return folder / f"pages-{px}-{tokens}.sqlite"


def page_documents(corpus_name="visual"):
    """The documents with pages: the whole visual corpus, or only the PDFs of F05's corpus."""
    source = corpus.CORPORA[corpus_name]
    docs = corpus.load_manifest(source.manifest)
    return source, docs if corpus_name == "visual" else [doc for doc in docs if doc.format == "pdf"]


def build_pages(px, tokens, corpus_name="visual"):
    """Page vectors of a corpus at [px] and [tokens] (`run_eval.py visual-build`, and the text loss on F05)."""
    from eval import models, vision_runtime

    source, docs = page_documents(corpus_name)
    corpus.verify(docs, source.docs)
    path = pages_path(px, tokens, corpus_name)
    engine = vision_runtime.open_image_embedder(models.verify(models.VISUAL_EMBEDDING), tokens, cache_dir=builds.BUILD / "litert-cache")

    store = PageStore(path)
    store.prune({doc.id: doc.sha256 for doc in docs})
    done = store.keys()
    store.close()

    def stream():
        for doc in docs:
            for page in page_numbers(doc, source.docs):
                if (doc.id, page) not in done:
                    yield doc.id, doc.sha256, page, page_bytes(doc, source.docs, page, px)

    try:
        return embed_pages(stream(), lambda data: vision_runtime.embed_image(engine, data, tokens), path)
    finally:
        engine.close()

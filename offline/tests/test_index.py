"""Evaluation index with the app artifacts (F05 T011, data-model §3)."""

import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from embedding_text import DOCUMENT, truncate_and_normalize  # noqa: E402
from eval import appconfig, fts_variants, index  # noqa: E402
from eval.extract import RawBlock  # noqa: E402
from normalizer import fts_query, normalize  # noqa: E402


def fake_embedding(text):
    """Deterministic 768-component vector from the text (no model needed)."""
    out, seed = [], hashlib.sha256(text.encode("utf-8")).digest()
    while len(out) < 768:
        seed = hashlib.sha256(seed).digest()
        out.extend(b / 127.5 - 1.0 for b in seed)
    return out[:768]


DOCS = [
    index.IndexDocument("doc-a", "Manual de bombas", "a" * 64, "text/markdown", 100, None, [
        RawBlock(("Mantenimiento",), "Las bombas hidráulicas se revisan cada 500 horas.\n\nEl aceite se cambia cada año.", None),
        RawBlock(("Seguridad",), "Use guantes y gafas protectoras.", None),
    ]),
    index.IndexDocument("doc-b", "Guía de facturas", "b" * 64, "application/pdf", 200, 2, [
        RawBlock((), "Las facturas se conservan durante cuatro años.", 1),
        RawBlock((), "El plazo de presentación termina el día 20.", 2),
    ]),
]


class IndexTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.inputs = []

        def embed(text):
            self.inputs.append(text)
            return fake_embedding(text)

        self.embed = embed

    def tearDown(self):
        self.tmp.cleanup()

    def build(self, name="ref.db", header=True):
        path = Path(self.tmp.name, name)
        index.build_index(path, DOCS, target_chars=60, max_chars=200, contextual_header=header,
                          chars_per_token=4.5, embed=self.embed, build_info={"variant": name})
        return path

    def test_schema_meta_and_normalized_columns(self):
        db = index.connect(self.build())
        tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
        self.assertTrue({"rag_meta", "documents", "chunks", "chunks_fts", "eval_full_embeddings", "eval_build"} <= tables)
        self.assertEqual(dict(db.execute("SELECT key, value FROM rag_meta")), appconfig.expected_meta())
        rows = db.execute(
            "SELECT d.display_name, c.heading_path, c.content, c.title_norm, c.body_norm, c.token_estimate, d.source "
            "FROM chunks c JOIN documents d ON d.id = c.document_id ORDER BY c.id").fetchall()
        for name, heading, content, title_norm, body_norm, tokens, source in rows:
            self.assertEqual(title_norm, normalize(" › ".join(x for x in (name, heading) if x)))
            self.assertEqual(body_norm, normalize(content))
            self.assertEqual(tokens, -(-len(content) // 4.5))
            self.assertEqual(source, "user")

    def test_full_vectors_and_truncated_embedding(self):
        db = index.connect(self.build())
        for chunk_id, blob in db.execute("SELECT chunk_id, v768 FROM eval_full_embeddings"):
            full = list(struct.unpack("<768f", blob))
            query = struct.pack("<256f", *truncate_and_normalize(full, 256))
            hit = db.execute("SELECT rowid, distance FROM vector_full_scan('chunks', 'embedding', ?, 1)", (query,)).fetchone()
            self.assertEqual(hit[0], chunk_id)
            self.assertAlmostEqual(hit[1], 0.0, places=5)

    def test_contextual_header_only_changes_the_embedded_text(self):
        self.build("ref.db", header=True)
        with_header = list(self.inputs)
        self.inputs.clear()
        self.build("nohdr.db", header=False)
        self.assertTrue(all(t.startswith(DOCUMENT) for t in with_header + self.inputs))
        self.assertTrue(any("Manual de bombas › Mantenimiento\n" in t for t in with_header))
        self.assertFalse(any("Manual de bombas" in t for t in self.inputs))

    def test_dimension_variant(self):
        ref = self.build()
        dim = Path(self.tmp.name, "dim128.db")
        index.derive_dims(ref, dim, 128)
        db = index.connect(dim)
        self.assertEqual(dict(db.execute("SELECT key, value FROM rag_meta"))["embedding_dims"], "128")
        chunk_id, blob = db.execute("SELECT chunk_id, v768 FROM eval_full_embeddings LIMIT 1").fetchone()
        query = struct.pack("<128f", *truncate_and_normalize(list(struct.unpack("<768f", blob)), 128))
        self.assertEqual(db.execute("SELECT rowid FROM vector_full_scan('chunks', 'embedding', ?, 1)", (query,)).fetchone()[0], chunk_id)

    def test_fts_variants(self):
        ref = self.build()
        raw = Path(self.tmp.name, "fts_raw.db")
        index.derive_fts(ref, raw, "raw")
        db = index.connect(raw)
        body = db.execute("SELECT body_norm FROM chunks WHERE content LIKE 'Use guantes%'").fetchone()[0]
        self.assertEqual(body, "Use guantes y gafas protectoras.")
        self.assertTrue(db.execute("SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ?", (fts_variants.query("raw", "las gafas"),)).fetchall())
        ref_db = index.connect(ref)
        self.assertTrue(ref_db.execute("SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ?", (fts_query("bombas"),)).fetchall())

    def test_build_is_deterministic(self):
        first = index.fingerprint(index.connect(self.build("a.db")))
        second = index.fingerprint(index.connect(self.build("b.db")))
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()

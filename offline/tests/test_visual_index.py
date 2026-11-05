"""Visual indexes: chunks to pages and page vectors (F18 T021, research R10)."""

import math
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import builds, visual_index  # noqa: E402
from eval.extract import RawBlock, extract  # noqa: E402


def fake_vector(seed):
    return [((seed * 7 + i * 13) % 23) / 23.0 + 0.01 for i in range(768)]


class ChunkPagesTest(unittest.TestCase):
    pages = [
        RawBlock((), "Uno dos tres cuatro cinco seis siete ocho nueve diez.", 1),
        RawBlock((), "Once doce trece catorce quince dieciséis.", 2),
        RawBlock((), "Diecisiete dieciocho diecinueve veinte.", 4),
    ]

    def test_a_chunk_inside_one_page(self):
        self.assertEqual(visual_index.pages_of("cuatro cinco seis siete", self.pages), [1])

    def test_a_chunk_across_pages_covers_them_all(self):
        self.assertEqual(visual_index.pages_of("nueve diez. Once doce trece catorce quince dieciséis. Diecisiete dieciocho", self.pages), [1, 2, 4])

    def test_whitespace_differences_do_not_matter(self):
        self.assertEqual(visual_index.pages_of("Once   doce\ntrece", self.pages), [2])

    def test_a_chunk_not_found_falls_back_to_its_page(self):
        self.assertEqual(visual_index.pages_of("texto que no está", self.pages, fallback=4), [4])
        self.assertEqual(visual_index.pages_of("texto que no está", self.pages), [])


class ImagesHaveNoTextTest(unittest.TestCase):
    def test_images_extract_no_blocks(self):
        with tempfile.TemporaryDirectory() as tmp:
            image = Path(tmp, "x.png")
            image.write_bytes(b"\x89PNG")
            self.assertEqual(extract(image, "png"), [])
            self.assertEqual(extract(image, "jpg"), [])

    def test_the_visual_text_index_uses_the_app_chunking(self):
        self.assertEqual(builds.VARIANTS["visual-text"]["corpus"], "visual")
        self.assertEqual(builds.corpus_of("visual-text").name, "visual")


class PageVectorsTest(unittest.TestCase):
    def test_vectors_round_trip_and_truncate(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp, "pages.sqlite")
            store = visual_index.PageStore(db)
            store.add("doc-a", "sha-a", 1, fake_vector(1))
            store.add("doc-a", "sha-a", 2, fake_vector(2))
            store.add("img-b", "sha-b", 1, fake_vector(3))
            self.assertTrue(store.has("doc-a", 2))
            self.assertFalse(store.has("doc-a", 3))
            store.close()
            rows = visual_index.load_pages(db, dims=256)
            wide = visual_index.load_pages(db, dims=512)
        self.assertEqual([(r.doc_id, r.page) for r in rows], [("doc-a", 1), ("doc-a", 2), ("img-b", 1)])
        for row in rows:
            self.assertEqual(len(row.vector), 256)
            self.assertAlmostEqual(math.sqrt(sum(v * v for v in row.vector)), 1.0, places=5)
        self.assertEqual(len(wide[0].vector), 512)

    def test_page_vectors_skip_what_is_already_embedded(self):
        calls = []

        def embed(data):
            calls.append(len(data))
            return fake_vector(len(calls))

        pages = [("doc-a", "sha-a", 1, b"one"), ("doc-a", "sha-a", 2, b"two")]
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp, "pages.sqlite")
            visual_index.embed_pages(pages[:1], embed, db)
            visual_index.embed_pages(pages, embed, db)
            self.assertEqual(len(visual_index.load_pages(db, dims=256)), 2)
        self.assertEqual(calls, [3, 3])

    def test_rows_of_changed_or_removed_documents_are_pruned(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = visual_index.PageStore(Path(tmp, "pages.sqlite"))
            store.add("doc-a", "sha-old", 1, fake_vector(1))
            store.add("doc-a", "sha-old", 2, fake_vector(2))
            store.add("img-b", "sha-b", 1, fake_vector(3))
            store.add("gone", "sha-g", 1, fake_vector(4))
            self.assertEqual(store.prune({"doc-a": "sha-new", "img-b": "sha-b"}), 3)
            self.assertEqual(store.keys(), {("img-b", 1)})
            store.close()


if __name__ == "__main__":
    unittest.main()

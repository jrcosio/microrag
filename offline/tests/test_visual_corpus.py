"""The visual corpus: its manifest, rebuilt scans, simulated photos and SOURCES.md (F18 T019, research R8)."""

import io
import sys
import tempfile
import time
import unittest
from pathlib import Path

from PIL import Image
import pypdfium2 as pdfium

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import corpus  # noqa: E402

FIXTURE = Path(__file__).resolve().parents[2] / "app" / "src" / "androidTest" / "assets" / "pdf" / "text_3_pages.pdf"


def entry(**changes):
    base = {
        "id": "ine-ipc-2026-09", "title": "IPC septiembre 2026", "format": "pdf", "kind": "estadistica",
        "source_url": "https://www.ine.es/dyngs/Prensa/IPC0926.pdf", "publisher": "INE", "license": "INE: CC BY 4.0",
        "attribution": "Fuente: Sitio web del INE: www.ine.es", "source_updated": "2026-10-14", "retrieved": "2026-11-05",
        "transformations": "ninguna", "fetch": {"type": "pdf", "url": "https://www.ine.es/dyngs/Prensa/IPC0926.pdf"},
        "sha256": "pending",
    }
    base.update(changes)
    return base


class ManifestTest(unittest.TestCase):
    def test_the_visual_corpus_is_registered_apart_from_the_test_assets(self):
        visual = corpus.CORPORA["visual"]
        self.assertEqual(visual.manifest.parent.name, "corpus_visual")
        self.assertNotEqual(visual.manifest.parent, corpus.CORPUS)
        self.assertNotIn(corpus.CORPUS, visual.manifest.parents)

    def test_images_kinds_and_licenses_of_the_visual_corpus(self):
        docs = corpus.parse_manifest([
            entry(),
            entry(id="commons-map", format="jpg", kind="imagen", license="CC BY-SA 4.0",
                  fetch={"type": "image", "url": "https://upload.wikimedia.org/x.jpg"}),
            entry(id="gaceta", kind="escaneado", license="BOE: reutilización con atribución",
                  fetch={"type": "scan", "url": "https://boe.es/x.pdf", "pages": [1, 2], "dpi": 130}),
            entry(id="photo", format="jpg", kind="imagen", license="INE: CC BY 4.0",
                  fetch={"type": "photo", "from": "ine-ipc-2026-09", "page": 1, "seed": 3, "recipe": "v1"}),
        ])
        self.assertEqual([doc.mime_type for doc in docs], ["application/pdf", "image/jpeg", "application/pdf", "image/jpeg"])

    def test_an_unknown_license_is_refused(self):
        with self.assertRaises(ValueError):
            corpus.parse_manifest([entry(license="Todos los derechos reservados")])


class ScanTest(unittest.TestCase):
    def test_a_rebuilt_scan_has_no_text_layer_and_the_same_bytes_every_time(self):
        original = corpus.http_get
        corpus.http_get = lambda url, accept=None, pause=0.0: FIXTURE.read_bytes()
        try:
            doc = corpus.parse_manifest([entry(kind="escaneado", fetch={"type": "scan", "url": "x", "pages": [1, 2], "dpi": 60})])[0]
            first = corpus.default_fetcher(doc)
            time.sleep(1.1)  # a different second: nothing may depend on the clock
            second = corpus.default_fetcher(doc)
        finally:
            corpus.http_get = original
        self.assertEqual(first, second)
        rebuilt = pdfium.PdfDocument(first)
        try:
            self.assertEqual(len(rebuilt), 2)
            self.assertEqual(rebuilt[0].get_textpage().get_text_range().strip(), "")
        finally:
            rebuilt.close()


class PhotoTest(unittest.TestCase):
    def test_a_photo_is_made_from_a_corpus_page_and_is_deterministic(self):
        with tempfile.TemporaryDirectory() as tmp:
            docs = Path(tmp)
            (docs / "ine-ipc-2026-09.pdf").write_bytes(FIXTURE.read_bytes())
            doc = corpus.parse_manifest([entry(id="photo", format="jpg", kind="imagen",
                                               fetch={"type": "photo", "from": "ine-ipc-2026-09", "page": 2, "seed": 5,
                                                      "recipe": "v1"})])[0]
            first = corpus.fetch_photo(doc.fetch, doc, docs)
            second = corpus.fetch_photo(doc.fetch, doc, docs)
        self.assertEqual(first, second)
        self.assertEqual(Image.open(io.BytesIO(first)).format, "JPEG")


class SourcesTest(unittest.TestCase):
    def test_sources_name_each_file_with_its_license(self):
        docs = corpus.parse_manifest([entry(sha256="a" * 64)])
        text = corpus.sources_markdown(docs, corpus.CORPORA["visual"].purpose)
        self.assertIn("`docs/ine-ipc-2026-09.pdf`", text)
        self.assertIn("INE: CC BY 4.0", text)
        self.assertIn("F18", text)


if __name__ == "__main__":
    unittest.main()

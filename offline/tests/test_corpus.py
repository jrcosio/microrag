"""Corpus manifest, verified fetch and SOURCES.md (F05 T009, FR-020)."""

import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import corpus  # noqa: E402


def entry(**overrides):
    base = {
        "id": "boe-lau", "title": "Ley 29/1994, de Arrendamientos Urbanos", "format": "md", "kind": "normativa",
        "source_url": "https://www.boe.es/buscar/act.php?id=BOE-A-1994-26003",
        "publisher": "Agencia Estatal Boletín Oficial del Estado", "license": "BOE: reutilización con atribución",
        "attribution": "Basado en datos de la Agencia Estatal Boletín Oficial del Estado.",
        "source_updated": "2024-05-23", "retrieved": "2026-10-27",
        "transformations": "XML del BOE → Markdown; sin notas de modificación",
        "fetch": {"type": "boe", "boe_id": "BOE-A-1994-26003"},
        "sha256": hashlib.sha256(b"contenido").hexdigest(),
    }
    return {**base, **overrides}


class ManifestTest(unittest.TestCase):
    def test_valid_entry_loads(self):
        docs = corpus.parse_manifest([entry()])
        self.assertEqual(docs[0].id, "boe-lau")
        self.assertEqual(docs[0].file_name, "boe-lau.md")

    def test_missing_field_unknown_license_or_duplicate_id_fail(self):
        for bad in ([{k: v for k, v in entry().items() if k != "attribution"}],
                    [entry(license="Todos los derechos reservados")],
                    [entry(format="docx")],
                    [entry(), entry()]):
            with self.assertRaises(ValueError):
                corpus.parse_manifest(bad)


class FetchTest(unittest.TestCase):
    def test_downloads_only_missing_files_and_checks_the_hash(self):
        calls = []

        def fetcher(doc):
            calls.append(doc.id)
            return b"contenido"

        with tempfile.TemporaryDirectory() as tmp:
            docs_dir = Path(tmp)
            docs = corpus.parse_manifest([entry()])
            corpus.fetch(docs, docs_dir, fetcher)
            corpus.fetch(docs, docs_dir, fetcher)
            self.assertEqual(calls, ["boe-lau"])
            self.assertEqual((docs_dir / "boe-lau.md").read_bytes(), b"contenido")

    def test_hash_mismatch_fails_and_writes_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            docs = corpus.parse_manifest([entry()])
            with self.assertRaises(SystemExit):
                corpus.fetch(docs, Path(tmp), lambda doc: b"otro contenido")
            self.assertFalse((Path(tmp) / "boe-lau.md").exists())


class SourcesTest(unittest.TestCase):
    def test_sources_lists_attribution_license_and_transformations(self):
        text = corpus.sources_markdown(corpus.parse_manifest([entry()]))
        for expected in ("Ley 29/1994", "Basado en datos de la Agencia Estatal", "BOE: reutilización con atribución",
                         "XML del BOE → Markdown", "2024-05-23"):
            self.assertIn(expected, text)


class WikibooksSiteTest(unittest.TestCase):
    """F12 L8: the Wikibooks site comes from the fetch entry, with es by default."""

    def urls(self, spec):
        urls = []
        original = corpus.http_get
        corpus.http_get = lambda url, accept=None, pause=0.0: urls.append(url) or b"Text"
        try:
            corpus.fetch_wikibooks(spec, None)
        finally:
            corpus.http_get = original
        return urls

    def test_spanish_site_by_default_and_english_when_asked(self):
        pages = [{"title": "Book/Page", "revid": 7}]
        self.assertTrue(self.urls({"type": "wikibooks", "pages": pages})[0].startswith("https://es.wikibooks.org/"))
        self.assertTrue(self.urls({"type": "wikibooks", "site": "en", "pages": pages})[0].startswith("https://en.wikibooks.org/"))

    def test_unknown_site_fails(self):
        with self.assertRaises(ValueError):
            self.urls({"type": "wikibooks", "site": "evil.example/", "pages": [{"title": "P", "revid": 1}]})


class CorporaTest(unittest.TestCase):
    def test_spanish_corpus_keeps_its_paths_and_english_has_its_own(self):
        self.assertEqual((corpus.CORPORA["es"].manifest, corpus.CORPORA["es"].docs, corpus.CORPORA["es"].sources),
                         (corpus.MANIFEST, corpus.DOCS, corpus.SOURCES))
        english = corpus.CORPORA["en"]
        self.assertEqual((english.manifest, english.docs, english.sources),
                         (corpus.CORPUS / "en" / "manifest.json", corpus.CORPUS / "en" / "docs", corpus.CORPUS / "en" / "SOURCES.md"))

    def test_english_manifest_has_three_wikibooks_under_cc_by_sa(self):
        docs = corpus.load_manifest(corpus.CORPORA["en"].manifest)
        self.assertEqual(len(docs), 3)
        for doc in docs:
            self.assertEqual((doc.license, doc.format, doc.fetch["type"], doc.fetch["site"]), ("CC BY-SA 4.0", "txt", "wikibooks", "en"))
            self.assertTrue(all(isinstance(page["revid"], int) for page in doc.fetch["pages"]))
        corpus.verify(docs, corpus.CORPORA["en"].docs)

    def test_sources_files_are_generated_from_their_manifest(self):
        for source in corpus.CORPORA.values():
            expected = corpus.sources_markdown(corpus.load_manifest(source.manifest), source.purpose)
            self.assertEqual(source.sources.read_text(encoding="utf-8"), expected, source.name)


class WikitextTest(unittest.TestCase):
    def test_markup_is_removed_and_code_samples_kept(self):
        wikitext = (
            "== Título ==\n{{Plantilla|x={{anidada}}}}Texto con '''negrita''' y [[Enlace|enlace]].<ref>nota</ref>\n"
            "{|\n| celda\n|}\n[[Archivo:foto.png|miniatura|pie]]\n* punto\n<noinclude>Nav</noinclude><p>Párrafo</p>\n"
            "Código: &lt;b&gt; <em:id>x</em:id> <math>a^2</math>"
        )
        self.assertEqual(
            corpus.wikitext_to_text(wikitext),
            "Título\nTexto con negrita y enlace.\n\npunto\nNavPárrafo\nCódigo: <b> <em:id>x</em:id> a^2",
        )


if __name__ == "__main__":
    unittest.main()

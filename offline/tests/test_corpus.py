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


if __name__ == "__main__":
    unittest.main()

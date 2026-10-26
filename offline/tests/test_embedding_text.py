"""embedding_text.py against EmbeddingPrefixes and EmbeddingMath (F04 T033)."""

import math
import re
import sys
import unittest
from pathlib import Path

OFFLINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(OFFLINE))

import embedding_text  # noqa: E402
from embedding_text import document_input, query_input, to_float32, truncate_and_normalize  # noqa: E402

PREFIXES_KT = OFFLINE.parent / "app/src/main/java/com/lisdatasolutions/microrag/rag/runtime/EmbeddingPrefixes.kt"


class InputsTest(unittest.TestCase):
    def test_prefixes_match_the_kotlin_source(self):
        source = PREFIXES_KT.read_text(encoding="utf-8")
        self.assertEqual(re.search(r'DOCUMENT = "([^"]*)"', source).group(1), embedding_text.DOCUMENT)
        self.assertEqual(re.search(r'QUERY = "([^"]*)"', source).group(1), embedding_text.QUERY)

    def test_query_is_trimmed_before_the_prefix(self):
        self.assertEqual(query_input("  hola \n"), "task: search query | text: hola")

    def test_document_joins_trimmed_header_and_content(self):
        self.assertEqual(
            document_input(" Texto del bloque. ", header=" Informe › Embeddings "),
            "task: search result | text: Informe › Embeddings\nTexto del bloque.",
        )

    def test_document_without_header_has_no_leading_newline(self):
        self.assertEqual(document_input("Texto", header="  "), "task: search result | text: Texto")


class TruncationTest(unittest.TestCase):
    def test_keeps_256_float32_components_with_unit_norm(self):
        vector = [float(i % 7) - 3.0 + 0.001 * i for i in range(768)]

        result = truncate_and_normalize(vector)

        self.assertEqual(len(result), 256)
        self.assertTrue(all(to_float32(v) == v for v in result))
        self.assertAlmostEqual(math.sqrt(sum(v * v for v in result)), 1.0, delta=1e-6)

    def test_rejects_short_or_zero_vectors(self):
        with self.assertRaises(ValueError):
            truncate_and_normalize([1.0] * 255)
        with self.assertRaises(ValueError):
            truncate_and_normalize([0.0] * 300)


if __name__ == "__main__":
    unittest.main()

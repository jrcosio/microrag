"""normalizer.py against the report, the Kotlin source and the golden (F04 T026)."""

import hashlib
import json
import re
import sys
import tempfile
import unittest
from pathlib import Path

OFFLINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(OFFLINE))

import gen_normalizer_golden  # noqa: E402
from normalizer import VERSION, STOP, fts_query, normalize  # noqa: E402

GOLDEN = OFFLINE / "golden" / "normalizer_golden.jsonl"
KOTLIN = OFFLINE.parent / "app/src/main/java/com/lisdatasolutions/microrag/rag/text/SpanishText.kt"

# Pinned per normalizer version (rule O6): a new golden without a new VERSION fails here and on the JVM.
GOLDEN_SHA256 = {1: "d118b8e6ece31a64b03f1ca557e4261983b29f2ef6574d548fb13e8c5d1a10f9"}


class ReportExamplesTest(unittest.TestCase):
    def test_report_examples(self):
        self.assertEqual(normalize("clientes"), "client")
        self.assertEqual(normalize("cliente"), "client")
        self.assertEqual(normalize("motores"), "motor")
        self.assertEqual(normalize("luces"), "luz")
        self.assertEqual(normalize("R-1234"), "r-1234")

    def test_same_cases_as_spanish_text_test(self):
        cases = {
            "Canción": "cancion",
            "AÑO": "ano",
            "pingüino": "pinguin",
            "de la que": "",
            "x k z": "",
            "El motor de la bomba": "motor bomb",
            "casa": "casa",
            "casas": "cas",
            "meses": "mes",
            "abc12": "abc12",
            "modelo2024": "modelo2024",
            "v1.2/a": "v1.2/a",
            "abc_def": "abc_def",
            "abc- -abc": "abc abc",
            "abc--def": "abc def",
        }
        for text, expected in cases.items():
            self.assertEqual(normalize(text), expected, text)

    def test_fts_query(self):
        self.assertEqual(fts_query("clientes y motores"), '"client" OR "motor"')
        self.assertEqual(fts_query("cliente clientes"), '"client"')
        self.assertEqual(fts_query('R-1234 "manual"'), '"r-1234" OR "manual"')
        self.assertEqual(fts_query("de la que"), "")
        terms = fts_query(" ".join(f"t{i:02d}" for i in range(1, 21))).split(" OR ")
        self.assertEqual(len(terms), 16)
        self.assertEqual(terms[-1], '"t16"')


class KotlinSubtletiesTest(unittest.TestCase):
    """F04 research R4: behaviors of the Kotlin code that a naive port would miss."""

    def test_lengths_count_utf16_units(self):
        # U+1D41A is one code point but two UTF-16 units: Kotlin keeps it (length 2 > 1).
        self.assertEqual(normalize("\U0001d41a"), "\U0001d41a")

    def test_only_bmp_nd_characters_are_digits(self):
        self.assertEqual(normalize("modelos٣"), "modelos٣")  # Arabic-Indic digit: Nd, BMP
        self.assertEqual(normalize("\U0001d7d1casas"), "\U0001d7d1cas")  # bold digit: Nd, outside the BMP
        self.assertEqual(normalize("²casas"), "²cas")  # superscript two: No, not a digit

    def test_separators_only_join_alphanumerics(self):
        self.assertEqual(normalize("-abc"), "abc")
        self.assertEqual(normalize("abc-"), "abc")
        self.assertEqual(normalize("casa..perro"), "casa perr")

    def test_stopwords_match_the_kotlin_source(self):
        source = KOTLIN.read_text(encoding="utf-8")
        block = re.search(r"STOP = setOf\((.*?)\)", source, re.S).group(1)
        self.assertEqual(STOP, frozenset(re.findall(r'"([^"]*)"', block)))

    def test_version_matches_the_kotlin_source(self):
        source = KOTLIN.read_text(encoding="utf-8")
        self.assertEqual(int(re.search(r"const val VERSION = (\d+)", source).group(1)), VERSION)


class GoldenTest(unittest.TestCase):
    def test_golden_matches_exactly(self):
        pairs = [json.loads(line) for line in GOLDEN.read_text(encoding="utf-8").splitlines()]
        self.assertGreaterEqual(len(pairs), gen_normalizer_golden.MIN_PAIRS)
        mismatches = [p for p in pairs if normalize(p["in"]) != p["out"]]
        self.assertEqual(mismatches, [])

    def test_golden_hash_is_pinned_for_the_version(self):
        digest = hashlib.sha256(GOLDEN.read_bytes()).hexdigest()
        self.assertEqual(GOLDEN_SHA256.get(VERSION), digest)

    def test_generator_is_deterministic(self):
        with tempfile.TemporaryDirectory() as tmp:
            first, second = Path(tmp, "a.jsonl"), Path(tmp, "b.jsonl")
            gen_normalizer_golden.main(["--out", str(first)])
            gen_normalizer_golden.main(["--out", str(second)])
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(first.read_bytes(), GOLDEN.read_bytes())


if __name__ == "__main__":
    unittest.main()

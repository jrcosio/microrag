"""The extraction golden is up to date with offline/eval/extract.py (F06 T005, rule X1)."""

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gen_extraction_golden  # noqa: E402


class ExtractionGoldenTest(unittest.TestCase):
    def test_committed_golden_matches_the_extractor(self):
        self.assertEqual(gen_extraction_golden.TARGET.read_text(encoding="utf-8"), gen_extraction_golden.render())

    def test_covers_every_kind_and_the_corpus(self):
        cases = [json.loads(line) for line in gen_extraction_golden.TARGET.read_text(encoding="utf-8").split("\n") if line]
        kinds = {c["kind"] for c in cases}
        self.assertEqual(kinds, {"decode", "text", "markdown", "pdf_page"})
        corpus = [c["case"] for c in cases if c["case"].startswith("corpus:")]
        self.assertEqual(sum(":p" not in name for name in corpus), 15)  # 9 TXT and 6 MD excerpts
        self.assertGreaterEqual(sum(":p" in name for name in corpus), 50)
        self.assertEqual(len({c["case"] for c in cases}), len(cases))


if __name__ == "__main__":
    unittest.main()

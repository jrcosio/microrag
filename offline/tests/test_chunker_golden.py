"""golden/chunker_golden.jsonl is what chunker.py produces for its inputs (F05 T047)."""

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval.chunker import Chunker  # noqa: E402
from eval.extract import RawBlock  # noqa: E402

GOLDEN = Path(__file__).resolve().parents[1] / "golden" / "chunker_golden.jsonl"


@unittest.skipUnless(GOLDEN.exists(), "chunker golden not generated yet")
class ChunkerGoldenTest(unittest.TestCase):
    def test_every_case_matches(self):
        cases = [json.loads(line) for line in GOLDEN.read_text(encoding="utf-8").splitlines()]
        self.assertGreaterEqual(len(cases), 20)
        for case in cases:
            blocks = [RawBlock(tuple(b["heading_path"]), b["text"], b["page"]) for b in case["blocks"]]
            chunks = Chunker(case["target_chars"], case["max_chars"]).chunk(blocks)
            produced = [{"ordinal": c.ordinal, "heading_path": c.heading_path, "content": c.content, "page": c.page} for c in chunks]
            self.assertEqual(produced, case["chunks"], case["case"])


if __name__ == "__main__":
    unittest.main()

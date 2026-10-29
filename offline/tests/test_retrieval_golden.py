"""Shape of the retrieval golden and the parity sample (F10 T002, contracts/eval-retrieval.md rule G5).

Regenerating them needs the reference index (offline/build); `gen_retrieval_golden.py --check` does it.
"""

import json
import sys
import unittest
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gen_retrieval_golden  # noqa: E402
from eval import corpus  # noqa: E402
from eval.pipeline import live_questions  # noqa: E402
from eval.retrieval import FUSED_LIMIT, PARAMETERS  # noqa: E402

GOLDEN_FIELDS = {"id", "question", "previous", "follow_up", "search_text", "vector", "fts", "fused", "chunks", "blocks"}
PARITY_FIELDS = {"id", "type", "question", "previous", "doc", "file", "evidence", "desktop_hit"}


def read(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


class RetrievalGoldenShapeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = read(gen_retrieval_golden.GOLDEN)
        cls.params = json.loads(PARAMETERS.read_text(encoding="utf-8"))

    def test_one_row_per_live_question_in_id_order(self):
        ids = [row["id"] for row in self.rows]
        self.assertEqual(ids, sorted(q.id for q in live_questions()))
        self.assertEqual(len(ids), 426)

    def test_fields_and_lists(self):
        for row in self.rows:
            self.assertEqual(set(row), GOLDEN_FIELDS, row["id"])
            self.assertLessEqual(len(row["vector"]), self.params["candidates"])
            self.assertLessEqual(len(row["fts"]), self.params["candidates"])
            self.assertLessEqual(len(row["fused"]), FUSED_LIMIT)
            self.assertTrue(set(row["fused"]) <= set(row["vector"]) | set(row["fts"]), row["id"])
            self.assertEqual(row["search_text"] == row["question"], row["previous"] is None or not row["follow_up"], row["id"])

    def test_every_fused_chunk_and_its_next_are_described(self):
        for row in self.rows:
            chunks = row["chunks"]
            for chunk_id in row["fused"]:
                document_id, ordinal, tokens, next_id = chunks[str(chunk_id)]
                self.assertGreater(tokens, 0)
                if next_id is not None:
                    self.assertEqual(chunks[str(next_id)][:2], [document_id, ordinal + 1], row["id"])

    def test_blocks_of_both_configurations(self):
        limits = {"d034": (self.params["max_blocks"], self.params["budget_tokens"]),
                  "report": (gen_retrieval_golden.REPORT_CONFIG[0], gen_retrieval_golden.REPORT_CONFIG[2])}
        for row in self.rows:
            self.assertEqual(set(row["blocks"]), set(limits), row["id"])
            for name, (max_blocks, budget) in limits.items():
                blocks = row["blocks"][name]
                self.assertLessEqual(len(blocks), max_blocks)
                self.assertLessEqual(sum(b[3] for b in blocks), budget)
                for document_id, first, last, tokens, chunk_ids in blocks:
                    self.assertEqual(len(chunk_ids), last - first + 1, row["id"])
                    self.assertEqual([row["chunks"][str(i)][:2] for i in chunk_ids],
                                     [[document_id, o] for o in range(first, last + 1)], row["id"])
                    self.assertEqual(sum(row["chunks"][str(i)][2] for i in chunk_ids), tokens, row["id"])
            self.assertTrue(row["blocks"]["d034"] or not row["fused"], row["id"])


class RetrievalParityShapeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = read(gen_retrieval_golden.PARITY)

    def test_fixed_sample_of_fifty(self):
        self.assertEqual(len(self.rows), 50)
        self.assertEqual([r["id"] for r in self.rows], sorted(r["id"] for r in self.rows))
        self.assertEqual(Counter(r["type"] for r in self.rows), {"answerable": 40, "followup": 10})

    def test_fields(self):
        files = {d.id: d.file_name for d in corpus.load_manifest()}
        for row in self.rows:
            self.assertEqual(set(row), PARITY_FIELDS, row["id"])
            self.assertEqual(row["file"], files[row["doc"]])
            self.assertTrue(row["evidence"].strip(), row["id"])
            self.assertIn(row["desktop_hit"], (0, 1))
            self.assertEqual(row["previous"] is not None, row["type"] == "followup", row["id"])

    def test_questions_are_in_the_golden(self):
        golden = {row["id"]: row for row in read(gen_retrieval_golden.GOLDEN)}
        for row in self.rows:
            self.assertEqual(golden[row["id"]]["question"], row["question"])
            self.assertEqual(golden[row["id"]]["previous"], row["previous"])


if __name__ == "__main__":
    unittest.main()

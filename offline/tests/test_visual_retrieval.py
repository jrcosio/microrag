"""Page-level retrieval: chunk lists to pages, RRF of two and three lists, and metrics (F18 T027, research R10)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import visual_retrieval  # noqa: E402
from eval.visual_index import PageRow  # noqa: E402


class ToPagesTest(unittest.TestCase):
    def test_each_page_takes_the_best_rank_of_its_chunks(self):
        chunk_pages = {10: [1], 11: [1, 2], 12: [3]}
        chunk_doc = {10: "a", 11: "a", 12: "b"}
        self.assertEqual(visual_retrieval.to_pages([11, 12, 10], chunk_pages, chunk_doc), [("a", 1), ("a", 2), ("b", 3)])

    def test_a_chunk_without_pages_adds_nothing(self):
        self.assertEqual(visual_retrieval.to_pages([10], {10: []}, {10: "a"}), [])


class PageRankingTest(unittest.TestCase):
    def test_pages_by_cosine(self):
        rows = [PageRow("doc-a", "a", 1, [1.0, 0.0]), PageRow("doc-a", "a", 2, [0.0, 1.0]), PageRow("img", "b", 1, [0.6, 0.8])]
        self.assertEqual(visual_retrieval.rank_pages([0.0, 1.0], rows, k=2), [("a", 2), ("b", 1)])


class FusionTest(unittest.TestCase):
    vector = [("a", 1), ("a", 2)]
    fts = [("a", 2), ("c", 1)]
    pages = [("b", 1), ("a", 2)]

    def test_text_fuses_the_two_chunk_lists(self):
        self.assertEqual(visual_retrieval.fuse("texto", self.vector, self.fts, self.pages)[0], ("a", 2))
        self.assertNotIn(("b", 1), visual_retrieval.fuse("texto", self.vector, self.fts, self.pages))

    def test_visual_is_the_page_list(self):
        self.assertEqual(visual_retrieval.fuse("visual", self.vector, self.fts, self.pages), self.pages)

    def test_both_fuses_the_three_lists(self):
        both = visual_retrieval.fuse("ambos", self.vector, self.fts, self.pages)
        self.assertEqual(both[0], ("a", 2))  # in all three lists
        self.assertIn(("b", 1), both)

    def test_unknown_mode(self):
        with self.assertRaises(ValueError):
            visual_retrieval.fuse("mixto", [], [], [])


class MetricsTest(unittest.TestCase):
    def test_page_metrics(self):
        ranked = [("x", 1), ("a", 2), ("y", 1)]
        m = visual_retrieval.page_metrics(ranked, {("a", 2)})
        self.assertEqual(m["recall@1"], 0.0)
        self.assertEqual(m["recall@3"], 1.0)
        self.assertEqual(m["mrr"], 0.5)
        self.assertGreater(m["ndcg@10"], 0.0)

    def test_summary_by_type_and_the_visual_group(self):
        rows = [
            {"type": "tabla", "metrics": {"recall@5": 1.0}},
            {"type": "grafico", "metrics": {"recall@5": 0.0}},
            {"type": "control", "metrics": {"recall@5": 1.0}},
        ]
        summary = visual_retrieval.summarize(rows)
        self.assertEqual(summary["tabla"]["recall@5"], 1.0)
        self.assertEqual(summary["visual"]["recall@5"], 0.5)
        self.assertEqual(summary["all"]["recall@5"], 2 / 3)
        self.assertEqual(summary["visual"]["n"], 2)


if __name__ == "__main__":
    unittest.main()

"""Retrieval metrics computed by hand (F05 T028, research R10)."""

import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import metrics  # noqa: E402
from eval.selector import ContextBlock  # noqa: E402


class MetricsTest(unittest.TestCase):
    def test_recall_at_k(self):
        self.assertEqual(metrics.recall_at([5, 3, 9, 1], {3, 1}, 2), 0.5)
        self.assertEqual(metrics.recall_at([5, 3, 9, 1], {3, 1}, 4), 1.0)
        self.assertEqual(metrics.recall_at([], {3}, 20), 0.0)

    def test_reciprocal_rank(self):
        self.assertEqual(metrics.reciprocal_rank([5, 3, 9], {9, 3}), 0.5)
        self.assertEqual(metrics.reciprocal_rank([5, 3, 9], {7}), 0.0)

    def test_ndcg_at_10(self):
        expected = (1 / math.log2(3)) / (1 / math.log2(2) + 1 / math.log2(3))
        self.assertAlmostEqual(metrics.ndcg_at([4, 2, 8], {2, 7}, 10), expected)
        self.assertEqual(metrics.ndcg_at([2, 7], {2, 7}, 10), 1.0)

    def test_blocks_hit(self):
        blocks = [ContextBlock("Doc", None, None, 1, 3, 5, "texto", 300)]
        self.assertEqual(metrics.blocks_hit(blocks, {(1, 4)}), 1.0)
        self.assertEqual(metrics.blocks_hit(blocks, {(1, 6), (2, 4)}), 0.0)

    def test_mean_over_questions(self):
        self.assertEqual(metrics.mean([1.0, 0.0, 0.5]), 0.5)
        self.assertIsNone(metrics.mean([]))


if __name__ == "__main__":
    unittest.main()

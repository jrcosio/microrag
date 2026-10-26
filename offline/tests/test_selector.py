"""ContextSelector of the report (F05 T027, research R6)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval.selector import ChunkView, ContextSelector  # noqa: E402


def view(doc, ordinal, tokens=100, content=None):
    return ChunkView(id=doc * 100 + ordinal, document_id=doc, ordinal=ordinal, heading_path=None, page=None,
                     content=content or f"Doc {doc} fragmento {ordinal}.", token_estimate=tokens, document_name=f"Doc {doc}")


class SelectorTest(unittest.TestCase):
    def setUp(self):
        self.store = {(d, o): view(d, o) for d in range(1, 5) for o in range(10)}

    def neighbor(self, doc, ordinal):
        return self.store.get((doc, ordinal))

    def test_at_most_three_per_document(self):
        ranked = [self.store[(1, o)] for o in (0, 2, 4, 6)] + [self.store[(2, 0)]]
        blocks = ContextSelector(expand_top=0).select(ranked, self.neighbor)
        self.assertEqual([(b.document_id, b.first_ordinal) for b in blocks], [(1, 0), (1, 2), (1, 4), (2, 0)])

    def test_neighbor_of_the_two_best_is_added_and_merged(self):
        ranked = [self.store[(1, 4)], self.store[(2, 7)], self.store[(3, 1)]]
        blocks = ContextSelector().select(ranked, self.neighbor)
        self.assertEqual([(b.document_id, b.first_ordinal, b.last_ordinal) for b in blocks], [(1, 4, 5), (2, 7, 8), (3, 1, 1)])
        self.assertEqual(blocks[0].tokens, 200)
        self.assertEqual(blocks[0].text, "Doc 1 fragmento 4.\n\nDoc 1 fragmento 5.")

    def test_merge_drops_the_overlapped_paragraph(self):
        a = view(1, 0, content="Primero.\n\nSolape común.")
        b = view(1, 1, content="Solape común.\n\nSegundo.")
        blocks = ContextSelector(expand_top=0).select([a, b], self.neighbor)
        self.assertEqual(blocks[0].text, "Primero.\n\nSolape común.\n\nSegundo.")

    def test_budget_and_block_limit(self):
        ranked = [self.store[(d, 0)] for d in range(1, 5)]
        self.assertEqual(len(ContextSelector(expand_top=0, budget_tokens=250).select(ranked, self.neighbor)), 2)
        self.assertEqual(len(ContextSelector(expand_top=0, max_blocks=3).select(ranked, self.neighbor)), 3)

    def test_blocks_are_ordered_by_document_and_ordinal(self):
        ranked = [self.store[(3, 2)], self.store[(1, 8)], self.store[(3, 0)]]
        blocks = ContextSelector(expand_top=0).select(ranked, self.neighbor)
        self.assertEqual([(b.document_id, b.first_ordinal) for b in blocks], [(1, 8), (3, 0), (3, 2)])


if __name__ == "__main__":
    unittest.main()

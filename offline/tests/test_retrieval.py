"""Hybrid retrieval as planned for the app (F05 T026, research R6, contracts/eval-cli.md E5)."""

import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from embedding_text import truncate_and_normalize  # noqa: E402
from eval import index, retrieval  # noqa: E402
from eval.extract import RawBlock  # noqa: E402
from eval.runs import EvalConfig  # noqa: E402
from test_index import fake_embedding  # noqa: E402


class RrfTest(unittest.TestCase):
    def test_reciprocal_rank_fusion_by_hand(self):
        # k = 60, ranks from 1: a = 1/61 + 1/63, b = 1/62 + 1/61, c = 1/63, d = 1/62.
        fused = retrieval.rrf([["a", "b", "c"], ["b", "d", "a"]], k=60)
        self.assertEqual(fused, ["b", "a", "d", "c"])

    def test_ties_keep_the_best_rank_then_id(self):
        self.assertEqual(retrieval.rrf([["x", "y"], ["y", "x"]], k=60), ["x", "y"])


class FollowUpTest(unittest.TestCase):
    def test_short_or_connector_questions_are_follow_ups(self):
        self.assertTrue(retrieval.is_follow_up("¿Y para empresas?"))
        self.assertTrue(retrieval.is_follow_up("pero entonces cuál es el plazo para presentar la declaración del impuesto anual"))
        # 9 normalized tokens: plaz, presentar, declaracion, trimestral, impuest, valor, anadid, empres, exportador.
        self.assertFalse(retrieval.is_follow_up("¿Cuál es el plazo para presentar la declaración trimestral del impuesto sobre el valor añadido de las empresas exportadoras?"))

    def test_search_text_prepends_the_previous_question(self):
        self.assertEqual(retrieval.search_text("¿Y el aceite?", "¿Cada cuánto se revisa la bomba?", True),
                         "¿Cada cuánto se revisa la bomba? ¿Y el aceite?")
        self.assertEqual(retrieval.search_text("¿Y el aceite?", "¿Cada cuánto se revisa la bomba?", False), "¿Y el aceite?")
        self.assertEqual(retrieval.search_text("¿Y el aceite?", None, True), "¿Y el aceite?")


class RetrieverTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        docs = [index.IndexDocument("d", "Manual", "a" * 64, "text/plain", 1, None, [
            RawBlock((), "Las bombas hidráulicas se revisan cada 500 horas.", None),
            RawBlock(("Aceite",), "El aceite se cambia cada año.", None),
            RawBlock(("Filtros",), "Los filtros de aire se limpian cada mes.", None),
        ])]
        self.path = Path(self.tmp.name, "ref.db")
        index.build_index(self.path, docs, 60, 200, True, 4.5, fake_embedding, {"variant": "ref"})
        self.db = index.connect(self.path)
        rows = self.db.execute("SELECT chunk_id, v768 FROM eval_full_embeddings ORDER BY chunk_id").fetchall()
        self.vectors = {cid: truncate_and_normalize(list(struct.unpack("<768f", blob)), 256) for cid, blob in rows}
        target = self.vectors[2]
        self.retriever = retrieval.Retriever(self.db, embed_query=lambda text: target, fts_variant="v1")

    def tearDown(self):
        self.db.close()
        self.tmp.cleanup()

    def test_vector_fts_and_hybrid(self):
        self.assertEqual(self.retriever.vector("lo que sea", 50)[0], 2)
        self.assertEqual(self.retriever.fts("bombas", 50), [1])
        result = self.retriever.retrieve("bombas", None, EvalConfig())
        self.assertEqual(result.vector_ids[0], 2)
        self.assertEqual(result.fts_ids, [1])
        self.assertEqual(set(result.fused_ids[:2]), {1, 2})
        self.assertEqual(self.retriever.retrieve("bombas", None, EvalConfig(mode="fts")).fused_ids, [1])
        self.assertEqual(self.retriever.retrieve("bombas", None, EvalConfig(mode="vector")).fused_ids[0], 2)

    def test_stopword_query_has_no_lexical_hits(self):
        self.assertEqual(self.retriever.fts("de la que", 50), [])


if __name__ == "__main__":
    unittest.main()

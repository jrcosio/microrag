"""Hybrid retrieval as planned for the app (F05 T026, research R6, contracts/eval-cli.md E5)."""

import json
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


class ParametersTest(unittest.TestCase):
    def test_fused_limit_is_the_shared_parameter(self):
        shared = json.loads(retrieval.PARAMETERS.read_text(encoding="utf-8"))
        self.assertEqual(retrieval.FUSED_LIMIT, 30)
        self.assertEqual(retrieval.FUSED_LIMIT, shared["fused_limit"])


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


class TrigramQueryTest(unittest.TestCase):
    """F10 D-035: the third list's query (contracts/eval-retrieval.md)."""

    def test_terms_without_accents_or_stopwords(self):
        self.assertEqual(retrieval.trigram_query("Las bombas hidráulicas de la central"),
                         '"bombas" OR "hidraulicas" OR "central"')

    def test_terms_shorter_than_three_characters_are_left_out(self):
        self.assertEqual(retrieval.trigram_query("ok sí 12 abc IVA"), '"abc" OR "iva"')

    def test_distinct_and_at_most_sixteen(self):
        self.assertEqual(retrieval.trigram_query("Bomba bomba BOMBA"), '"bomba"')
        words = [f"palabra{n:02d}" for n in range(20)]
        self.assertEqual(retrieval.trigram_query(" ".join(words)), " OR ".join(f'"{w}"' for w in words[:16]))

    def test_no_terms_gives_an_empty_query(self):
        self.assertEqual(retrieval.trigram_query("de la que"), "")
        self.assertEqual(retrieval.trigram_query(""), "")


class RerankTest(unittest.TestCase):
    """F10 D-035: alpha * normalized cosine similarity + (1 - alpha) * normalized BM25, only on the head."""

    # Similarities 0.8, 0.9, 0.7 -> 0.5, 1, 0; BM25 relevances 5, 1, 3 -> 1, 0, 0.5; a missing score counts 0.
    VECTOR = {1: 0.2, 2: 0.1, 3: 0.3}
    TEXT = {1: -5.0, 3: -1.0, 4: -3.0}

    def test_combined_score_by_hand(self):
        # Head [3, 2, 1] scores 0, 0.5 and 0.75; 4 would score 0.25 but stays behind the head.
        self.assertEqual(retrieval.rerank([3, 2, 1, 4], self.VECTOR, self.TEXT, alpha=0.5, top=3), [1, 2, 3, 4])

    def test_alpha_weighs_the_lists(self):
        self.assertEqual(retrieval.rerank([3, 2, 1, 4], self.VECTOR, self.TEXT, alpha=1.0, top=3), [2, 1, 3, 4])
        self.assertEqual(retrieval.rerank([3, 2, 1, 4], self.VECTOR, self.TEXT, alpha=0.0, top=4), [1, 4, 3, 2])

    def test_ties_keep_the_fusion_order_and_a_single_value_counts_one(self):
        self.assertEqual(retrieval.rerank([2, 1], {1: 0.5, 2: 0.5}, {}, alpha=1.0, top=2), [2, 1])
        self.assertEqual(retrieval.rerank([5, 6], {6: 0.4}, {}, alpha=1.0, top=2), [6, 5])


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

    def test_trigram_list_finds_parts_of_words_without_touching_the_index(self):
        before = index.fingerprint(self.db)
        self.assertEqual(self.retriever.trigram("hidráulic", 50), [1])
        self.assertEqual(self.retriever.trigram("de la", 50), [])
        self.assertEqual(index.fingerprint(self.db), before)
        self.assertEqual(self.db.execute("SELECT count(*) FROM sqlite_master WHERE name LIKE '%trigram%'").fetchone()[0], 0)

    def test_third_list_and_rerank_change_only_the_fusion(self):
        base = self.retriever.retrieve("bombas", None, EvalConfig())
        three = self.retriever.retrieve("bombas", None, EvalConfig(third_list="trigram"))
        self.assertEqual((three.vector_ids, three.fts_ids), (base.vector_ids, base.fts_ids))
        self.assertEqual(three.trigram_ids, [1])
        self.assertEqual(three.fused_ids, retrieval.rrf([base.vector_ids, base.fts_ids, [1]], 60))
        reranked = self.retriever.retrieve("bombas", None, EvalConfig(rerank_alpha=0.0, rerank_top=20))
        self.assertEqual(reranked.fused_ids[0], 1)  # the only lexical hit leads when only BM25 counts
        self.assertEqual(sorted(reranked.fused_ids), sorted(base.fused_ids))


if __name__ == "__main__":
    unittest.main()

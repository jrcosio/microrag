"""appconfig reads the app's expected values from the Kotlin sources (F05 T004, research R5)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import appconfig  # noqa: E402


class AppConfigTest(unittest.TestCase):
    def test_models_come_from_the_catalog(self):
        eg = appconfig.model("EG2_270M")
        self.assertEqual(eg.file_name, "embeddinggemma-2-text-270m.litertlm")
        self.assertEqual(eg.sha256, "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb")
        self.assertEqual(eg.size_bytes, 164_626_432)
        e2b = appconfig.model("GEMMA4_E2B")
        self.assertEqual(e2b.file_name, "gemma-4-E2B-it.litertlm")
        self.assertEqual(e2b.sha256, "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c")

    def test_vector_space_values(self):
        self.assertEqual(appconfig.dimensions(), 256)
        self.assertEqual(appconfig.document_prefix(), "task: search result | text: ")
        self.assertEqual(appconfig.query_prefix(), "task: search query | text: ")
        self.assertEqual(appconfig.normalizer_version(), 1)
        self.assertEqual(appconfig.schema_version(), 3)  # F08: documents.name_norm and replaces_id (R1)

    def test_expected_meta_has_the_seven_keys_in_order(self):
        meta = appconfig.expected_meta()
        self.assertEqual(
            list(meta),
            ["schema_version", "embedding_model_sha256", "embedding_dims", "embedding_doc_prefix",
             "embedding_query_prefix", "normalizer_version", "quant_dirty"],
        )
        self.assertEqual(meta["embedding_dims"], "256")
        self.assertEqual(meta["quant_dirty"], "1")
        self.assertEqual(appconfig.expected_meta(dims=128)["embedding_dims"], "128")

    def test_prefixes_match_the_offline_embedding_inputs(self):
        import embedding_text
        self.assertEqual(appconfig.document_prefix(), embedding_text.DOCUMENT)
        self.assertEqual(appconfig.query_prefix(), embedding_text.QUERY)


if __name__ == "__main__":
    unittest.main()

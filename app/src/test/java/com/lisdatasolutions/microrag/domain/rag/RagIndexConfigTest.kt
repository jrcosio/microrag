package com.lisdatasolutions.microrag.domain.rag

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.rag.text.SpanishText
import org.junit.Test

class RagIndexConfigTest {

    @Test
    fun current_describesTheAppVectorSpace() {
        val config = RagIndexConfig.current()

        assertThat(config.schemaVersion).isEqualTo(2)
        assertThat(config.embeddingModelSha256).isEqualTo(ModelCatalog.byId(ModelId.EG2_270M).sha256)
        assertThat(config.dims).isEqualTo(EmbeddingMath.DIMENSIONS)
        assertThat(config.docPrefix).isEqualTo("task: search result | text: ")
        assertThat(config.docPrefix).isEqualTo(EmbeddingPrefixes.DOCUMENT)
        assertThat(config.queryPrefix).isEqualTo(EmbeddingPrefixes.QUERY)
        assertThat(config.normalizerVersion).isEqualTo(SpanishText.VERSION)
    }

    @Test
    fun toMeta_hasExactlyTheSevenReportKeys() {
        val config = RagIndexConfig.current()

        assertThat(config.toMeta()).containsExactly(
            "schema_version", "2",
            "embedding_model_sha256", config.embeddingModelSha256,
            "embedding_dims", "256",
            "embedding_doc_prefix", "task: search result | text: ",
            "embedding_query_prefix", "task: search query | text: ",
            "normalizer_version", "1",
            "quant_dirty", "1",
        ).inOrder()
    }
}

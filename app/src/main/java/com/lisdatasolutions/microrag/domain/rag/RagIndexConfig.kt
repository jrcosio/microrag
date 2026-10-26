package com.lisdatasolutions.microrag.domain.rag

import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.rag.text.SpanishText

/** Keys of `rag_meta` (constitution III, F04 data-model §2). */
object RagMetaKeys {
    const val SCHEMA_VERSION = "schema_version"
    const val EMBEDDING_MODEL_SHA256 = "embedding_model_sha256"
    const val EMBEDDING_DIMS = "embedding_dims"
    const val EMBEDDING_DOC_PREFIX = "embedding_doc_prefix"
    const val EMBEDDING_QUERY_PREFIX = "embedding_query_prefix"
    const val NORMALIZER_VERSION = "normalizer_version"
    const val QUANT_DIRTY = "quant_dirty"

    /** Checked on open; `quant_dirty` only tracks the INT8 index, which F14 decides on. */
    val VALIDATED = listOf(
        SCHEMA_VERSION, EMBEDDING_MODEL_SHA256, EMBEDDING_DIMS, EMBEDDING_DOC_PREFIX, EMBEDDING_QUERY_PREFIX,
        NORMALIZER_VERSION,
    )
}

/** The vector space and schema this app version reads and writes. */
data class RagIndexConfig(
    val schemaVersion: Int,
    val embeddingModelSha256: String,
    val dims: Int,
    val docPrefix: String,
    val queryPrefix: String,
    val normalizerVersion: Int,
) {
    /** Values written when an index is created; there is no INT8 index yet, so it starts dirty. */
    fun toMeta(): Map<String, String> = linkedMapOf(
        RagMetaKeys.SCHEMA_VERSION to schemaVersion.toString(),
        RagMetaKeys.EMBEDDING_MODEL_SHA256 to embeddingModelSha256,
        RagMetaKeys.EMBEDDING_DIMS to dims.toString(),
        RagMetaKeys.EMBEDDING_DOC_PREFIX to docPrefix,
        RagMetaKeys.EMBEDDING_QUERY_PREFIX to queryPrefix,
        RagMetaKeys.NORMALIZER_VERSION to normalizerVersion.toString(),
        RagMetaKeys.QUANT_DIRTY to "1",
    )

    companion object {
        const val SCHEMA_VERSION = 1

        fun current(): RagIndexConfig = RagIndexConfig(
            schemaVersion = SCHEMA_VERSION,
            embeddingModelSha256 = ModelCatalog.byId(ModelId.EG2_270M).sha256,
            dims = EmbeddingMath.DIMENSIONS,
            docPrefix = EmbeddingPrefixes.DOCUMENT,
            queryPrefix = EmbeddingPrefixes.QUERY,
            normalizerVersion = SpanishText.VERSION,
        )
    }
}

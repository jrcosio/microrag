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

    // F19 research R8 (constitution 1.2.0, III): the page space; it never blocks opening, it decides if pages are used.
    const val PAGE_MODEL_SHA256 = "page_model_sha256"
    const val PAGE_DIMS = "page_dims"
    const val PAGE_TOKENS_PER_IMAGE = "page_tokens_per_image"
    const val PAGE_PX = "page_px"
    const val PAGE_FORMAT = "page_format"

    val PAGE_SPACE = listOf(PAGE_MODEL_SHA256, PAGE_DIMS, PAGE_TOKENS_PER_IMAGE, PAGE_PX, PAGE_FORMAT)
}

/** How the pages of the multimodal mode are stored (F19 research R8, D-078): written with the first page. */
data class PageSpace(
    val modelSha256: String,
    val dims: Int,
    val tokensPerImage: Int,
    val px: Int,
    val format: String,
) {
    fun toMeta(): Map<String, String> = linkedMapOf(
        RagMetaKeys.PAGE_MODEL_SHA256 to modelSha256,
        RagMetaKeys.PAGE_DIMS to dims.toString(),
        RagMetaKeys.PAGE_TOKENS_PER_IMAGE to tokensPerImage.toString(),
        RagMetaKeys.PAGE_PX to px.toString(),
        RagMetaKeys.PAGE_FORMAT to format,
    )

    /** The stored keys match this app, or no page was stored yet. */
    fun matches(meta: Map<String, String>): Boolean = RagMetaKeys.PAGE_SPACE.none { it in meta } || toMeta().all { (k, v) -> meta[k] == v }

    companion object {
        const val TOKENS_PER_IMAGE = 140
        const val PX = 768
        const val JPEG_QUALITY = 85

        val CURRENT = PageSpace(
            modelSha256 = ModelCatalog.byId(ModelId.EG2_440M).sha256,
            dims = EmbeddingMath.DIMENSIONS,
            tokensPerImage = TOKENS_PER_IMAGE,
            px = PX,
            format = "jpeg-$JPEG_QUALITY",
        )
    }
}

/**
 * The vector space and schema this app version reads and writes. [embeddingModelSha256] is the text embedder a new
 * index is created with; an existing one may record any of [acceptedEmbeddingModels] (F19 research R2).
 */
data class RagIndexConfig(
    val schemaVersion: Int,
    val embeddingModelSha256: String,
    val dims: Int,
    val docPrefix: String,
    val queryPrefix: String,
    val normalizerVersion: Int,
    val acceptedEmbeddingModels: Set<String> = setOf(embeddingModelSha256),
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
        /**
         * 2 since F06: `documents.skipped_pages` (D-038); 3 since F08: `name_norm` and `replaces_id`; 4 since F17:
         * `folders` and `folder_id`; 5 since F19: `documents.index_mode` and `pages` (research R1, R1 and R8 of each).
         */
        const val SCHEMA_VERSION = 5

        /**
         * Text embedders whose 256D vectors are the same (constitution 1.2.0, III): the 270M by default and the 440M
         * of the multimodal mode, which replaces it without reindexing after the on-device identity check (D-078).
         */
        val TEXT_EMBEDDERS = listOf(ModelId.EG2_270M, ModelId.EG2_440M)

        /** The text embedder an index records, or null for any other model. */
        fun textEmbedderOf(sha256: String?): ModelId? = TEXT_EMBEDDERS.firstOrNull { ModelCatalog.byId(it).sha256 == sha256 }

        fun current(newIndexEmbedder: ModelId = ModelId.EG2_270M): RagIndexConfig = RagIndexConfig(
            schemaVersion = SCHEMA_VERSION,
            embeddingModelSha256 = ModelCatalog.byId(newIndexEmbedder).sha256,
            dims = EmbeddingMath.DIMENSIONS,
            docPrefix = EmbeddingPrefixes.DOCUMENT,
            queryPrefix = EmbeddingPrefixes.QUERY,
            normalizerVersion = SpanishText.VERSION,
            acceptedEmbeddingModels = TEXT_EMBEDDERS.map { ModelCatalog.byId(it).sha256 }.toSet(),
        )
    }
}

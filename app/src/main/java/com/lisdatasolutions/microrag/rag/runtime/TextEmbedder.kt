package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.domain.model.ModelId

/**
 * Text embeddings with EmbeddingGemma 2. Every vector has [EmbeddingMath.DIMENSIONS] components
 * and unit L2 norm. All calls run on `AppDispatchers.embeddings`.
 */
interface TextEmbedder : ManagedEngine {
    val model: ModelId

    suspend fun initialize()

    /** Embeds a search query with [EmbeddingPrefixes.QUERY]. */
    suspend fun embedQuery(text: String): FloatArray

    /** Embeds a document chunk with [EmbeddingPrefixes.DOCUMENT] and its contextual [header]. */
    suspend fun embedDocument(content: String, header: String = ""): FloatArray

    /**
     * Embeds a chunk for indexing, cutting the embedded text with the D-036 rule while the model
     * rejects it as too long (F07 research R6). Throws [EmbeddingInputTooLongException] after
     * [EmbeddingOverflow.MAX_ATTEMPTS] attempts and [EmbeddingEngineException] on any other failure.
     */
    suspend fun embedDocumentFitting(content: String, header: String): DocumentEmbedding =
        DocumentEmbedding(embedDocument(content, header), cuts = 0)

    /** Size of the raw model output before truncation; null until the first embedding. */
    val rawDimensions: Int?
}

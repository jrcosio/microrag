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

    /** Size of the raw model output before truncation; null until the first embedding. */
    val rawDimensions: Int?
}

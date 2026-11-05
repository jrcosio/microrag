package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File

/**
 * EmbeddingGemma 2 with images, with [visionTokensPerImage] tokens per image (F19 research R7, measured in F18).
 * Its text side stays on the CPU; the file only declares vision on the CPU too.
 */
interface ImageEmbedder : ManagedEngine {
    val model: ModelId
    val visionTokensPerImage: Int

    suspend fun initialize()

    /** The raw vector (768) of an encoded image, not normalized: truncation and L2 are done with [EmbeddingMath]. */
    suspend fun embedImage(bytes: ByteArray): FloatArray
}

/** Creates image embedders; the only implementation lives in rag/runtime/litert (constitution II). */
interface ImageEmbedderFactory {
    fun imageEmbedder(file: ModelFile, path: File, backend: ComputeBackend, visionTokensPerImage: Int): ImageEmbedder
}

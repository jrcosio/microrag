package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.ManagedEngine
import java.io.File
import kotlinx.serialization.Serializable

/**
 * What the visual diagnostic needs from the runtime and the platform (F18 contracts/visual-diagnostic.md §1).
 * Nothing here imports LiteRT-LM or the PDF API: the adapters live in rag/runtime/litert and data/ingest of
 * the debug source set (research R3).
 */

/** One part of a message to Gemma, in order: labels, images and text, as in the v5-visual layout (R13). */
sealed interface VisionPart {
    data class Text(val text: String) : VisionPart

    class Image(val bytes: ByteArray) : VisionPart
}

data class VisionGeneration(
    val text: String,
    val firstTokenMs: Long,
    val totalMs: Long,
    val prefillTokens: Int?,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
)

/** Gemma with images: its text on the engine's backend and its vision encoder on [visionBackend]. */
interface VisionGenerator : ManagedEngine {
    val model: ModelId
    val visionBackend: ComputeBackend

    suspend fun initialize()

    /** One greedy answer to [parts]; [visualTokenBudget] null keeps the file's default. */
    suspend fun generate(parts: List<VisionPart>, visualTokenBudget: Int?, maxOutputTokens: Int): VisionGeneration
}

/** EmbeddingGemma 2 with images, with [visionTokensPerImage] tokens per image. */
interface ImageEmbedder : ManagedEngine {
    val model: ModelId
    val visionTokensPerImage: Int

    suspend fun initialize()

    /** The raw vector (768) of an encoded image, not normalized: truncation and L2 are done with EmbeddingMath. */
    suspend fun embedImage(bytes: ByteArray): FloatArray
}

/** What a model file declares (FR-004). */
@Serializable
data class VisionModelInfo(
    val text: Boolean,
    val vision: Boolean,
    val maxVisionTokenBudget: Int,
    val visionSignatures: List<Int>,
    val textBackends: List<ComputeBackend>,
    val visionBackends: List<ComputeBackend>,
)

interface VisionEngineFactory {
    fun generator(file: ModelFile, path: File, backend: ComputeBackend, visionBackend: ComputeBackend, maxNumImages: Int): VisionGenerator

    fun imageEmbedder(file: ModelFile, path: File, backend: ComputeBackend, visionTokensPerImage: Int): ImageEmbedder

    /** Blocking native read of the file header; call it off the main thread. */
    fun modelInfo(path: File): VisionModelInfo
}

enum class ImageFormat { WEBP, JPEG, PNG }

class EncodedImage(val bytes: ByteArray, val widthPx: Int, val heightPx: Int)

/** PdfRenderer and ImageDecoder (research R6). Pages are 1-based, as on the desktop. */
interface PageRasterizer {
    fun pageCount(pdf: File): Int

    /** Renders [page] on white, scaled so its long side is [longSidePx], encoded as [format] at [quality]. */
    fun renderPage(pdf: File, page: Int, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage

    /** Decodes a JPG or PNG upright (EXIF), on white, scaled down so its long side is at most [longSidePx]. */
    fun scaleImage(image: File, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage
}

package com.lisdatasolutions.microrag.rag.runtime.litert

import android.content.Context
import com.google.ai.edge.litertlm.BackendType
import com.google.ai.edge.litertlm.Modality
import com.google.ai.edge.litertlm.ModelInfo
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionEngineFactory
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionGeneration
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionGenerator
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionModelInfo
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionPart
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisualProtocol
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationPart
import com.lisdatasolutions.microrag.rag.runtime.GenerationPrompt
import com.lisdatasolutions.microrag.rag.runtime.GenerationStats
import com.lisdatasolutions.microrag.rag.runtime.ImageEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import java.io.File

/**
 * LiteRT-LM with images, for the F18 visual diagnostic only (research R1, R3): debug source set, same
 * package as the app's adapters, so the SDK stays behind them (constitution II). Nothing here logs prompts,
 * answers or images.
 */
class LiteRtVisionEngineFactory(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : VisionEngineFactory {

    private val compiledCacheDir: File
        get() = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    override fun generator(
        file: ModelFile,
        path: File,
        backend: ComputeBackend,
        visionBackend: ComputeBackend,
        maxNumImages: Int,
    ): VisionGenerator {
        val maxOutputTokens = checkNotNull(VisualProtocol.GENERATION.maxOutputTokens)
        val config = GenerationConfig(
            maxNumTokens = VisualProtocol.MAX_NUM_TOKENS,
            maxOutputTokens = maxOutputTokens,
            greedy = true,
            visionBackend = visionBackend,
            maxNumImages = maxNumImages,
        )
        return MainVisionGenerator(LiteRtTextGenerator(file.id, path, backend, compiledCacheDir, config, dispatchers.llm), visionBackend, maxOutputTokens)
    }

    override fun imageEmbedder(file: ModelFile, path: File, backend: ComputeBackend, visionTokensPerImage: Int): ImageEmbedder =
        LiteRtImageEmbedder(file.id, path, backend, compiledCacheDir, visionTokensPerImage, dispatchers.embeddings)

    override fun modelInfo(path: File): VisionModelInfo = ModelInfo.from(path.path).use { info ->
        val modalities = info.inputModalities()
        VisionModelInfo(
            text = modalities.text,
            vision = modalities.vision,
            maxVisionTokenBudget = info.maxVisionTokenBudget(),
            visionSignatures = info.visionSignatureSelection()?.toList().orEmpty(),
            textBackends = info.supportedBackends(Modality.TEXT).mapNotNull { it.toComputeBackend() },
            visionBackends = info.supportedBackends(Modality.VISION).mapNotNull { it.toComputeBackend() },
        )
    }

    private companion object {
        const val CACHE_DIR = "litertlm"
    }
}

/**
 * The F18 contract over the app's Gemma (F20 research R6): the engine with images is [LiteRtTextGenerator], created
 * with vision, so the chat and this diagnostic share one adapter. The answer is collected into one [VisionGeneration].
 */
internal class MainVisionGenerator(
    private val generator: TextGenerator,
    override val visionBackend: ComputeBackend,
    private val maxOutputTokens: Int,
) : VisionGenerator {

    override val model: ModelId get() = generator.model

    override val backend: ComputeBackend get() = generator.backend

    override suspend fun initialize() = generator.initialize()

    override suspend fun generate(parts: List<VisionPart>, visualTokenBudget: Int?, maxOutputTokens: Int): VisionGeneration {
        require(maxOutputTokens == this.maxOutputTokens) { "The engine was created for ${this.maxOutputTokens} output tokens" }
        val text = StringBuilder()
        var stats: GenerationStats? = null
        val prompt = GenerationPrompt.Parts(
            parts.map { part ->
                when (part) {
                    is VisionPart.Text -> GenerationPart.Text(part.text)
                    is VisionPart.Image -> GenerationPart.Image(part.bytes)
                }
            },
            visualTokenBudget,
        )
        generator.generate(prompt).collect { event ->
            when (event) {
                is GenerationEvent.Token -> text.append(event.textDelta)
                is GenerationEvent.Finished -> stats = event.stats
            }
        }
        val finished = checkNotNull(stats) { "The generation ended without its statistics" }
        return VisionGeneration(
            text = text.toString(),
            firstTokenMs = finished.ttftWallMs,
            totalMs = finished.totalWallMs,
            prefillTokens = finished.prefillTokens,
            prefillTokensPerSecond = finished.prefillTokensPerSecond,
            decodeTokensPerSecond = finished.decodeTokensPerSecond,
        )
    }

    override fun close() = generator.close()
}

private fun BackendType.toComputeBackend(): ComputeBackend? = when (this) {
    BackendType.CPU -> ComputeBackend.CPU
    BackendType.GPU -> ComputeBackend.GPU
    BackendType.NPU -> null
}

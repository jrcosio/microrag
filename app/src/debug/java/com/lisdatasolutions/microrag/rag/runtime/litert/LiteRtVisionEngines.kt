package com.lisdatasolutions.microrag.rag.runtime.litert

import android.content.Context
import com.google.ai.edge.litertlm.BackendType
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.Modality
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.SamplerConfig
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.visual.ImageEmbedder
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionEngineFactory
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionGeneration
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionGenerator
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionModelInfo
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionPart
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisualProtocol
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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
    ): VisionGenerator = LiteRtVisionGenerator(file.id, path, backend, visionBackend, compiledCacheDir, maxNumImages, dispatchers.llm)

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
 * Gemma with its text on [backend] and its vision encoder on [visionBackend], in one engine. The visual
 * budget is a process-wide experimental flag: it is set and restored around each answer on the LLM thread.
 */
internal class LiteRtVisionGenerator(
    override val model: ModelId,
    private val modelPath: File,
    override val backend: ComputeBackend,
    override val visionBackend: ComputeBackend,
    private val cacheDir: File,
    private val maxNumImages: Int,
    private val dispatcher: CoroutineDispatcher,
) : VisionGenerator {

    @Volatile
    private var engine: Engine? = null

    override suspend fun initialize() = withContext(dispatcher) {
        if (engine != null) return@withContext
        val created = Engine(
            EngineConfig(
                modelPath = modelPath.path,
                backend = backend.toLiteRt(),
                visionBackend = visionBackend.toLiteRt(),
                maxNumTokens = VisualProtocol.MAX_NUM_TOKENS,
                maxNumImages = maxNumImages,
                cacheDir = cacheDir.path,
            ),
        )
        try {
            created.initialize()
        } catch (t: Throwable) {
            created.close()
            throw t
        }
        engine = created
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun generate(parts: List<VisionPart>, visualTokenBudget: Int?, maxOutputTokens: Int): VisionGeneration =
        withContext(dispatcher) {
            val engine = checkNotNull(engine) { "Vision generator for $model is not initialized" }
            val previousBudget = ExperimentalFlags.visualTokenBudget
            ExperimentalFlags.visualTokenBudget = visualTokenBudget
            var conversation: Conversation? = null
            try {
                val created = engine.createConversation(
                    ConversationConfig(samplerConfig = GREEDY, maxOutputToken = maxOutputTokens, enableSpeculativeDecoding = false),
                )
                conversation = created
                val start = System.nanoTime()
                var firstToken: Long? = null
                val text = StringBuilder()
                created.sendMessageAsync(Contents.of(parts.map { it.toContent() })).collect { message ->
                    val delta = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                    if (delta.isNotEmpty()) {
                        if (firstToken == null) firstToken = System.nanoTime()
                        text.append(delta)
                    }
                }
                val end = System.nanoTime()
                val info = if (ExperimentalFlags.enableBenchmark) created.getBenchmarkInfo() else null
                VisionGeneration(
                    text = text.toString(),
                    firstTokenMs = ((firstToken ?: end) - start) / NANOS_PER_MILLI,
                    totalMs = (end - start) / NANOS_PER_MILLI,
                    prefillTokens = info?.lastPrefillTokenCount,
                    prefillTokensPerSecond = info?.lastPrefillTokensPerSecond,
                    decodeTokensPerSecond = info?.lastDecodeTokensPerSecond,
                )
            } catch (e: CancellationException) {
                conversation?.cancelProcess()
                throw e
            } finally {
                conversation?.close()
                ExperimentalFlags.visualTokenBudget = previousBudget
            }
        }

    override fun close() {
        engine?.close()
        engine = null
    }

    private fun VisionPart.toContent(): Content = when (this) {
        is VisionPart.Text -> Content.Text(text)
        is VisionPart.Image -> Content.ImageBytes(bytes)
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)
    }
}

/** EmbeddingGemma 2 with images: its vision encoder on [backend], with a fixed number of tokens per image. */
internal class LiteRtImageEmbedder(
    override val model: ModelId,
    private val modelPath: File,
    override val backend: ComputeBackend,
    private val cacheDir: File,
    override val visionTokensPerImage: Int,
    private val dispatcher: CoroutineDispatcher,
) : ImageEmbedder {

    @Volatile
    private var engine: EmbeddingEngine? = null

    override suspend fun initialize() = withContext(dispatcher) {
        if (engine != null) return@withContext
        val created = EmbeddingEngine(
            EmbeddingEngineConfig(
                modelPath = modelPath.path,
                backend = ComputeBackend.CPU.toLiteRt(),
                visionBackend = backend.toLiteRt(),
                cacheDir = cacheDir.path,
                visionTokensPerImage = visionTokensPerImage,
            ),
        )
        try {
            created.initialize()
        } catch (t: Throwable) {
            created.close()
            throw t
        }
        engine = created
    }

    override suspend fun embedImage(bytes: ByteArray): FloatArray = withContext(dispatcher) {
        val engine = checkNotNull(engine) { "Image embedder for $model is not initialized" }
        engine.computeEmbedding(
            listOf(InputData.Image(bytes)),
            EmbeddingOptions(normalize = false, visionTokensPerImage = visionTokensPerImage),
        ).embedding
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}

private fun BackendType.toComputeBackend(): ComputeBackend? = when (this) {
    BackendType.CPU -> ComputeBackend.CPU
    BackendType.GPU -> ComputeBackend.GPU
    BackendType.NPU -> null
}

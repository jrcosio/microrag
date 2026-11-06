package com.lisdatasolutions.microrag.rag.runtime.litert

import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingMode
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.ActivationPrecision
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationPart
import com.lisdatasolutions.microrag.rag.runtime.GenerationPrompt
import com.lisdatasolutions.microrag.rag.runtime.GenerationStats
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Gemma on LiteRT-LM. Each [generate] call uses a fresh conversation (single turn). F20 (research R5, R6): with
 * [GenerationConfig.visionBackend], the same engine also reads images, as the F18 diagnostic did; the budget per
 * image is a process-wide experimental flag, set and restored around each answer on the LLM thread.
 */
internal class LiteRtTextGenerator(
    override val model: ModelId,
    private val modelPath: File,
    override val backend: ComputeBackend,
    private val cacheDir: File,
    private val config: GenerationConfig,
    private val dispatcher: CoroutineDispatcher,
) : TextGenerator {

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var activeConversation: Conversation? = null

    override suspend fun initialize() = withContext(dispatcher) {
        if (engine != null) return@withContext
        val created = Engine(
            EngineConfig(
                modelPath = modelPath.path,
                backend = backend.toLiteRt(),
                maxNumTokens = config.maxNumTokens,
                visionBackend = config.visionBackend?.toLiteRt(),
                maxNumImages = config.maxNumImages.takeIf { config.visionBackend != null },
                cacheDir = cacheDir.path,
                activationDataType = when (config.activationPrecision) {
                    null -> null
                    ActivationPrecision.FLOAT32 -> ActivationDataType.FLOAT32
                    ActivationPrecision.FLOAT16 -> ActivationDataType.FLOAT16
                },
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

    override fun generate(prompt: String): Flow<GenerationEvent> = stream { it.sendMessageAsync(prompt) }

    override fun generate(prompt: GenerationPrompt): Flow<GenerationEvent> = when (prompt) {
        is GenerationPrompt.Text -> generate(prompt.text)
        is GenerationPrompt.Parts -> {
            check(config.visionBackend != null) { "Generator for $model has no vision" }
            stream(visualTokens = prompt.visualTokens, withBudget = true) { conversation ->
                conversation.sendMessageAsync(Contents.of(prompt.parts.map { it.toContent() }))
            }
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun stream(
        visualTokens: Int? = null,
        withBudget: Boolean = false,
        send: (Conversation) -> Flow<Message>,
    ): Flow<GenerationEvent> = flow {
        val engine = checkNotNull(engine) { "Generator for $model is not initialized" }
        val previousBudget = ExperimentalFlags.visualTokenBudget
        if (withBudget) ExperimentalFlags.visualTokenBudget = visualTokens
        val conversation = try {
            engine.createConversation(conversationConfig())
        } catch (t: Throwable) {
            if (withBudget) ExperimentalFlags.visualTokenBudget = previousBudget
            throw t
        }
        activeConversation = conversation
        try {
            val start = System.nanoTime()
            var firstToken: Long? = null
            send(conversation).collect { message ->
                val delta = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                if (delta.isNotEmpty()) {
                    if (firstToken == null) firstToken = System.nanoTime()
                    emit(GenerationEvent.Token(delta))
                }
            }
            val end = System.nanoTime()
            val info = if (ExperimentalFlags.enableBenchmark) conversation.getBenchmarkInfo() else null
            emit(
                GenerationEvent.Finished(
                    GenerationStats(
                        initSeconds = info?.initTimeInSecond,
                        ttftSeconds = info?.timeToFirstTokenInSecond,
                        ttftWallMs = ((firstToken ?: end) - start) / NANOS_PER_MILLI,
                        totalWallMs = (end - start) / NANOS_PER_MILLI,
                        prefillTokens = info?.lastPrefillTokenCount,
                        prefillTokensPerSecond = info?.lastPrefillTokensPerSecond,
                        decodeTokens = info?.lastDecodeTokenCount,
                        decodeTokensPerSecond = info?.lastDecodeTokensPerSecond,
                    ),
                ),
            )
        } catch (e: CancellationException) {
            conversation.cancelProcess()
            throw e
        } finally {
            activeConversation = null
            conversation.close()
            if (withBudget) ExperimentalFlags.visualTokenBudget = previousBudget
        }
    }.flowOn(dispatcher)

    override fun cancel() {
        activeConversation?.cancelProcess()
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    private fun conversationConfig() = ConversationConfig(
        samplerConfig = if (config.greedy) GREEDY else null,
        maxOutputToken = config.maxOutputTokens,
        // Off until F14 measures it (research R7); 0.19 replaced the deprecated boolean with this config.
        speculativeDecodingConfig = SpeculativeDecodingConfig(SpeculativeDecodingMode.OFF),
    )

    private fun GenerationPart.toContent(): Content = when (this) {
        is GenerationPart.Text -> Content.Text(text)
        is GenerationPart.Image -> Content.ImageBytes(bytes)
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)
    }
}

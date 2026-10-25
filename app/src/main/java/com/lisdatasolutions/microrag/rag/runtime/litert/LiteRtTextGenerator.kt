package com.lisdatasolutions.microrag.rag.runtime.litert

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationStats
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Gemma on LiteRT-LM. Each [generate] call uses a fresh conversation (single turn). */
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
    override fun generate(prompt: String): Flow<GenerationEvent> = flow {
        val engine = checkNotNull(engine) { "Generator for $model is not initialized" }
        val conversation = engine.createConversation(conversationConfig())
        activeConversation = conversation
        try {
            val start = System.nanoTime()
            var firstToken: Long? = null
            conversation.sendMessageAsync(prompt).collect { message ->
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
        // Baseline measurement; speculative decoding is a later lever (research R4).
        enableSpeculativeDecoding = false,
    )

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)
    }
}

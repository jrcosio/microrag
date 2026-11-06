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
import com.lisdatasolutions.microrag.rag.runtime.SpeculativeMode
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

    /**
     * F14 (research R6, contract G3): the conversation that holds the rules already prefilled, with the [basePrefix]
     * it was made for; each answer clones it. Touched on [dispatcher] only, closed with the engine.
     */
    private var base: Conversation? = null
    private var basePrefix: String? = null

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
                // F14 (research R7): the drafter loads with the engine only when speculation is on.
                speculativeDecodingConfig = speculativeConfig(),
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
        // F14 (research R6): the rules prefilled once and cloned; without the lever, the same text in one message.
        is GenerationPrompt.Split ->
            if (config.prefixCache) stream(open = { prefixBase(it, prompt.prefix).clone() }) { it.sendMessageAsync(prompt.rest) } else generate(prompt.whole)
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
        open: (Engine) -> Conversation = { it.createConversation(conversationConfig()) },
        send: (Conversation) -> Flow<Message>,
    ): Flow<GenerationEvent> = flow {
        val engine = checkNotNull(engine) { "Generator for $model is not initialized" }
        val previousBudget = ExperimentalFlags.visualTokenBudget
        if (withBudget) ExperimentalFlags.visualTokenBudget = visualTokens
        val conversation = try {
            open(engine)
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
        base?.close()
        base = null
        basePrefix = null
        engine?.close()
        engine = null
    }

    private fun conversationConfig() = ConversationConfig(
        samplerConfig = if (config.greedy) GREEDY else null,
        maxOutputToken = config.maxOutputTokens,
        // 0.19 replaced the deprecated boolean with this config (F14 research R1).
        speculativeDecodingConfig = speculativeConfig(),
    )

    private fun speculativeConfig() = SpeculativeDecodingConfig(
        if (config.speculative == SpeculativeMode.ADAPTIVE) SpeculativeDecodingMode.ADAPTIVE else SpeculativeDecodingMode.OFF,
    )

    /**
     * Blocking, on [dispatcher]: the conversation with [prefix] prefilled, made once per engine and prefix. Its template
     * draws the prefix as the start of the user's turn, so prefix + rest are the tokens of today's single message
     * (research R6, checked on the desktop: the same greedy answers and token counts).
     */
    private fun prefixBase(engine: Engine, prefix: String): Conversation {
        base?.takeIf { basePrefix == prefix }?.let { return it }
        base?.close()
        val created = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(prefix),
                samplerConfig = if (config.greedy) GREEDY else null,
                prefillPrefaceOnInit = true,
                maxOutputToken = config.maxOutputTokens,
                chatTemplate = PREFIX_TEMPLATE,
                speculativeDecodingConfig = speculativeConfig(),
            ),
        )
        base = created
        basePrefix = prefix
        return created
    }

    private fun GenerationPart.toContent(): Content = when (this) {
        is GenerationPart.Text -> Content.Text(text)
        is GenerationPart.Image -> Content.ImageBytes(bytes)
    }

    private companion object {
        /**
         * Gemma 4's turns (the template inside the model file) with the system message drawn as the start of the user's
         * turn, without trimming it: `<bos><|turn>user\n` + rules, then the rest + `<turn|>\n` and `<|turn>model\n`.
         */
        const val PREFIX_TEMPLATE =
            "{{ bos_token }}" +
                "{%- for message in messages -%}" +
                "{%- if message['role'] == 'system' -%}{{ '<|turn>user\\n' }}" +
                "{%- elif message['role'] == 'model' or message['role'] == 'assistant' -%}{{ '<|turn>model\\n' }}{%- endif -%}" +
                "{%- if message['content'] is string -%}{{ message['content'] }}" +
                "{%- else -%}{%- for item in message['content'] -%}{%- if item['type'] == 'text' -%}{{ item['text'] }}{%- endif -%}" +
                "{%- endfor -%}{%- endif -%}" +
                "{%- if message['role'] != 'system' -%}{{ '<turn|>\\n' }}{%- endif -%}" +
                "{%- endfor -%}" +
                "{%- if add_generation_prompt -%}{{ '<|turn>model\\n' }}{%- endif -%}"
        const val NANOS_PER_MILLI = 1_000_000L
        val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)
    }
}

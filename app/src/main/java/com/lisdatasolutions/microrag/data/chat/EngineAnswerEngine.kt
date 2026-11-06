package com.lisdatasolutions.microrag.data.chat

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.chat.AnswerEngine
import com.lisdatasolutions.microrag.domain.chat.AnswerEvent
import com.lisdatasolutions.microrag.domain.chat.AnswerPrompt
import com.lisdatasolutions.microrag.domain.chat.PromptPart
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.rag.runtime.ActivationPrecision
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.EngineKind
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationPart
import com.lisdatasolutions.microrag.rag.runtime.GenerationPrompt
import com.lisdatasolutions.microrag.rag.runtime.SpeculativeMode
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.rag.runtime.initializeOrClose
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Gemma for the chat (F11 research R5): the backend F03 prepared (GPU, or CPU with the «Modo CPU» notice),
 * the desktop's config (context, answer length and greedy sampling), loaded on demand through the
 * [EngineCoordinator]. Never held: the memory callbacks release it when the UI is hidden.
 *
 * F20 (research R5, D-085): a prompt with a page uses Gemma with its vision encoder on the CPU and a wider window,
 * under its own key; any other prompt uses today's Gemma. The key comes from the prompt, never from the mode read
 * apart, so a page never reaches an engine without vision.
 */
class EngineAnswerEngine(
    private val coordinator: EngineCoordinator,
    private val factory: EngineFactory,
    private val models: ModelFileRepository,
    /** F14 (contract G1): the benches measure each lever over the app's text configuration; the app keeps it. */
    textOverrides: (GenerationConfig) -> GenerationConfig = { it },
) : AnswerEngine {

    private val model = ModelCatalog.byId(RagParameters.LLM_MODEL)
    private val baseConfig = GenerationConfig(
        maxNumTokens = RagParameters.LLM_MAX_NUM_TOKENS,
        maxOutputTokens = RagParameters.MAX_OUTPUT_TOKENS,
        greedy = true,
    )

    /** F14 (research R6–R8): the levers the phone phase adopted, for the answers without a page. */
    private val textConfig = textOverrides(
        baseConfig.copy(
            activationPrecision = RagParameters.ACTIVATION_PRECISION?.let(ActivationPrecision::valueOf),
            prefixCache = RagParameters.PREFIX_CACHE,
            speculative = if (RagParameters.SPECULATIVE_DECODING) SpeculativeMode.ADAPTIVE else SpeculativeMode.OFF,
        ),
    )
    private val visualConfig = baseConfig.copy(
        maxNumTokens = RagParameters.LLM_VISUAL_MAX_NUM_TOKENS,
        visionBackend = ComputeBackend.CPU,
        maxNumImages = RagParameters.LLM_MAX_NUM_IMAGES,
    )

    override suspend fun ready(): Boolean {
        // A fresh process has not scanned the models folder yet (as EngineQueryEmbedder).
        if (models.pathOf(model.id) == null) models.refresh()
        return models.states.value[model.id] is ModelFileState.Verified && model.id in models.preparedBackends.value
    }

    override fun preparedBackend(): Flow<ComputeBackend?> =
        combine(models.states, models.preparedBackends) { states, prepared ->
            if (states[model.id] is ModelFileState.Verified) prepared[model.id] else null
        }.distinctUntilChanged()

    override suspend fun warmUp(visual: Boolean) {
        val (key, path) = target(visual) ?: return
        coordinator.withEngine(key, { load(path, key) }) { }
    }

    override fun generate(prompt: AnswerPrompt): Flow<AnswerEvent> = channelFlow {
        val (key, path) = checkNotNull(target(visual = prompt is AnswerPrompt.Visual)) { "Gemma is not verified and prepared" }
        coordinator.withEngine(key, { load(path, key) }) { engine ->
            engine.generate(prompt.toGeneration()).collect { event ->
                send(
                    when (event) {
                        is GenerationEvent.Token -> AnswerEvent.Token(event.textDelta)
                        is GenerationEvent.Finished -> AnswerEvent.Finished(
                            ttftMs = event.stats.ttftWallMs.toDouble(),
                            totalMs = event.stats.totalWallMs.toDouble(),
                            decodeTokensPerSecond = event.stats.decodeTokensPerSecond,
                        )
                    },
                )
            }
        }
    }

    private fun target(visual: Boolean): Pair<EngineKey, File>? {
        val path = models.pathOf(model.id) ?: return null
        val backend = models.preparedBackends.value[model.id] ?: return null
        return EngineKey(model.id, backend, if (visual) EngineKind.VISION_GENERATOR else EngineKind.DEFAULT) to path
    }

    private suspend fun load(path: File, key: EngineKey): TextGenerator {
        val config = if (key.kind == EngineKind.VISION_GENERATOR) visualConfig else textConfig
        return factory.generator(model, path, key.backend, config).initializeOrClose { it.initialize() }
    }

    private fun AnswerPrompt.toGeneration(): GenerationPrompt = when (this) {
        is AnswerPrompt.Text -> prefix?.let { GenerationPrompt.Split(it, text.substring(it.length)) } ?: GenerationPrompt.Text(text)
        is AnswerPrompt.Visual -> GenerationPrompt.Parts(
            parts.map { part ->
                when (part) {
                    is PromptPart.Text -> GenerationPart.Text(part.text)
                    is PromptPart.Image -> GenerationPart.Image(part.jpeg)
                }
            },
            visualTokens,
        )
    }
}

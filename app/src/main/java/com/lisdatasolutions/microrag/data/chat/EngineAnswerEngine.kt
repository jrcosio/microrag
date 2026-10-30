package com.lisdatasolutions.microrag.data.chat

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.chat.AnswerEngine
import com.lisdatasolutions.microrag.domain.chat.AnswerEvent
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
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
 */
class EngineAnswerEngine(
    private val coordinator: EngineCoordinator,
    private val factory: EngineFactory,
    private val models: ModelFileRepository,
) : AnswerEngine {

    private val model = ModelCatalog.byId(RagParameters.LLM_MODEL)
    private val config = GenerationConfig(
        maxNumTokens = RagParameters.LLM_MAX_NUM_TOKENS,
        maxOutputTokens = RagParameters.MAX_OUTPUT_TOKENS,
        greedy = true,
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

    override suspend fun warmUp() {
        val (key, path) = target() ?: return
        coordinator.withEngine(key, { load(path, key.backend) }) { }
    }

    override fun generate(prompt: String): Flow<AnswerEvent> = channelFlow {
        val (key, path) = checkNotNull(target()) { "Gemma is not verified and prepared" }
        coordinator.withEngine(key, { load(path, key.backend) }) { engine ->
            engine.generate(prompt).collect { event ->
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

    private fun target(): Pair<EngineKey, File>? {
        val path = models.pathOf(model.id) ?: return null
        val backend = models.preparedBackends.value[model.id] ?: return null
        return EngineKey(model.id, backend) to path
    }

    private suspend fun load(path: File, backend: ComputeBackend): TextGenerator =
        factory.generator(model, path, backend, config).initializeOrClose { it.initialize() }
}

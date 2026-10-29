package com.lisdatasolutions.microrag.data.retrieval

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.QueryEmbedding
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.initializeOrClose
import java.io.File

/**
 * The app's [QueryEmbedder]: EmbeddingGemma 2 270M on the CPU (D-024), loaded on demand through the
 * [EngineCoordinator] and never held, so memory callbacks release it (FR-021). During an indexing run
 * on the CPU it shares the ingest's engine (F10 research R5).
 */
class EngineQueryEmbedder(
    private val coordinator: EngineCoordinator,
    private val factory: EngineFactory,
    private val models: ModelFileRepository,
) : QueryEmbedder {

    private val model = ModelCatalog.byId(ModelId.EG2_270M)
    private val key = EngineKey(model.id, ComputeBackend.CPU)

    override suspend fun ready(): Boolean {
        // A fresh process has not scanned the models folder yet (as ChunkEmbeddingSession).
        if (models.pathOf(model.id) == null) models.refresh()
        return models.states.value[model.id] is ModelFileState.Verified && model.id in models.preparedBackends.value
    }

    override suspend fun embed(question: String, previous: String?): QueryEmbedding {
        val path = checkNotNull(models.pathOf(model.id)) { "Embedding model not verified" }
        return coordinator.withEngine(key, { load(path) }) { engine -> engine.embedQueryFitting(question, previous) }
    }

    override val loaded: Boolean get() = key in coordinator.loaded.value

    private suspend fun load(path: File): TextEmbedder =
        factory.embedder(model, path, ComputeBackend.CPU).initializeOrClose { it.initialize() }
}

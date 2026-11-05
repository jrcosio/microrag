package com.lisdatasolutions.microrag.data.multimodal

import android.content.res.AssetManager
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.IdentityCheck
import com.lisdatasolutions.microrag.domain.multimodal.IdentityChecker
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.initializeOrClose
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One line of `assets/embedding/identity_texts.jsonl` (F19 T003). */
@Serializable
data class IdentityText(val id: String, val kind: String, val text: String)

/**
 * F19 research R3 (contract M5): before the switch, the 270M and the 440M must give this device the same 256D
 * vectors. Every sample text is embedded as a document and as a query with both models, on the CPU and one model
 * after the other, each engine released before the next loads. Only the figure goes to the log, never a text.
 */
class IdentityValidator(
    private val coordinator: EngineCoordinator,
    private val factory: EngineFactory,
    private val models: ModelFileRepository,
    private val texts: suspend () -> List<IdentityText>,
    private val logger: AppLogger,
) : IdentityChecker {
    /** Throws when a model is not verified or its engine fails: the caller treats it as a failed preparation. */
    override suspend fun check(): IdentityCheck {
        val samples = texts()
        require(samples.isNotEmpty()) { "No identity texts" }
        val before = vectors(ModelId.EG2_270M, samples)
        val after = vectors(ModelId.EG2_440M, samples)
        val minCosine = before.indices.minOf { EmbeddingMath.cosine(before[it], after[it]) }
        val check = IdentityCheck(texts = samples.size, minCosine = minCosine, passed = minCosine >= MIN_COSINE)
        logger.i(TAG) { "multimodal identity ${check.texts} texts min cosine ${"%.6f".format(minCosine)} passed=${check.passed}" }
        return check
    }

    private suspend fun vectors(id: ModelId, samples: List<IdentityText>): List<FloatArray> {
        val file = ModelCatalog.byId(id)
        val path = checkNotNull(models.pathOf(id)) { "$id not verified" }
        val key = EngineKey(id, ComputeBackend.CPU)
        return try {
            coordinator.withEngine(key, { factory.embedder(file, path, ComputeBackend.CPU).initializeOrClose { it.initialize() } }) { engine ->
                samples.flatMap { listOf(engine.embedDocument(it.text), engine.embedQuery(it.text)) }
            }
        } finally {
            // One model at a time (constitution IV): this one goes before the other loads.
            withContext(NonCancellable) { coordinator.release(key) }
        }
    }

    companion object {
        private const val TAG = "MicroRAG"

        /** Constitution 1.2.0 (III): the 440M replaces the 270M only with this identity or more. */
        const val MIN_COSINE = 0.9999

        const val ASSET = "embedding/identity_texts.jsonl"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(lines: String): List<IdentityText> = lines.lineSequence().filter { it.isNotBlank() }.map { json.decodeFromString<IdentityText>(it) }.toList()

        fun load(assets: AssetManager): List<IdentityText> = parse(assets.open(ASSET).bufferedReader().use { it.readText() })
    }
}

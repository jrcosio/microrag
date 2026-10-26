package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelFile
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

sealed interface CompatibilityResult {
    data class Compatible(val backend: ComputeBackend) : CompatibilityResult

    data object Incompatible : CompatibilityResult
}

/** On-device test of a verified model file (F03 FR-021). */
fun interface ModelCompatibility {
    suspend fun check(file: ModelFile, path: File): CompatibilityResult
}

/**
 * Real on-device test before a model is declared ready (constitution V, F03 FR-021, research R8):
 * the file opens with the runtime, EmbeddingGemma 2 produces a 768 → 256 unit vector on CPU, and
 * Gemma 4 starts and emits tokens on the GPU, or on the CPU as a fallback. Engines are released
 * when done; [EngineCoordinator] keeps a single GPU engine. It checks the runtime, not answer quality.
 */
class ModelCompatibilityChecker(
    private val factory: EngineFactory,
    private val coordinator: EngineCoordinator,
    private val logger: AppLogger,
) : ModelCompatibility {

    override suspend fun check(file: ModelFile, path: File): CompatibilityResult {
        val declared = try {
            factory.metadata(path).supportedBackends
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, e) { "compat ${file.id}: metadata unreadable" }
            return CompatibilityResult.Incompatible
        }
        val result = when (file.family) {
            ModelFamily.EMBEDDING ->
                if (ComputeBackend.CPU in declared && embeds(file, path)) CompatibilityResult.Compatible(ComputeBackend.CPU) else null
            ModelFamily.LLM -> listOf(ComputeBackend.GPU, ComputeBackend.CPU)
                .filter { it in declared }
                .firstOrNull { generates(file, path, it) }
                ?.let { CompatibilityResult.Compatible(it) }
        } ?: CompatibilityResult.Incompatible
        logger.i(TAG) { "compat ${file.id}: $result" }
        return result
    }

    private suspend fun embeds(file: ModelFile, path: File): Boolean = attempt(file, ComputeBackend.CPU) { key ->
        coordinator.withEngine(
            key,
            factory = { factory.embedder(file, path, ComputeBackend.CPU).initializeOrClose { it.initialize() } },
        ) { embedder ->
            val vector = embedder.embedQuery(PROBE_QUERY)
            embedder.rawDimensions == RAW_DIMENSIONS && vector.size == EmbeddingMath.DIMENSIONS &&
                abs(EmbeddingMath.l2Norm(vector) - 1.0) <= NORM_TOLERANCE
        }
    }

    private suspend fun generates(file: ModelFile, path: File, backend: ComputeBackend): Boolean = attempt(file, backend) { key ->
        coordinator.withEngine(
            key,
            factory = { factory.generator(file, path, backend, PROBE_CONFIG).initializeOrClose { it.initialize() } },
        ) { generator ->
            generator.generate(PROBE_PROMPT).filterIsInstance<GenerationEvent.Token>().toList().isNotEmpty()
        }
    }

    /** Runs one probe; any runtime error means «not on this backend». The engine is always released. */
    private suspend fun attempt(file: ModelFile, backend: ComputeBackend, probe: suspend (EngineKey) -> Boolean): Boolean {
        val key = EngineKey(file.id, backend)
        return try {
            probe(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, e) { "compat ${file.id} on $backend failed" }
            false
        } finally {
            withContext(NonCancellable) { coordinator.release(key) }
        }
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val RAW_DIMENSIONS = 768
        const val NORM_TOLERANCE = 1e-3
        const val PROBE_QUERY = "prueba de compatibilidad"
        const val PROBE_PROMPT = "Responde solo con la palabra: listo"
        val PROBE_CONFIG = GenerationConfig(maxNumTokens = 4096, maxOutputTokens = 8, greedy = true)
    }
}

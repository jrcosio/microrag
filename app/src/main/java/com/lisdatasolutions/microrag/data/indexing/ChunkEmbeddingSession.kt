package com.lisdatasolutions.microrag.data.indexing

import android.os.Build
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.DocumentEmbedding
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingEngineException
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.initializeOrClose
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** What the runner embeds chunks with: one engine, held for a stretch of work (F07 research R4). */
interface ChunkEmbedder {
    val backend: ComputeBackend

    /** Throws EmbeddingInputTooLongException or EmbeddingEngineException (research R6). */
    suspend fun embed(content: String, header: String): DocumentEmbedding
}

/** Opens the embedding engine for a stretch of indexing work. */
interface EmbeddingSessions {
    /** The embedding model is installed and verified (F03), so a session can open. */
    fun modelReady(): Boolean

    /**
     * Runs [block] with an embedder. If the engine fails, it is reloaded once and [block] runs again;
     * on a second failure a GPU session goes on with the CPU. Throws EmbeddingEngineException when the
     * CPU fails too. The engine is released when [block] ends, whatever the reason.
     */
    suspend fun <R> use(block: suspend (ChunkEmbedder) -> R): R
}

/** The indexing embedder: EmbeddingGemma 2 270M (D-025), on the GPU when this device passed the check (D-040). */
class ChunkEmbeddingSession(
    private val coordinator: EngineCoordinator,
    private val factory: EngineFactory,
    private val modelPath: () -> File?,
    private val runStore: IngestRunStore,
    private val appVersion: Long,
    private val logger: AppLogger,
    private val isEmulator: Boolean = Build.HARDWARE in EMULATOR_HARDWARE,
) : EmbeddingSessions {

    private val model = ModelCatalog.byId(ModelId.EG2_270M)

    override fun modelReady(): Boolean = modelPath() != null

    override suspend fun <R> use(block: suspend (ChunkEmbedder) -> R): R {
        // Never the LLM and the ingest at once (constitution IV).
        coordinator.releaseGenerators()
        val path = modelPath() ?: throw EmbeddingEngineException(IllegalStateException("Embedding model not verified"))
        var backend = backend(path)
        var reloaded = false
        while (true) {
            val key = EngineKey(model.id, backend)
            try {
                return coordinator.withHeldEngine(key, { load(path, backend) }) { engine -> block(Embedder(engine, backend)) }
            } catch (e: EmbeddingEngineException) {
                when {
                    !reloaded -> reloaded = true
                    backend == ComputeBackend.GPU -> {
                        backend = ComputeBackend.CPU
                        reloaded = false
                    }
                    else -> throw e
                }
                logger.w(TAG) { "ingest engine failed ${e.cause?.javaClass?.simpleName}; going on with $backend" }
            } finally {
                coordinator.release(key)
            }
        }
    }

    private suspend fun load(path: File, backend: ComputeBackend): TextEmbedder = try {
        factory.embedder(model, path, backend).initializeOrClose { it.initialize() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw EmbeddingEngineException(e)
    }

    /** GPU only if it gave the CPU's vectors on this device, model and app version (research R4). */
    private suspend fun backend(path: File): ComputeBackend {
        if (isEmulator) {
            logger.i(TAG) { "ingest backend cpu: emulator" }
            return ComputeBackend.CPU
        }
        val stamp = "${model.sha256}|$appVersion|"
        runStore.gpuCheck()?.takeIf { it.startsWith(stamp) }?.let { check ->
            return if (check.endsWith(GPU_OK)) ComputeBackend.GPU else ComputeBackend.CPU
        }
        val gpu = gpuGivesTheCpuVectors(path)
        runStore.setGpuCheck(stamp + if (gpu) GPU_OK else CPU_ONLY)
        return if (gpu) ComputeBackend.GPU else ComputeBackend.CPU
    }

    private suspend fun gpuGivesTheCpuVectors(path: File): Boolean {
        val cpuKey = EngineKey(model.id, ComputeBackend.CPU)
        return try {
            val gpu = vectors(EngineKey(model.id, ComputeBackend.GPU), path)
            val cpu = vectors(cpuKey, path)
            val cosine = gpu.indices.minOf { EmbeddingMath.cosine(gpu[it], cpu[it]) }
            logger.i(TAG) { "ingest gpu check min cosine ${"%.5f".format(cosine)}" }
            cosine >= MIN_COSINE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG) { "ingest gpu check failed ${(e.cause ?: e).javaClass.simpleName}" }
            false
        } finally {
            coordinator.release(cpuKey)
        }
    }

    private suspend fun vectors(key: EngineKey, path: File): List<FloatArray> =
        coordinator.withEngine(key, { load(path, key.backend) }) { engine -> PROBES.map { engine.embedDocumentFitting(it, PROBE_HEADER).vector } }

    private class Embedder(private val engine: TextEmbedder, override val backend: ComputeBackend) : ChunkEmbedder {
        override suspend fun embed(content: String, header: String): DocumentEmbedding = engine.embedDocumentFitting(content, header)
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val GPU_OK = "gpu"
        const val CPU_ONLY = "cpu"

        /** D-024 and D-030: below this, a phone indexes on the CPU. */
        const val MIN_COSINE = 0.99

        /** LiteRT-LM's GPU init blocks in native code on the emulator (F04 report §6). */
        val EMULATOR_HARDWARE = setOf("ranchu", "goldfish")

        const val PROBE_HEADER = "Prueba del dispositivo"
        val PROBES = listOf(
            "El plazo para solicitar la devolución es de treinta días naturales desde la entrega del producto.",
            "Para cambiar el filtro de aceite, apague el motor y espere a que se enfríe durante diez minutos.",
            "La factura debe incluir el número de identificación fiscal del emisor y la fecha de expedición.",
        )
    }
}

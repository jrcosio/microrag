package com.lisdatasolutions.microrag.data.indexing

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.PageSpace
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingEngineException
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.EngineKind
import com.lisdatasolutions.microrag.rag.runtime.ImageEmbedder
import com.lisdatasolutions.microrag.rag.runtime.ImageEmbedderFactory
import com.lisdatasolutions.microrag.rag.runtime.initializeOrClose
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** What the runner embeds page images with: the raw 768D vector, which it truncates and normalizes. */
fun interface PageEmbedder {
    /** Throws [EmbeddingEngineException] when the engine fails. */
    suspend fun embed(image: ByteArray): FloatArray
}

/** Opens the 440M's image engine for the page phase of one document (F19 contract I3). */
interface PageEmbeddingSessions {
    /** The 440M is installed and verified, so a session can open. */
    suspend fun modelReady(): Boolean

    /** Runs [block] with the image engine held, and releases it when [block] ends, whatever the reason. */
    suspend fun <R> use(block: suspend (PageEmbedder) -> R): R
}

/**
 * The page embedder (F19 research R9): the 440M with its vision encoder, always on the CPU (constitution 1.2.0
 * IV), at [tokensPerImage]. Its key is not the 440M's text engine (analyze H2), so a question asked meanwhile
 * gets a text engine of its own; the ingest never holds both, as the text session has ended before this one.
 */
class PageEmbeddingSession(
    private val coordinator: EngineCoordinator,
    private val factory: ImageEmbedderFactory,
    /** The verified 440M, or null; may scan the models folder (the worker can start in a fresh process). */
    private val modelPath: suspend () -> File?,
    private val logger: AppLogger,
    private val tokensPerImage: Int = PageSpace.TOKENS_PER_IMAGE,
) : PageEmbeddingSessions {

    private val model = ModelCatalog.byId(ModelId.EG2_440M)
    private val key = EngineKey(model.id, ComputeBackend.CPU, EngineKind.IMAGE_EMBEDDER)

    override suspend fun modelReady(): Boolean = modelPath() != null

    override suspend fun <R> use(block: suspend (PageEmbedder) -> R): R {
        // Never the LLM and the ingest at once (constitution IV).
        coordinator.releaseGenerators()
        val path = modelPath() ?: throw EmbeddingEngineException(IllegalStateException("Page model not verified"))
        logger.i(TAG) { "ingest pages engine ${model.id} cpu tokens=$tokensPerImage" }
        return try {
            coordinator.withHeldEngine(key, { load(path) }) { engine -> block(Embedder(engine)) }
        } finally {
            withContext(NonCancellable) { coordinator.release(key) }
        }
    }

    private suspend fun load(path: File): ImageEmbedder = try {
        factory.imageEmbedder(model, path, ComputeBackend.CPU, tokensPerImage).initializeOrClose { it.initialize() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw EmbeddingEngineException(e)
    }

    private class Embedder(private val engine: ImageEmbedder) : PageEmbedder {
        override suspend fun embed(image: ByteArray): FloatArray = try {
            engine.embedImage(image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmbeddingEngineException) {
            throw e
        } catch (e: Exception) {
            throw EmbeddingEngineException(e)
        }
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}

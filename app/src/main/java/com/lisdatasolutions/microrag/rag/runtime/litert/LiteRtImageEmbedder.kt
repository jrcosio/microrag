package com.lisdatasolutions.microrag.rag.runtime.litert

import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.ImageEmbedder
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The 440M with images (F19 research R7), as measured in F18: the text side on the CPU and the vision encoder on
 * [backend]. The file only declares vision on the CPU, so the app always passes CPU; the debug diagnostic also
 * tries the GPU and reports that it is not available.
 */
internal class LiteRtImageEmbedder(
    override val model: ModelId,
    private val modelPath: File,
    override val backend: ComputeBackend,
    private val cacheDir: File,
    override val visionTokensPerImage: Int,
    private val dispatcher: CoroutineDispatcher,
) : ImageEmbedder {

    @Volatile
    private var engine: EmbeddingEngine? = null

    override suspend fun initialize() = withContext(dispatcher) {
        if (engine != null) return@withContext
        val created = EmbeddingEngine(
            EmbeddingEngineConfig(
                modelPath = modelPath.path,
                backend = ComputeBackend.CPU.toLiteRt(),
                visionBackend = backend.toLiteRt(),
                cacheDir = cacheDir.path,
                visionTokensPerImage = visionTokensPerImage,
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

    override suspend fun embedImage(bytes: ByteArray): FloatArray = withContext(dispatcher) {
        val engine = checkNotNull(engine) { "Image embedder for $model is not initialized" }
        engine.computeEmbedding(
            listOf(InputData.Image(bytes)),
            EmbeddingOptions(normalize = false, visionTokensPerImage = visionTokensPerImage),
        ).embedding
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}

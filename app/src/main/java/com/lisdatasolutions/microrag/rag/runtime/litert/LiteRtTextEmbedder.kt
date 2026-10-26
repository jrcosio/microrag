package com.lisdatasolutions.microrag.rag.runtime.litert

import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.DocumentEmbedding
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingEngineException
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingInputTooLongException
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingOverflow
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** EmbeddingGemma 2 on LiteRT-LM. The full output is requested and truncated in our own code. */
internal class LiteRtTextEmbedder(
    override val model: ModelId,
    private val modelPath: File,
    override val backend: ComputeBackend,
    private val cacheDir: File,
    private val dispatcher: CoroutineDispatcher,
) : TextEmbedder {

    @Volatile
    private var engine: EmbeddingEngine? = null

    @Volatile
    override var rawDimensions: Int? = null
        private set

    override suspend fun initialize() = withContext(dispatcher) {
        if (engine != null) return@withContext
        val created = EmbeddingEngine(
            EmbeddingEngineConfig(
                modelPath = modelPath.path,
                backend = backend.toLiteRt(),
                cacheDir = cacheDir.path,
                // Selects the 128..512 token signatures: queries and ~350-token chunks (research R3).
                maxInputLength = MAX_INPUT_LENGTH,
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

    override suspend fun embedQuery(text: String): FloatArray = embed(EmbeddingPrefixes.query(text))

    override suspend fun embedDocument(content: String, header: String): FloatArray =
        embed(EmbeddingPrefixes.document(content, header))

    override suspend fun embedDocumentFitting(content: String, header: String): DocumentEmbedding = withContext(dispatcher) {
        var input = EmbeddingPrefixes.document(content, header)
        repeat(EmbeddingOverflow.MAX_ATTEMPTS) { attempt ->
            try {
                return@withContext DocumentEmbedding(compute(input), cuts = attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isInputTooLong()) throw EmbeddingEngineException(e)
                input = EmbeddingOverflow.cut(input)
            }
        }
        throw EmbeddingInputTooLongException()
    }

    private suspend fun embed(input: String): FloatArray = withContext(dispatcher) { compute(input) }

    /** Blocking; runs on [dispatcher]. */
    private fun compute(input: String): FloatArray {
        val engine = checkNotNull(engine) { "Embedder for $model is not initialized" }
        val raw = engine.computeEmbedding(
            listOf(InputData.Text(input)),
            EmbeddingOptions(normalize = false, outputSize = null),
        ).embedding
        rawDimensions = raw.size
        return EmbeddingMath.truncateAndNormalize(raw)
    }

    /** What LiteRT-LM 0.18.1 says for an input over [MAX_INPUT_LENGTH] tokens (F07 research R6, T002). */
    private fun Exception.isInputTooLong(): Boolean =
        this is LiteRtLmJniException && message?.contains(TOO_LONG_MESSAGE) == true

    override fun close() {
        engine?.close()
        engine = null
    }

    private companion object {
        const val MAX_INPUT_LENGTH = 512
        const val TOO_LONG_MESSAGE = "exceeds maximum supported signature length"
    }
}

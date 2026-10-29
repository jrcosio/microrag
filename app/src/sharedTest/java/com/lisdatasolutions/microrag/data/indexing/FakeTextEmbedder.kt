package com.lisdatasolutions.microrag.data.indexing

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.DocumentEmbedding
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingEngineException
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingInputTooLongException
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingOverflow
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.rag.runtime.QueryEmbedding
import com.lisdatasolutions.microrag.rag.runtime.QueryOverflow
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import java.util.Collections
import kotlin.random.Random

/**
 * Deterministic [TextEmbedder] for JVM and instrumented tests (F07 T020): the vector of a text is a
 * unit vector seeded by its hash, so the same input always gives the same vector. It can reject long
 * inputs, as LiteRT does over 512 tokens, and fail as a broken engine would, for documents and, through
 * embedQueryFitting, for queries (F10).
 */
class FakeTextEmbedder(
    override val backend: ComputeBackend = ComputeBackend.CPU,
    override val model: ModelId = ModelId.EG2_270M,
    /** Changes every vector, to play a backend whose vectors differ from the others. */
    private val salt: String = "",
) : TextEmbedder {

    /** Every embedded input, in order. */
    val embedded: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Inputs longer than this many characters are rejected as too long. */
    @Volatile
    var maxInputChars: Int = Int.MAX_VALUE

    /** The next calls that fail as an engine error. */
    @Volatile
    var failEngineTimes: Int = 0

    /** Runs before every embedding: a test can block here or count calls. */
    @Volatile
    var beforeEmbedding: () -> Unit = {}

    @Volatile
    var closed = false
        private set

    override val rawDimensions: Int = RAW_DIMENSIONS

    override suspend fun initialize() = Unit

    override suspend fun embedQuery(text: String): FloatArray = vectorOf(salt + EmbeddingPrefixes.query(text))

    override suspend fun embedDocument(content: String, header: String): FloatArray =
        embedDocumentFitting(content, header).vector

    override suspend fun embedDocumentFitting(content: String, header: String): DocumentEmbedding {
        var input = EmbeddingPrefixes.document(content, header)
        repeat(EmbeddingOverflow.MAX_ATTEMPTS) { attempt ->
            beforeEmbedding()
            if (failEngineTimes > 0) {
                failEngineTimes--
                throw EmbeddingEngineException(IllegalStateException("fake engine failure"))
            }
            if (input.length <= maxInputChars) {
                embedded += input
                return DocumentEmbedding(vectorOf(salt + input), cuts = attempt)
            }
            input = EmbeddingOverflow.cut(input)
        }
        throw EmbeddingInputTooLongException()
    }

    /** Same rules as for documents: [beforeEmbedding], [failEngineTimes] and [maxInputChars] on every attempt (F10). */
    override suspend fun embedQueryFitting(question: String, previous: String?): QueryEmbedding {
        var step = QueryOverflow.Step(previous, question)
        repeat(EmbeddingOverflow.MAX_ATTEMPTS) { attempt ->
            beforeEmbedding()
            if (failEngineTimes > 0) {
                failEngineTimes--
                throw EmbeddingEngineException(IllegalStateException("fake engine failure"))
            }
            val input = EmbeddingPrefixes.query(QueryOverflow.join(step.previous, step.question))
            if (input.length <= maxInputChars) {
                embedded += input
                return QueryEmbedding(vectorOf(salt + input), cuts = attempt)
            }
            step = QueryOverflow.next(step.previous, step.question)
        }
        throw EmbeddingInputTooLongException()
    }

    override fun close() {
        closed = true
    }

    companion object {
        const val RAW_DIMENSIONS = 768

        fun vectorOf(text: String): FloatArray {
            val random = Random(text.hashCode())
            return EmbeddingMath.truncateAndNormalize(FloatArray(RAW_DIMENSIONS) { random.nextFloat() * 2f - 1f })
        }
    }
}

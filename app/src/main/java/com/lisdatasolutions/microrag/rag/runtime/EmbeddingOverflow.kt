package com.lisdatasolutions.microrag.rag.runtime

/**
 * D-036: LiteRT-LM 0.18.1 rejects inputs over 512 tokens and has no overflow strategy, so the embedded
 * text is cut to 90 % of its length, back to its last space, and tried again (F07 research R6). Exact
 * port of `overflow_cut` in offline/embedding_text.py: lengths count code points, as Python's len().
 * Only the embedded text is cut, never the stored chunk.
 */
object EmbeddingOverflow {

    /** Attempts in total, the first with the whole text, as `OVERFLOW_ATTEMPTS` offline. */
    const val MAX_ATTEMPTS = 12

    private const val KEEP = 0.9

    fun cut(text: String): String {
        val keep = (text.codePointCount(0, text.length) * KEEP).toInt()
        val cut = text.substring(0, text.offsetByCodePoints(0, keep))
        val space = cut.lastIndexOf(' ')
        return if (space >= 0) cut.substring(0, space) else cut
    }
}

/** A document chunk's vector and how many D-036 cuts its embedded text needed. */
class DocumentEmbedding(val vector: FloatArray, val cuts: Int)

/** The embedded text still did not fit after [EmbeddingOverflow.MAX_ATTEMPTS] attempts. */
class EmbeddingInputTooLongException : RuntimeException("Embedding input still too long after the D-036 cuts")

/** Any other failure of the embedding engine (F07 research R4: reload once, then CPU, then a document error). */
class EmbeddingEngineException(cause: Throwable) : RuntimeException(cause)

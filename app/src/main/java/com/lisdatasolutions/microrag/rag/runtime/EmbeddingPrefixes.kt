package com.lisdatasolutions.microrag.rag.runtime

/**
 * EmbeddingGemma 2 task prefixes, exactly as documented by LiteRT-LM (constitution III).
 * Text is trimmed before the prefix is added. F04 also stores these values in `rag_meta`.
 */
object EmbeddingPrefixes {
    const val DOCUMENT = "task: search result | text: "
    const val QUERY = "task: search query | text: "

    fun query(text: String): String = QUERY + text.trim()

    /** [header] is the contextual "document › section" path; it is only part of the embedded text. */
    fun document(content: String, header: String = ""): String =
        DOCUMENT + listOf(header.trim(), content.trim()).filter { it.isNotEmpty() }.joinToString("\n")
}

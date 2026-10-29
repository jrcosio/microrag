package com.lisdatasolutions.microrag.rag.retrieval

import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.rag.ingest.PyText
import com.lisdatasolutions.microrag.rag.runtime.QueryOverflow
import com.lisdatasolutions.microrag.rag.text.SpanishText

/**
 * Follow-up questions without an LLM, exact port of offline/eval/retrieval.py (F10 research R2): a short
 * question, or one that starts with a connector, is searched after the previous question.
 */
object FollowUpRule {
    val CONNECTORS = listOf("y ", "¿y ", "pero ", "entonces", "¿y si", "y si ")

    /** Fewer normalized tokens than this make a follow-up. */
    const val MAX_TOKENS = 8

    fun isFollowUp(question: String): Boolean {
        val text = PyText.strip(question).lowercase()
        return SpanishText.tokens(question).size < MAX_TOKENS || CONNECTORS.any { text.startsWith(it) }
    }

    /** The previous question, a space and [question] for a follow-up with a previous question; else [question]. */
    fun searchText(question: String, previous: String?, enabled: Boolean = RagParameters.FOLLOW_UP_RULE): String =
        if (enabled && !previous.isNullOrEmpty() && isFollowUp(question)) QueryOverflow.join(previous, question) else question
}

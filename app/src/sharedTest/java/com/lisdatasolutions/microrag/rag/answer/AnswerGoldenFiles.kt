package com.lisdatasolutions.microrag.rag.answer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reading of the answer golden (F11 data-model §7), shared by the JVM tests (`../offline/golden`) and the
 * instrumented ones (test APK assets). Written by `offline/gen_answer_golden.py`.
 */
object AnswerGoldenFiles {
    const val GOLDEN = "answer_golden.jsonl"

    fun cases(text: String): List<GoldenAnswerCase> = text.split("\n").filter { it.isNotBlank() }.map { Json.decodeFromString(it) }
}

/** One live question of the evaluation set: the prompt inputs and output, and the generated answer read by judge.py. */
@Serializable
data class GoldenAnswerCase(
    val id: String,
    val template: String,
    val question: String,
    /** [question, generated answer] of the previous turn, or null. */
    val previous: List<String>?,
    val blocks: List<GoldenPromptBlock>,
    /** Code points, as Python's len(). */
    @SerialName("prompt_chars") val promptChars: Int,
    @SerialName("prompt_sha256") val promptSha256: String,
    /** The whole prompt in ten fixed rows only. */
    val prompt: String?,
    val answer: String,
    val citations: List<Int>,
    @SerialName("cited_order") val citedOrder: List<Int>,
    val refusal: Boolean,
    val norm: String,
)

@Serializable
data class GoldenPromptBlock(
    @SerialName("document_name") val documentName: String?,
    @SerialName("heading_path") val headingPath: String?,
    val page: Int?,
    val text: String,
)

package com.lisdatasolutions.microrag.domain.chat

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import kotlinx.coroutines.flow.Flow

/** Gemma for the chat (F11 research R5), behind the engine coordinator. */
interface AnswerEngine {
    /** Gemma verified and prepared (F03 rule); refreshes once when its path is unknown. */
    suspend fun ready(): Boolean

    /** The prepared backend, or null when Gemma is not ready; CPU drives the C9 notice. */
    fun preparedBackend(): Flow<ComputeBackend?>

    /** Loads Gemma without generating: with vision when the next answer will carry a page (F20 research R5). */
    suspend fun warmUp(visual: Boolean = false)

    /** Streams the answer; cancelling the collector stops the engine. The engine follows the prompt's kind (R5). */
    fun generate(prompt: AnswerPrompt): Flow<AnswerEvent>

    /** Today's text prompt. */
    fun generate(prompt: String): Flow<AnswerEvent> = generate(AnswerPrompt.Text(prompt))
}

sealed interface AnswerEvent {
    data class Token(val delta: String) : AnswerEvent

    data class Finished(val ttftMs: Double, val totalMs: Double, val decodeTokensPerSecond: Double?) : AnswerEvent
}

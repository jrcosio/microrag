package com.lisdatasolutions.microrag.domain.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The saved conversations (F11 contracts/chat-api.md; `chat.db`, research R9). */
interface ChatRepository {
    /** Null until [open] has looked at the file; nothing is promised before (constitution V). */
    val storageState: StateFlow<ChatStorageState?>

    /** Opens `chat.db` once, creating it when missing; later calls return the known state. */
    suspend fun open(): ChatStorageState

    /** The turns of [id] in order; empty when it does not exist. Emits after every write. */
    fun conversation(id: Long): Flow<List<ChatTurn>>

    /** One turn with its sources; null when it does not exist. */
    suspend fun turn(id: Long): ChatTurn?

    /** The conversation with the most recent question. */
    suspend fun lastConversationId(): Long?

    suspend fun createConversation(now: Long): Long

    suspend fun insertTurn(conversationId: Long, question: String, partialCoverage: Boolean, now: Long): ChatTurn

    suspend fun updateTurn(turnId: Long, update: TurnUpdate)

    suspend fun replaceSources(turnId: Long, sources: List<AnswerSource>)

    /** Back to RETRIEVING with an empty answer, no sources and no failure (contract A10). */
    suspend fun resetForRetry(turnId: Long)

    /** RETRIEVING and GENERATING turns become INTERRUPTED (research R8); returns how many. */
    suspend fun markInterrupted(now: Long): Int

    suspend fun deleteConversation(id: Long)

    /** For F12 «Borrar historial»; no UI in F11. */
    suspend fun deleteAll()
}

/** A write on `chat.db` that could not be done: the file is unavailable (C17) or the disk is full (C16). */
class ChatStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The fields a turn update writes; null leaves a field as it is. */
data class TurnUpdate(
    val state: TurnState? = null,
    val answer: String? = null,
    val failure: TurnFailure? = null,
    val refusal: Boolean? = null,
    val promptBlocks: Int? = null,
    val finishedAt: Long? = null,
)

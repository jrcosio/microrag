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

    /** The conversation with the most recent question; never one without questions (F13 Q1). */
    suspend fun lastConversationId(): Long?

    /**
     * F13 (research R1, R2): the first [limit] conversations with questions, by last activity, and their
     * total. Read again only when a question or a deletion changes the list. Fails with
     * [ChatStorageException] when `chat.db` cannot be read.
     */
    fun conversations(limit: Int): Flow<ConversationPage>

    /** F17 (contract S2): the conversation and its [scope] in one transaction. */
    suspend fun createConversation(now: Long, scope: ChatScope = ChatScope.WholeLibrary): Long

    suspend fun insertTurn(conversationId: Long, question: String, partialCoverage: Boolean, now: Long): ChatTurn

    suspend fun updateTurn(turnId: Long, update: TurnUpdate)

    suspend fun replaceSources(turnId: Long, sources: List<AnswerSource>)

    /** Back to RETRIEVING with an empty answer, no sources and no failure (contract A10). */
    suspend fun resetForRetry(turnId: Long)

    /** RETRIEVING and GENERATING turns become INTERRUPTED (research R8); returns how many. */
    suspend fun markInterrupted(now: Long): Int

    suspend fun deleteConversation(id: Long)

    /** Every conversation, with its turns, sources and scopes, and the last scope too (F12 «Borrar historial», F17 FR-020c). */
    suspend fun deleteAll()

    /** F17 (research R7): the saved scope as it was saved; [ChatScope.WholeLibrary] without rows or without the file. */
    suspend fun conversationScope(id: Long): ChatScope

    /** Replaces the scope of [id]; its `updated_at` stays, so Historial keeps its order (S4). */
    suspend fun setConversationScope(id: Long, scope: ChatScope)

    /** The scope «Nuevo chat» starts with (FR-020b); [ChatScope.WholeLibrary] the first time or without the file. */
    suspend fun lastScope(): ChatScope

    suspend fun setLastScope(scope: ChatScope)

    /** Conversations with at least one turn (F12 S6); throws [ChatStorageException] when the file is unavailable. */
    suspend fun conversationCount(): Int

    /** Throws [ChatStorageException] when the file is unavailable. */
    suspend fun conversationExists(id: Long): Boolean
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

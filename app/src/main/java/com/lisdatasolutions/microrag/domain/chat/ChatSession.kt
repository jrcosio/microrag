package com.lisdatasolutions.microrag.domain.chat

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Answers questions, one at a time in the whole app (F11 contracts/chat-api.md, rules A1–A15). */
interface ChatSession {
    val active: StateFlow<ActiveAnswer?>

    /** Starts an answer; the conversation id (new when [conversationId] is null), or null when nothing started. */
    suspend fun ask(conversationId: Long?, question: String): Long?

    /** Stops the answer in progress; it stays CANCELLED with its text (A9). */
    fun cancel()

    /** False when [turnId] is not FAILED or INTERRUPTED, another answer runs, or the chat is not Ready (A10). */
    suspend fun retry(turnId: Long): Boolean

    /** Cancels any answer in progress, as a new conversation starts (research R6). */
    fun startNewConversation()

    /**
     * F13 Q6: [conversationId] is shown, from Historial. An answer of another conversation stops as «Nuevo
     * chat» stops it, and this returns once it is saved; an answer of this one goes on.
     */
    suspend fun openConversation(conversationId: Long)

    /**
     * F13 Q7: stops the answer of [conversationId], if any, then deletes it, under the session lock so no
     * question starts in between. Throws ChatStorageException; announces only a confirmed deletion.
     */
    suspend fun delete(conversationId: Long)

    /** F13 Q8: the id of every conversation deleted with [delete]; [deleteAll] says it through [historyCleared]. */
    val deletions: SharedFlow<Long>

    /** Bumped by every [deleteAll]; the chat screen then drops the conversation it shows (F12 P11). */
    val historyCleared: StateFlow<Long>

    /** Stops the answer in progress, then deletes every conversation (F12 P9). Throws ChatStorageException. */
    suspend fun deleteAll()
}

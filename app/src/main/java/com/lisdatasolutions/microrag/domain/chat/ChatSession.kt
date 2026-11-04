package com.lisdatasolutions.microrag.domain.chat

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Answers questions, one at a time in the whole app (F11 contracts/chat-api.md, rules A1–A15). */
interface ChatSession {
    val active: StateFlow<ActiveAnswer?>

    /**
     * Starts an answer; the conversation id (new when [conversationId] is null), or null when nothing started.
     * F17 (contract S2): it answers within [scope] as resolved now, which the conversation keeps and which is
     * the last scope from then on; the answer keeps that scope even if it changes meanwhile (FR-019).
     */
    suspend fun ask(conversationId: Long?, question: String, scope: ChatScope = ChatScope.WholeLibrary): Long?

    /** Stops the answer in progress; it stays CANCELLED with its text (A9). */
    fun cancel()

    /**
     * False when [turnId] is not FAILED or INTERRUPTED, another answer runs, or the chat is not Ready (A10).
     * F17 (analyze H3): it answers within its conversation's scope, as resolved now.
     */
    suspend fun retry(turnId: Long): Boolean

    /** F17 S3: the selector applied [scope] to [conversationId], for its next question; it is the last scope too. */
    suspend fun setScope(conversationId: Long, scope: ChatScope)

    /** F17 S1: the scope of [conversationId] as it means now, with what fell out of it; rewritten once if it changed (M8). */
    suspend fun scopeOf(conversationId: Long): ResolvedScope

    /** F17 FR-020b: the scope «Nuevo chat» starts with, as it means now. */
    suspend fun lastScope(): ResolvedScope

    /** F17 S3: the scope of a chat without questions yet, which only becomes the last one. */
    suspend fun setLastScope(scope: ChatScope)

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

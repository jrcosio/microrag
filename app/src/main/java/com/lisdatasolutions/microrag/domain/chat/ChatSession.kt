package com.lisdatasolutions.microrag.domain.chat

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

    suspend fun delete(conversationId: Long)

    /** Bumped by every [deleteAll]; the chat screen then drops the conversation it shows (F12 P11). */
    val historyCleared: StateFlow<Long>

    /** Stops the answer in progress, then deletes every conversation (F12 P9). Throws ChatStorageException. */
    suspend fun deleteAll()
}

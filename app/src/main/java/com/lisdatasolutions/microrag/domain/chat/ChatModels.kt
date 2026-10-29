package com.lisdatasolutions.microrag.domain.chat

// The chat (F11 data-model §2): conversations, their turns and the cited sources. A turn is one question
// and its answer, in the order of the conversation; ids and numbers come from the index, never invented.

data class Conversation(val id: Long, val createdAt: Long, val updatedAt: Long)

enum class TurnState {
    RETRIEVING,
    /** From the moment the model receives the prompt (prefill included). */
    GENERATING,
    COMPLETED,
    /** The user stopped it, started another conversation or deleted this one; final. */
    CANCELLED,
    FAILED,
    /** The app went to the background, memory ran low or the process died (C13); it can be retried. */
    INTERRUPTED,
}

/** Why a turn failed: the index (C8) or anything else (C7). */
enum class TurnFailure { INDEX, GENERATION }

/** One question and its answer, in conversation order. */
data class ChatTurn(
    val id: Long,
    val conversationId: Long,
    /** 0, 1, 2… within the conversation. */
    val ordinal: Int,
    val question: String,
    /** Raw model text, with the prompt's [n]; empty until the first token. */
    val answer: String,
    val state: TurnState,
    /** Only when [state] is FAILED. */
    val failure: TurnFailure?,
    /** The model answered the refusal sentence (C1), or there were no blocks. */
    val refusal: Boolean,
    /** Included documents were not indexed yet when the question was asked (C6). */
    val partialCoverage: Boolean,
    /** Blocks in the prompt: the valid range of [n]. */
    val promptBlocks: Int,
    /** Cited only, in first-citation order; empty while the answer is in progress. */
    val sources: List<AnswerSource>,
    val createdAt: Long,
    val finishedAt: Long?,
)

/** A cited block, as it was when the answer was given (research R12). */
data class AnswerSource(
    /** 1-based display number (D-053). */
    val position: Int,
    /** Its [n] in the prompt. */
    val promptNumber: Int,
    val documentId: Long,
    /** First chunk of the block. */
    val chunkId: Long,
    val firstOrdinal: Int,
    val lastOrdinal: Int,
    val documentName: String,
    val page: Int?,
)

/** A block of the prompt, by its [n]: what a chip opens while the answer streams. */
data class PromptBlockRef(
    val promptNumber: Int,
    val documentId: Long,
    /** First chunk of the block. */
    val chunkId: Long,
    val firstOrdinal: Int,
    val lastOrdinal: Int,
    val documentName: String,
    val page: Int?,
)

enum class SourceAvailability { AVAILABLE, FRAGMENT_GONE, DOCUMENT_GONE }

/** The answer being generated, kept in memory between checkpoints (research R6). */
data class ActiveAnswer(
    val conversationId: Long,
    val turnId: Long,
    /** RETRIEVING or GENERATING; a finished state only while [saveFailed]. */
    val state: TurnState,
    val text: String,
    /** Every prompt block; the formatter renumbers by first citation. */
    val blocks: List<PromptBlockRef>,
    /** C16: the turn could not be saved; kept until the next question. */
    val saveFailed: Boolean = false,
)

/** Whether the chat can be used, in precedence order (research R11). */
sealed interface ChatAvailability {
    /** C17: the saved conversations cannot be opened; nothing is deleted. */
    data object StorageUnavailable : ChatAvailability

    /** C5: a model is missing, unverified or not prepared. */
    data object ModelsNotReady : ChatAvailability

    /** C8: the index cannot be opened. */
    data object IndexUnavailable : ChatAvailability

    /** C3: no documents. */
    data object EmptyLibrary : ChatAvailability

    /** C14: an indexing batch is running, so Gemma cannot be loaded (constitution IV, D-053). */
    data object WaitingForIndexing : ChatAvailability

    /** C4: documents, but none indexed and included. */
    data object NothingReady : ChatAvailability

    /** [cpuMode]: Gemma prepared on CPU only (C9); [partialCoverage]: included documents not indexed yet (C6). */
    data class Ready(val cpuMode: Boolean, val partialCoverage: Boolean) : ChatAvailability
}

/** Whether `chat.db` can be used (contract A15). */
enum class ChatStorageState { OK, UNAVAILABLE }

object ChatLimits {
    /** Longest question, in UTF-16 units as the composer and the session count it (research R10). */
    const val MAX_QUESTION_CHARS = 1000

    /** The partial answer is saved at most this often (research R6). */
    const val CHECKPOINT_MS = 1000L
}

package com.lisdatasolutions.microrag.feature.chat

import androidx.annotation.StringRes
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.model.CitationUi
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSummary

/** What the chat shows (F11 data-model §5, contracts/chat-ui.md). */
data class ChatUiState(
    /** Null for a conversation not saved yet. */
    val conversationId: Long? = null,
    /** Null until the first check: nothing is promised before (constitution V). */
    val availability: ChatAvailability? = null,
    val turns: List<TurnUi> = emptyList(),
    val question: String = "",
    /** An answer of this conversation is in progress: the composer shows stop. */
    val answering: Boolean = false,
    val confirmDelete: Boolean = false,
    /** «Respuesta copiada» under this turn, on Android 12 only (research R14). */
    val copiedTurnId: Long? = null,
    /** The last change TalkBack announces (FR-039). */
    val announcement: ChatAnnouncement? = null,
    /** F17 K7: what the chip says; null until the scope of the conversation is known. */
    val scope: ScopeSummary? = null,
    /** K8: the «Buscar en» sheet is open. */
    val selectingScope: Boolean = false,
    /** K10: what fell out of the scope when it was opened, said once. */
    val scopeNotice: ScopeNotice? = null,
) {
    val ready: Boolean get() = availability is ChatAvailability.Ready
    val canSend: Boolean get() = ready && !answering && question.isNotBlank()
    val cpuMode: Boolean get() = (availability as? ChatAvailability.Ready)?.cpuMode == true
}

/** One question and its answer, ready for the card. */
data class TurnUi(
    val id: Long,
    val question: String,
    val state: AnswerState,
    /** The phase while [state] is Streaming. */
    val status: TurnStatus?,
    val content: AnswerContent,
    /** The answer is the refusal (C1): the card shows the MD's sentence and no sources. */
    val refusal: Boolean,
    /** C7 (null, F01's text), C8 or C13. */
    @param:StringRes val failureText: Int?,
    /** C2, C6 and C16. */
    val notes: List<Int>,
    val sources: List<SourceRow>,
    /** What chip `n` opens: the cited source, also while the answer streams; null for a deleted document (F13 Q12). */
    val chipTargets: List<SourceTarget?>,
    /** A saved FAILED or INTERRUPTED turn: the card shows «Reintentar». */
    val retryable: Boolean,
    /** «Reintentar» works now: the chat is Ready and no other answer runs (A10). */
    val canRetry: Boolean,
)

enum class TurnStatus { SEARCHING, GENERATING }

/** A row under «Fuentes consultadas». */
data class SourceRow(val position: Int, val target: SourceTarget, val citation: CitationUi)

/** What a source opens: the document detail on its first fragment (D-043). */
data class SourceTarget(val documentId: Long, val chunkId: Long)

/** K10, in this order of precedence. */
enum class ScopeNotice { WHOLE_AGAIN, FOLDER_GONE, DOCUMENT_GONE }

/** F13 R6: [DELETE_FAILED], a deletion `chat.db` refused; the conversation stays. */
enum class ChatAnnouncement { SEARCHING, GENERATING, COMPLETED, CANCELLED, INTERRUPTED, FAILED, DELETED, DELETE_FAILED, COPIED }

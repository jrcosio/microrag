package com.lisdatasolutions.microrag.feature.history

import java.time.ZoneId

/** What Historial shows (data-model §6, contracts/history-ui.md §1). */
data class HistoryUiState(
    val content: HistoryScreenContent = HistoryScreenContent.Loading,
    /** The conversation the chat shows, marked «Abierta» (research R4). */
    val openConversationId: Long? = null,
    /** The conversation waiting for its confirmation (H6); kept in the SavedStateHandle. */
    val confirmDeleteId: Long? = null,
    /** The pending one is being answered: the dialog adds «La respuesta en curso se detendrá.». */
    val confirmDeleteAnswering: Boolean = false,
    /** The zone that grouped the rows; their dates are shown in it too (research R3). */
    val zone: ZoneId = ZoneId.systemDefault(),
)

sealed interface HistoryScreenContent {
    data object Loading : HistoryScreenContent

    data object Empty : HistoryScreenContent

    /** C17: the saved conversations cannot be read; no buttons (D-065). */
    data object Error : HistoryScreenContent

    data class Sections(val sections: List<HistorySection>, val hasMore: Boolean, val loaded: Int) : HistoryScreenContent
}

/** One-off events: the route leaves Historial, or a snackbar says how a deletion went. */
sealed interface HistoryEvent {
    data class Open(val conversationId: Long) : HistoryEvent

    data object NewChat : HistoryEvent

    data object Deleted : HistoryEvent

    data object DeleteFailed : HistoryEvent
}

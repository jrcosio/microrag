package com.lisdatasolutions.microrag.feature.home

import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus

/** P03 state (F09 data-model §3): the local AI, the library summary and an active indexing run. */
data class HomeUiState(
    /** Null while the first check runs: no chip (FR-009). */
    val ai: LocalAiStatus? = null,
    val summary: SummaryState = SummaryState.Loading,
    /** Null when no run is active (D-046). */
    val indexing: IndexingCard? = null,
)

sealed interface SummaryState {
    data object Loading : SummaryState

    /** 0 documents (FR-015). */
    data object Empty : SummaryState

    data class Loaded(val documents: Int, val chunks: Int) : SummaryState

    /** [unavailable]: the index is incompatible or unreadable; otherwise the load failed. */
    data class Error(val unavailable: Boolean) : SummaryState
}

/** An active F07 run (research R3): its chunks and, when paused, why. */
data class IndexingCard(val done: Int, val total: Int?, val pause: PauseReason?)

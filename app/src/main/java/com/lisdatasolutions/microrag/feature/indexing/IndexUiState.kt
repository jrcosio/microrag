package com.lisdatasolutions.microrag.feature.indexing

import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.PauseReason

/** P08 state (F07 contracts/index-ui.md): the run as the repository reports it, and a pending action. */
data class IndexUiState(
    val indexing: IndexingState = IndexingState.Loading,
    /** Pause, resume or retry is being applied: the action is disabled meanwhile. */
    val busy: Boolean = false,
) {
    /** The fixed action at the bottom, if the state has one (rule U4 for its size). */
    val action: IndexAction?
        get() = when (val state = indexing) {
            is IndexingState.Active -> when (state.pause) {
                null, PauseReason.THERMAL, PauseReason.SYSTEM_LIMIT -> IndexAction.Pause
                PauseReason.USER, PauseReason.NO_SPACE -> IndexAction.Resume
                PauseReason.MODEL_MISSING -> null
            }
            is IndexingState.Idle -> state.failed.singleOrNull()?.takeIf { it.canRetry }?.let { IndexAction.Retry(it.id) }
            else -> null
        }
}

sealed interface IndexAction {
    data object Pause : IndexAction
    data object Resume : IndexAction
    data class Retry(val documentId: Long) : IndexAction
}

/** One-off requests that need the navigation. */
sealed interface IndexEvent {
    /** P7: the embedding model is missing; its setup lives in F03's Preparación. */
    data object OpenSetup : IndexEvent
}

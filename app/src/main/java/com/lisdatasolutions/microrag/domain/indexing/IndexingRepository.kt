package com.lisdatasolutions.microrag.domain.indexing

import kotlinx.coroutines.flow.Flow

/** The indexing run as the UI sees and controls it (F07 contracts/indexing-api.md). */
interface IndexingRepository {
    /** The Index screen state, recomputed when the index, the work or the device's heat change. */
    val state: Flow<IndexingState>

    /**
     * «Iniciar indexación»: drops finished documents from the run, adds every non-indexed one (pending,
     * parsed, embedding), resumes the run and enqueues the work. Returns how many were added.
     */
    suspend fun start(): Int

    /** Saves the pause, then stops the work after the chunk in progress (rule N8). */
    suspend fun pause()

    /** Clears a user, no-space or model-missing pause and enqueues the run's unfinished documents. */
    suspend fun resume()

    /** A retryable failed document goes back to pending, joins the run and is enqueued. */
    suspend fun retry(documentId: Long)

    /** On app start, with the app visible: re-enqueues a run left without work (research R12, rule N14). */
    suspend fun reconcile()
}

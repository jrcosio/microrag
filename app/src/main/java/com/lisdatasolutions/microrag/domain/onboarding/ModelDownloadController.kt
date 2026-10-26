package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.flow.StateFlow

/**
 * Schedules the background model download: a user-initiated data transfer job on API 34+ and a
 * WorkManager worker on API 31–33 (D-028). The UI never touches the scheduler APIs directly.
 */
interface ModelDownloadController {
    val progress: StateFlow<DownloadProgress>

    /**
     * Clears the previous outcomes and schedules the queue. Must be called while the app is visible
     * (user-initiated jobs can only be scheduled from the foreground).
     */
    fun schedule(pendingBytes: Long)

    /** Stops the running download; the partial file is kept for a later resume. */
    fun cancel()

    suspend fun isScheduledOrRunning(): Boolean

    /** Bytes already on disk for [id]'s partial download. */
    fun partialBytes(id: ModelId): Long

    /** Deletes partial downloads of files that are no longer required (F03 FR-023). */
    fun deleteOrphanPartials()
}

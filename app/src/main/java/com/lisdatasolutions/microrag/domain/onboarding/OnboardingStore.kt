package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import kotlinx.coroutines.flow.Flow

/** Persisted onboarding progress and download intent (F03 FR-004, research R6). */
interface OnboardingStore {
    val stage: Flow<OnboardingStage>

    /** Models the user asked to download; kept until they are ready. */
    val downloadRequested: Flow<Set<ModelId>>

    /** Explicit pause by the user (FR-018). */
    val downloadPaused: Flow<Boolean>

    /** The user already confirmed the download size; resuming or retrying does not ask again. */
    val downloadConfirmed: Flow<Boolean>

    suspend fun setStage(stage: OnboardingStage)

    /** Adds [ids] to the requested set, marks the download as confirmed and clears the pause. */
    suspend fun requestDownload(ids: Set<ModelId>)

    suspend fun setPaused(paused: Boolean)

    suspend fun clearRequest(id: ModelId)
}

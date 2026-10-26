package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [OnboardingStore] for JVM tests. */
class FakeOnboardingStore(stage: OnboardingStage = OnboardingStage.NOT_STARTED) : OnboardingStore {
    override val stage = MutableStateFlow(stage)
    override val downloadRequested = MutableStateFlow<Set<ModelId>>(emptySet())
    override val downloadPaused = MutableStateFlow(false)
    override val downloadConfirmed = MutableStateFlow(false)

    override suspend fun setStage(stage: OnboardingStage) {
        this.stage.value = stage
    }

    override suspend fun requestDownload(ids: Set<ModelId>) {
        downloadRequested.update { it + ids }
        downloadConfirmed.value = true
        downloadPaused.value = false
    }

    override suspend fun setPaused(paused: Boolean) {
        downloadPaused.value = paused
    }

    override suspend fun clearRequest(id: ModelId) {
        downloadRequested.update { it - id }
    }
}

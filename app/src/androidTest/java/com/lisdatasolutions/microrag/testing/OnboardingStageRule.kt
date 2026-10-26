package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.rules.ExternalResource
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/**
 * Replaces the persisted onboarding store with an in-memory one at [stage], so instrumented tests
 * never touch the device's real onboarding state. Apply it before the rule that launches the activity.
 */
class OnboardingStageRule(stage: OnboardingStage) : ExternalResource() {

    val store = InMemoryOnboardingStore(stage)
    private val override = module { single<OnboardingStore> { store } }

    override fun before() = loadKoinModules(override)

    override fun after() = unloadKoinModules(override)
}

class InMemoryOnboardingStore(stage: OnboardingStage) : OnboardingStore {
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

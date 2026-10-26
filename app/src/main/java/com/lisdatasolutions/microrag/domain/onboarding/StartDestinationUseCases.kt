package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.model.StartDestination
import kotlinx.coroutines.flow.first

/** Launch destination from the persisted onboarding stage (FR-001, research R10). */
class ResolveStartDestinationUseCase(private val store: OnboardingStore) {
    suspend operator fun invoke(): StartDestination = when (store.stage.first()) {
        OnboardingStage.NOT_STARTED -> StartDestination.WELCOME
        OnboardingStage.STARTED -> StartDestination.SETUP_OVER_WELCOME
        OnboardingStage.COMPLETED -> StartDestination.HOME
    }
}

/** «Comenzar»: the user has seen the welcome screen; later launches open setup directly. */
class StartOnboardingUseCase(private val store: OnboardingStore) {
    suspend operator fun invoke() {
        if (store.stage.first() == OnboardingStage.NOT_STARTED) store.setStage(OnboardingStage.STARTED)
    }
}

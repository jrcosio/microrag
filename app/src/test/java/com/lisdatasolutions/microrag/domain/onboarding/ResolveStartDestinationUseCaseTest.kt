package com.lisdatasolutions.microrag.domain.onboarding

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.model.StartDestination
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResolveStartDestinationUseCaseTest {

    @Test
    fun eachStageOpensItsDestination() = runTest {
        val expected = mapOf(
            OnboardingStage.NOT_STARTED to StartDestination.WELCOME,
            OnboardingStage.STARTED to StartDestination.SETUP_OVER_WELCOME,
            OnboardingStage.COMPLETED to StartDestination.HOME,
        )
        expected.forEach { (stage, destination) ->
            assertThat(ResolveStartDestinationUseCase(FakeOnboardingStore(stage))()).isEqualTo(destination)
        }
    }

    @Test
    fun startOnboarding_onlyMovesForwardFromNotStarted() = runTest {
        val store = FakeOnboardingStore()
        StartOnboardingUseCase(store)()
        assertThat(store.stage.value).isEqualTo(OnboardingStage.STARTED)

        store.setStage(OnboardingStage.COMPLETED)
        StartOnboardingUseCase(store)()
        assertThat(store.stage.value).isEqualTo(OnboardingStage.COMPLETED)
    }
}

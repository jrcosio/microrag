package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.testing.OnboardingStageRule
import com.lisdatasolutions.microrag.testing.ReadyModelsRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** F03 FR-001/FR-003: launch destination per onboarding stage and Back behavior (contracts/onboarding-ui.md). */
@RunWith(Parameterized::class)
class OnboardingNavigationTest(private val stage: OnboardingStage) {

    private val stageRule = OnboardingStageRule(stage)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(stageRule).around(ReadyModelsRule()).around(compose)

    // Labels as the app shows them (Spanish by default, D-029).
    private fun string(id: Int) = compose.activity.getString(id)

    private fun text(id: Int) = compose.onNodeWithText(string(id))

    private fun homeTab() = compose.onNodeWithText(string(TopLevelDestination.HOME.label))

    private fun assertActivityFinished() {
        compose.waitForIdle()
        assertThat(compose.activityRule.scenario.state).isEqualTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun launchDestinationAndBack() {
        when (stage) {
            OnboardingStage.NOT_STARTED -> {
                text(R.string.welcome_title_line1).assertIsDisplayed()
                homeTab().assertDoesNotExist()
                Espresso.pressBackUnconditionally()
                assertActivityFinished()
            }
            OnboardingStage.STARTED -> {
                text(R.string.setup_title).assertIsDisplayed()
                homeTab().assertDoesNotExist()
                Espresso.pressBack()
                text(R.string.welcome_title_line1).assertIsDisplayed()
            }
            OnboardingStage.COMPLETED -> {
                homeTab().assertIsDisplayed()
                Espresso.pressBackUnconditionally()
                assertActivityFinished()
            }
        }
    }

    @Test
    fun startButtonOpensSetupAndRecordsTheStage() {
        if (stage != OnboardingStage.NOT_STARTED) return
        text(R.string.welcome_cta).performScrollTo().performClick()

        text(R.string.setup_title).assertIsDisplayed()
        compose.waitForIdle()
        assertThat(stageRule.store.stage.value).isEqualTo(OnboardingStage.STARTED)
        Espresso.pressBack()
        text(R.string.welcome_title_line1).assertIsDisplayed()
    }

    /** T062: with both models ready, «Continuar» completes onboarding and Home becomes the root. */
    @Test
    fun continueOpensHomeAndBackLeavesTheApp() {
        if (stage != OnboardingStage.STARTED) return
        text(R.string.setup_continue).performClick()

        compose.waitUntil(5_000) { stageRule.store.stage.value == OnboardingStage.COMPLETED }
        homeTab().assertIsDisplayed()
        Espresso.pressBackUnconditionally()
        assertActivityFinished()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stages() = OnboardingStage.entries.toList()
    }
}

package com.lisdatasolutions.microrag.core.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.testing.OnboardingStageRule
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
    val rules: TestRule = RuleChain.outerRule(stageRule).around(compose)

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int) = compose.onNodeWithText(context.getString(id))

    private fun homeTab() = compose.onNodeWithText(context.getString(TopLevelDestination.HOME.label))

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

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stages() = OnboardingStage.entries.toList()
    }
}

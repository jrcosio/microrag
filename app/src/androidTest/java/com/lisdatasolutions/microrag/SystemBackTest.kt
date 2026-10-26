package com.lisdatasolutions.microrag

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.navigation.TopLevelDestination
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.testing.OnboardingStageRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation contract N2/N3 with the real system Back key (contracts/navigation.md). */
@RunWith(AndroidJUnit4::class)
class SystemBackTest {

    // Since F03 the app opens Home only once onboarding is completed.
    @get:Rule(order = 0)
    val stage = OnboardingStageRule(OnboardingStage.COMPLETED)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun systemBackFromTabReturnsHome_andBackFromHomeExits() {
        compose.onNodeWithText(context.getString(TopLevelDestination.SETTINGS.label)).performClick()
        compose.onNodeWithText(context.getString(R.string.pending_destination_title, "F12")).assertIsDisplayed()

        Espresso.pressBack()
        compose.onNodeWithText(context.getString(R.string.pending_destination_title, "F09")).assertIsDisplayed()

        Espresso.pressBackUnconditionally()
        compose.waitForIdle()
        assertThat(compose.activityRule.scenario.state).isEqualTo(Lifecycle.State.DESTROYED)
    }
}

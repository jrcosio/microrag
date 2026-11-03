package com.lisdatasolutions.microrag.core.theme

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.settings.AppThemeController
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import com.lisdatasolutions.microrag.testing.OnboardingStageRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * F16 T030 (FR-014, research R5): the system recreates the activity in the new theme, and the open tab and
 * an open sheet are still there. It runs on Ajustes, which shows no user data, so it is safe on the
 * reference phone; the chat's draft lives in its SavedStateHandle and is covered on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class ThemeChangeKeepsStateTest {

    @get:Rule(order = 0)
    val stage = OnboardingStageRule(OnboardingStage.COMPLETED)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val controller: AppThemeController get() = GlobalContext.get().get()

    /** Back to the device's mode; the recreation, if any, finishes before the next test (L6). */
    @After
    fun restore() {
        controller.apply(ThemePreference.SYSTEM)
        SystemClock.sleep(SETTLE_MS)
        compose.waitForIdle()
    }

    @Test
    fun aThemeChange_keepsTheTab_andAnOpenSheet() {
        val settings = compose.activity.getString(R.string.nav_settings)
        compose.onNode(hasText(settings) and hasClickAction()).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_model_embeddings), useUnmergedTree = true).performClick()
        val file = compose.activity.getString(R.string.settings_sheet_file)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText(file)).fetchSemanticsNodes().isNotEmpty() }
        val wasNight = isNight()
        val before = compose.activity

        assertThat(controller.apply(if (wasNight) ThemePreference.LIGHT else ThemePreference.DARK)).isTrue()
        compose.waitUntil(TIMEOUT_MS) { compose.activity !== before && isNight() != wasNight }
        compose.waitForIdle()

        compose.onNodeWithText(file).assertExists()
        compose.onNode(hasText(settings) and isSelected()).assertExists()
    }

    private fun isNight(): Boolean =
        compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 2_000L
    }
}

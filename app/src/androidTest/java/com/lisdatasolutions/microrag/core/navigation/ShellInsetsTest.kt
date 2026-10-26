package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataSpacing
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.testing.OnboardingStageRule
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shell pads the navigation bar for the screens it hosts, so a screen's own bottom bar must not pad it
 * again (found on the reference phone during F06 T072: 48 dp of extra space under «Iniciar indexación»).
 */
@RunWith(AndroidJUnit4::class)
class ShellInsetsTest {

    @get:Rule(order = 0)
    val stage = OnboardingStageRule(OnboardingStage.COMPLETED)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aScreenBottomBarPadsTheNavigationBarOnce() {
        compose.onNodeWithText(compose.activity.getString(R.string.ingest_provisional_entry)).performClick()
        compose.waitForIdle()

        val button = compose.onNodeWithText(compose.activity.getString(R.string.ingest_start)).fetchSemanticsNode().boundsInWindow
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val navigationBar = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val margin = with(compose.density) { LisdataSpacing().space3.toPx() }

        val gap = root.bottom - button.bottom
        assertWithMessage("gap=$gap navigationBar=$navigationBar margin=$margin")
            .that(abs(gap - (navigationBar + margin)))
            .isAtMost(1.5f)
    }
}

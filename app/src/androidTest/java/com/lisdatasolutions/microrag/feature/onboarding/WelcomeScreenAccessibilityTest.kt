package com.lisdatasolutions.microrag.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.feature.onboarding.welcome.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** T021: P01 passes ATF in light and dark, keeps 48 dp targets and fits compact screens at 200 % font. */
@RunWith(Parameterized::class)
class WelcomeScreenAccessibilityTest(private val dark: Boolean) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    @Test
    fun passesChecks_onlyTheLogoIsAnnounced() {
        compose.enableAccessibilityChecks()
        compose.setContent { LisdataTheme(darkTheme = dark) { WelcomeScreen(onStart = {}) } }

        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithContentDescription(string(R.string.cd_logo)).assertIsDisplayed()
        val described = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes()
        assertWithMessage("only the logo carries a description; the illustration is decorative").that(described).hasSize(1)

        val minTouch = with(compose.density) { 48.dp.toPx() } - 0.5f
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            assertThat(node.touchBoundsInRoot.height).isAtLeast(minTouch)
        }
    }

    @Test
    fun compactScreenAtDoubleFont_keepsTheCtaReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                LisdataTheme(darkTheme = dark) {
                    Box(Modifier.size(360.dp, 640.dp)) { WelcomeScreen(onStart = {}) }
                }
            }
        }

        compose.onNodeWithText(string(R.string.welcome_cta)).performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(string(R.string.welcome_footer)).performScrollTo().assertIsDisplayed()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dark={0}")
        fun parameters() = listOf(false, true)
    }
}

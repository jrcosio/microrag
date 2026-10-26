package com.lisdatasolutions.microrag.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupActions
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupPreviewData
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupScreen
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** T033: P02 in every row state and step, light and dark: ATF, 48 dp, verbal rows and the «Continuar» reason. */
@RunWith(Parameterized::class)
class SetupScreenAccessibilityTest(
    private val name: String,
    private val state: SetupUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun passesChecksAndAnnouncesEveryRow() {
        compose.enableAccessibilityChecks()
        compose.setContent { LisdataTheme(darkTheme = dark) { SetupScreen(state, needsNotificationPermission = true, actions = SetupActions()) } }

        compose.onRoot().tryPerformAccessibilityChecks()
        checkTouchTargets()

        // Each model row is one node: name, purpose and verbal status, read politely when it changes.
        val rows = compose.onAllNodes(SemanticsMatcher("live region") { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite })
            .fetchSemanticsNodes()
        assertWithMessage("$name rows").that(rows).hasSize(2)
        rows.forEach { node ->
            val description = node.config[SemanticsProperties.ContentDescription].joinToString()
            assertThat(description.split(", ").size).isAtLeast(3)
        }

        val continueText = compose.activity.getString(R.string.setup_continue)
        val button = compose.onNode(hasText(continueText) and hasClickAction()).fetchSemanticsNode()
        if (!state.continueEnabled) {
            assertWithMessage("$name: disabled «Continuar» explains why")
                .that(button.config.getOrNull(SemanticsProperties.StateDescription)).isNotEmpty()
        }
    }

    private fun checkTouchTargets() {
        val minTouch = with(compose.density) { 48.dp.toPx() } - 0.5f
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f && it.size.height - it.boundsInRoot.height <= 1f }
            .forEach { node ->
                val bounds = node.touchBoundsInRoot
                assertWithMessage("$name: touch target ${bounds.width}x${bounds.height}px for ${node.config}")
                    .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
            }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={2}")
        fun parameters(): List<Array<Any>> = SetupPreviewData.all.flatMap { (name, state) ->
            listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true))
        }
    }
}

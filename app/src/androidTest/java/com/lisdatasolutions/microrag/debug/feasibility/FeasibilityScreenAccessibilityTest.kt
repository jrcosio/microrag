package com.lisdatasolutions.microrag.debug.feasibility

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** T062: the diagnostics screen passes ATF checks and keeps 48 dp touch targets in every state. */
@RunWith(Parameterized::class)
class FeasibilityScreenAccessibilityTest(
    private val name: String,
    private val state: FeasibilityUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun state_passesAccessibilityChecks() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            LisdataTheme(darkTheme = dark) { FeasibilityContent(state, FeasibilityActions()) }
        }
        val list = compose.onNode(hasScrollAction())
        val items = list.fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount
        // The list only composes what is visible: walk it item by item and check each viewport.
        (0 until items).forEach { index ->
            list.performScrollToIndex(index)
            compose.onRoot().tryPerformAccessibilityChecks()
            checkTouchTargets()
        }
    }

    private fun checkTouchTargets() {
        val minTouch = with(compose.density) { 48.dp.toPx() } - TOLERANCE_PX
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            // Nodes clipped by the list viewport report clipped bounds; they are checked once fully in view.
            .filter { it.boundsInRoot.height > 0f && it.size.height - it.boundsInRoot.height <= 1f }
            .forEach { node ->
                val bounds = node.touchBoundsInRoot
                assertWithMessage("$name: touch target ${bounds.width}x${bounds.height}px < 48dp for ${node.config}")
                    .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
            }
    }

    companion object {
        private const val TOLERANCE_PX = 0.5f

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={2}")
        fun parameters(): List<Array<Any>> = listOf(
            "missing" to FeasibilityPreviewData.missing,
            "verifying" to FeasibilityPreviewData.verifying,
            "running" to FeasibilityPreviewData.running,
            "results" to FeasibilityPreviewData.results,
            "error" to FeasibilityPreviewData.error,
        ).flatMap { (name, state) -> listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true)) }
    }
}

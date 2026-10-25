package com.lisdatasolutions.microrag

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.debug.catalog.CatalogEntry
import com.lisdatasolutions.microrag.debug.catalog.catalogEntries
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * SC-006 / FR-014 / FR-015: every catalog entry, in light and dark theme, passes the Accessibility
 * Test Framework checks (touch target, labels, contrast) and keeps 48 dp touch targets.
 */
@RunWith(Parameterized::class)
class CatalogAccessibilityTest(
    private val entry: CatalogEntry,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun entry_passesAccessibilityChecks() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            LisdataTheme(darkTheme = dark) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LisdataTheme.colors.background)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    entry.content()
                }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()

        val minTouch = with(compose.density) { 48.dp.toPx() } - TOLERANCE_PX
        val clickable = compose.onAllNodes(hasClickAction())
        repeat(clickable.fetchSemanticsNodes().size) { index ->
            // Bring the node into view: touch bounds are clipped for nodes scrolled out of the viewport.
            val node = clickable[index].performScrollTo().fetchSemanticsNode()
            val bounds = node.touchBoundsInRoot
            assertWithMessage("${entry.id}: touch target ${bounds.width}x${bounds.height}px < 48dp for ${node.config}")
                .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
        }

        val stateful = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) or
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
        ).fetchSemanticsNodes(atLeastOneRootRequired = false)
        stateful.forEach { node ->
            val config = node.config
            val labelled = SemanticsProperties.ContentDescription in config ||
                SemanticsProperties.Text in config ||
                node.children.any { SemanticsProperties.Text in it.config }
            assertWithMessage("${entry.id}: stateful control without label").that(labelled).isTrue()
        }
    }

    companion object {
        private const val TOLERANCE_PX = 0.5f

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={1}")
        fun parameters(): List<Array<Any>> =
            catalogEntries.flatMap { entry -> listOf(arrayOf<Any>(entry, false), arrayOf<Any>(entry, true)) }
    }
}

package com.lisdatasolutions.microrag.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** F08 T045 (SC-008, A1, A4, A6): P06 in every state, light and dark, also with the font at 200 %. */
@RunWith(Parameterized::class)
class LibraryScreenAccessibilityTest(
    private val name: String,
    private val state: LibraryUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme(darkTheme = dark) { LibraryScreen(state, LibraryActions()) }
            }
        }
    }

    @Test
    fun passesChecks_andReadsEachRowAsOneSentence() {
        compose.enableAccessibilityChecks()
        show()

        compose.onAllNodes(isRoot()).let { roots -> repeat(roots.fetchSemanticsNodes().size) { roots[it].tryPerformAccessibilityChecks() } }
        checkTouchTargets()
        checkRows()
    }

    @Test
    fun fitsTheWidthWithTheFontAt200Percent() {
        show(fontScale = 2f)

        checkTouchTargets()
        val width = compose.onRoot().fetchSemanticsNode().size.width
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
        checkRows()
    }

    /** A1: every row shown is one sentence that starts with its name; its switch says what it does. */
    private fun checkRows() {
        val documents = (state.content as? LibraryContent.Documents)?.documents ?: return
        val shown = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }
        val rows = documents.filter { document -> shown.any { it.startsWith("${document.displayName}, ") } }
        assertWithMessage("$name: rows read as sentences").that(rows).isNotEmpty()
        val label = compose.activity.getString(R.string.library_include_switch)
        compose.onAllNodes(isToggleable()).fetchSemanticsNodes().forEach { switch ->
            assertWithMessage("$name: switch label").that(switch.config.getOrNull(SemanticsProperties.ContentDescription)).containsExactly(label)
        }
    }

    /** A4: 48 dp, also the filter chips that draw at about 27 dp. */
    private fun checkTouchTargets() {
        val minTouch = px(48) - 0.5f
        compose.onAllNodes(hasClickAction().or(isToggleable())).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f && it.size.height - it.boundsInRoot.height <= 1f }
            .forEach { node ->
                val bounds = node.touchBoundsInRoot
                assertWithMessage("$name: touch target ${bounds.width}x${bounds.height}px for ${node.config}")
                    .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
            }
    }

    private fun px(dp: Int): Float = with(compose.density) { dp.dp.toPx() }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={2}")
        fun parameters(): List<Array<Any>> = LibraryPreviewData.all.flatMap { (name, state) ->
            listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true))
        }
    }
}

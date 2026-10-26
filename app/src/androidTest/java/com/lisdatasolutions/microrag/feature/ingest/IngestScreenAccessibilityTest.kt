package com.lisdatasolutions.microrag.feature.ingest

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/** F06 T061 (SC-008, U1–U4): P05 in every state, light and dark, also with the font at 200 %. */
@RunWith(Parameterized::class)
class IngestScreenAccessibilityTest(
    private val name: String,
    private val state: IngestUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun passesChecksAndDescribesEveryRow() {
        compose.enableAccessibilityChecks()
        compose.setContent { LisdataTheme(darkTheme = dark) { IngestScreen(state, IngestActions()) } }

        // The sheet and the dialog open their own windows: every root is checked.
        val roots = compose.onAllNodes(isRoot())
        repeat(roots.fetchSemanticsNodes().size) { roots[it].tryPerformAccessibilityChecks() }
        checkTouchTargets()
        checkRows()
    }

    @Test
    fun keepsEveryRowAndTheButtonWithTheFontAt200Percent() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                LisdataTheme(darkTheme = dark) { IngestScreen(state, IngestActions()) }
            }
        }
        checkRows()
        val start = compose.activity.getString(R.string.ingest_start)
        val button = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == start } == true }
        assertWithMessage("$name: «Iniciar indexación» at 200 %").that(button).isNotNull()
        assertWithMessage("$name: button height").that(button!!.size.height.toFloat()).isAtLeast(with(compose.density) { 48.dp.toPx() } - 0.5f)
    }

    /** U1: one node per row, its whole description and the «Ver detalle» action. */
    private fun checkRows() {
        val label = compose.activity.getString(R.string.ingest_row_action)
        val rows = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
        assertWithMessage("$name: rows").that(rows.size).isEqualTo(state.items.size)
        rows.zip(state.items).forEach { (node, item) ->
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            assertWithMessage("$name: row ${item.displayName}").that(description).startsWith(item.displayName)
            assertWithMessage("$name: row ${item.displayName} has its state").that(description.length).isGreaterThan(item.displayName.length + 2)
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
        fun parameters(): List<Array<Any>> = IngestPreviewData.all.flatMap { (name, state) ->
            listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true))
        }
    }
}

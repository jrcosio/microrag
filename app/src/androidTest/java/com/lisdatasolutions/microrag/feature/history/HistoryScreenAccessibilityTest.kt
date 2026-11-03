package com.lisdatasolutions.microrag.feature.history

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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

/** F13 T035 (U2–U10): Historial and its dialog, light and dark, at 200 % and in landscape, with ATF. */
@RunWith(Parameterized::class)
class HistoryScreenAccessibilityTest(
    private val name: String,
    private val state: HistoryUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme(darkTheme = dark) { HistoryScreen(state, HistoryActions()) }
            }
        }
    }

    private fun checkAll() {
        compose.onAllNodes(isRoot()).let { roots -> repeat(roots.fetchSemanticsNodes().size) { roots[it].tryPerformAccessibilityChecks() } }
        checkTouchTargets()
        checkSentences()
    }

    @Test
    fun passesChecks_andReadsAsSentences() {
        compose.enableAccessibilityChecks()
        show()

        checkAll()
    }

    /** The activity turns before anything is drawn, and is back upright before the next test starts. */
    @Test
    fun passesChecksInLandscape() {
        turn(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        try {
            compose.enableAccessibilityChecks()
            show()

            val root = screenRoot().size
            assertWithMessage("$name: landscape").that(root.width).isGreaterThan(root.height)
            checkAll()
        } finally {
            turn(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        }
    }

    @Test
    fun passesChecks_andFitsTheWidth_withTheFontAt200Percent() {
        compose.enableAccessibilityChecks()
        show(fontScale = 2f)

        checkAll()
        val width = screenRoot().size.width
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
    }

    private fun turn(requested: Int, expected: Int) {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = requested }
        compose.waitUntil(TURN_TIMEOUT_MS) {
            var orientation = Configuration.ORIENTATION_UNDEFINED
            compose.activityRule.scenario.onActivity { orientation = it.resources.configuration.orientation }
            orientation == expected
        }
        compose.waitForIdle()
    }

    /** U3: every row shown is one sentence that opens, and its bin is a node of its own. */
    private fun checkSentences() {
        if (state.content !is HistoryScreenContent.Sections || state.confirmDeleteId != null) return
        val open = compose.activity.getString(R.string.history_open_action)
        val rows = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsActions.OnClick)?.label == open }
        assertWithMessage("$name: rows").that(rows).isNotEmpty()
        rows.forEach { row ->
            assertWithMessage("$name: a row is read as one sentence")
                .that(row.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).hasSize(1)
        }
        val bins = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(binPrefix()) } }
        assertWithMessage("$name: one bin per row").that(bins).hasSize(rows.size)
    }

    private fun binPrefix(): String = compose.activity.getString(R.string.history_delete_label, "").trimEnd()

    /** U4: 48 dp. */
    private fun checkTouchTargets() {
        val minTouch = px(48) - 0.5f
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f && it.size.height - it.boundsInRoot.height <= 1f }
            .forEach { node ->
                val bounds = node.touchBoundsInRoot
                assertWithMessage("$name: touch target ${bounds.width}x${bounds.height}px for ${node.config}")
                    .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
            }
    }

    /** The activity's root: a dialog adds a smaller root of its own window. */
    private fun screenRoot() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxBy { it.size.width * it.size.height }

    private fun px(dp: Int): Float = with(compose.density) { dp.dp.toPx() }

    companion object {
        private const val TURN_TIMEOUT_MS = 5_000L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={2}")
        fun parameters(): List<Array<Any>> = HistoryPreviewData.all.flatMap { (name, state) ->
            listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true))
        }
    }
}

package com.lisdatasolutions.microrag.feature.home

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.feature.library.grouped
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** F09 T026 (SC-008, A1–A8): P03 in every state, light and dark, at 200 % and in landscape. */
@RunWith(Parameterized::class)
class HomeScreenAccessibilityTest(
    private val name: String,
    private val state: HomeUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme(darkTheme = dark) { HomeScreen(state, HomeActions()) }
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

    @Test
    fun passesChecksInLandscape() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.enableAccessibilityChecks()
        show()

        val root = compose.onRoot().fetchSemanticsNode().size
        assertWithMessage("$name: landscape").that(root.width).isGreaterThan(root.height)
        checkAll()
    }

    @Test
    fun fitsTheWidthWithTheFontAt200Percent() {
        show(fontScale = 2f)

        checkTouchTargets()
        val width = compose.onRoot().fetchSemanticsNode().size.width
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
    }

    /** A2–A4: the figures, every quick action and the indexing card are read as whole sentences. */
    private fun checkSentences() {
        val clickable = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val texts = clickable.map { it.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(", ") }
        listOf(R.string.home_import to R.string.home_import_subtitle, R.string.home_languages to R.string.home_languages_subtitle).forEach { (title, subtitle) ->
            val sentence = "${compose.activity.getString(title)}, ${compose.activity.getString(subtitle)}"
            assertWithMessage("$name: quick action read as «$sentence»").that(texts).contains(sentence)
        }
        val summary = state.summary as? SummaryState.Loaded ?: return
        val descriptions = compose.onAllNodes(SemanticsMatcher("any") { true }).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        assertWithMessage("$name: summary sentence").that(
            descriptions.any { it.startsWith(summary.documents.grouped()) && it.contains(summary.chunks.grouped()) },
        ).isTrue()
    }

    /** A6: 48 dp. */
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

    private fun px(dp: Int): Float = with(compose.density) { dp.dp.toPx() }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={2}")
        fun parameters(): List<Array<Any>> = HomePreviewData.all.flatMap { (name, state) ->
            listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true))
        }
    }
}

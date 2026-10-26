package com.lisdatasolutions.microrag.feature.indexing

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
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
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** F07 T056 (SC-008, U1–U6): P08 in every state, light and dark, also with the font at 200 %. */
@RunWith(Parameterized::class)
class IndexScreenAccessibilityTest(
    private val name: String,
    private val state: IndexUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme(darkTheme = dark) { IndexScreen(state, IndexActions()) }
            }
        }
    }

    @Test
    fun passesChecksAndDescribesTheRunAndTheFailures() {
        compose.enableAccessibilityChecks()
        show()

        compose.onAllNodes(isRoot()).let { roots -> repeat(roots.fetchSemanticsNodes().size) { roots[it].tryPerformAccessibilityChecks() } }
        checkTouchTargets()
        checkHero()
        checkFailedRows()
    }

    @Test
    fun keepsTheRingTheRowsAndTheActionWithTheFontAt200Percent() {
        show(fontScale = 2f)

        checkHero()?.let { hero ->
            // U6: the ring keeps at least its size; it grows rather than clip the percentage.
            assertWithMessage("$name: ring height").that(hero.size.height.toFloat()).isAtLeast(px(180))
        }
        checkFailedRows()
        val width = compose.onRoot().fetchSemanticsNode().size.width
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
        val action = actionNode() ?: return
        assertWithMessage("$name: action height").that(action.size.height.toFloat()).isAtLeast(px(48) - 0.5f)
        val content = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes().single()
        assertWithMessage("$name: the fixed action does not cover the content")
            .that(content.boundsInRoot.bottom).isAtMost(action.boundsInRoot.top + 1f)
    }

    /** U1: the ring, its percentage and the chunks as one sentence, or «Preparando documentos». */
    private fun checkHero(): SemanticsNode? {
        val active = state.indexing as? IndexingState.Active ?: return null
        if (active.pause == PauseReason.MODEL_MISSING) return null
        val ring = active.progress.ring()
        val total = active.progress.total
        val expected = if (ring is ProgressValue.Determinate && total != null) {
            compose.activity.getString(R.string.index_ring_description, ring.percent, active.progress.done.grouped(), total.grouped())
        } else {
            compose.activity.getString(R.string.index_preparing)
        }
        val hero = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(expected)))
            .fetchSemanticsNodes()
        assertWithMessage("$name: hero «$expected»").that(hero).hasSize(1)
        return hero.single()
    }

    /** P6: every failed document is one sentence with its cause; only the retryable ones offer «Reintentar». */
    private fun checkFailedRows() {
        val failed = when (val indexing = state.indexing) {
            is IndexingState.Active -> indexing.failed
            is IndexingState.Idle -> indexing.failed
            else -> emptyList()
        }
        failed.forEach { document ->
            val rows = compose.onAllNodes(SemanticsMatcher("row of ${document.displayName}") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("${document.displayName}, ") } == true
            }).fetchSemanticsNodes()
            assertWithMessage("$name: row ${document.displayName}").that(rows).hasSize(1)
        }
        val retry = compose.activity.getString(R.string.index_retry)
        val retryRows = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsActions.OnClick)?.label == retry }
        assertWithMessage("$name: rows offering «Reintentar»").that(retryRows.size).isEqualTo(failed.count { it.canRetry })
    }

    private fun actionNode(): SemanticsNode? {
        val label = when (state.action) {
            IndexAction.Pause -> R.string.index_pause
            IndexAction.Resume -> R.string.index_resume
            is IndexAction.Retry -> R.string.index_retry
            null -> return null
        }.let(compose.activity::getString)
        val button = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true }
        assertWithMessage("$name: «$label»").that(button).isNotNull()
        return button
    }

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
        fun parameters(): List<Array<Any>> = IndexPreviewData.all.flatMap { (name, state) ->
            listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true))
        }
    }
}

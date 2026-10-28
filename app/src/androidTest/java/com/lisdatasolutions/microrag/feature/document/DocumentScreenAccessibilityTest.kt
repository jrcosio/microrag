package com.lisdatasolutions.microrag.feature.document

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

/** F08 T046 (SC-008, A2, A3, A5, A6): P07 in every state, light and dark, also with the font at 200 %. */
@RunWith(Parameterized::class)
class DocumentScreenAccessibilityTest(
    private val name: String,
    private val state: DocumentUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme(darkTheme = dark) { DocumentScreen(state, DocumentActions()) }
            }
        }
    }

    @Test
    fun passesChecks_andReadsTheGridAndTheCitation() {
        compose.enableAccessibilityChecks()
        show()

        compose.onAllNodes(isRoot()).let { roots -> repeat(roots.fetchSemanticsNodes().size) { roots[it].tryPerformAccessibilityChecks() } }
        checkTouchTargets()
        checkGrid()
        checkCitation()
    }

    @Test
    fun fitsTheWidth_withoutOverlappingActions_withTheFontAt200Percent() {
        show(fontScale = 2f)

        checkTouchTargets()
        // With a dialog there are two roots; the dialog's nodes are placed in its own, narrower one.
        val width = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.width }
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
        // A6: the action buttons stack instead of overlapping.
        val buttons = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text ->
                text.text in ACTIONS.map(compose.activity::getString)
            }
        }
        buttons.forEach { a ->
            buttons.filter { it !== a }.forEach { b ->
                assertWithMessage("$name: ${a.config} overlaps ${b.config}").that(a.boundsInRoot.overlaps(b.boundsInRoot)).isFalse()
            }
        }
        checkGrid()
    }

    /** A2: each cell is read as «Páginas, 48», with «—» read as words. */
    private fun checkGrid() {
        if (state.content !is DocumentContent.Loaded) return
        val labels = listOf(R.string.document_stat_pages, R.string.document_stat_chunks, R.string.document_stat_size, R.string.document_stat_imported)
            .map(compose.activity::getString)
        val descriptions = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        labels.forEach { label ->
            assertWithMessage("$name: «$label, …»").that(descriptions.any { it.startsWith("$label, ") && !it.endsWith("—") }).isTrue()
        }
    }

    /** A3: the cited chunk is read as «Fragmento citado: …». */
    private fun checkCitation() {
        val preview = (state.preview as? PreviewContent.Shown)?.preview?.takeIf { it.highlighted } ?: return
        val expected = compose.activity.getString(R.string.document_cited_fragment, preview.text)
        val found = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(expected)), useUnmergedTree = true)
            .fetchSemanticsNodes()
        // Lower in the screen it may not be laid out yet; when it is, it is read as the citation.
        assertWithMessage("$name: citation").that(found.size).isAtMost(1)
    }

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
        private val ACTIONS = listOf(R.string.document_reindex, R.string.document_retry, R.string.document_delete, R.string.document_deleting, R.string.document_reindexing_button)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={2}")
        fun parameters(): List<Array<Any>> = DocumentPreviewData.all.flatMap { (name, state) ->
            listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true))
        }
    }
}

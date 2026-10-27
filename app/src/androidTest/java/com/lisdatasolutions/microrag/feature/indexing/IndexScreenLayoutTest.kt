package com.lisdatasolutions.microrag.feature.indexing

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * D-042: as in PNG 08, «Procesando ahora» shows without scrolling on the reference phone, with its own
 * system bars. Only there: Robolectric's text metrics do not match the real font, and other screens differ.
 */
class IndexScreenLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: IndexUiState) {
        assumeTrue("measured on the reference phone (D-022)", Build.MODEL == "A063")
        compose.setContent { LisdataTheme { IndexScreen(state, IndexActions()) } }
    }

    private fun viewportBottom() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNodes().single().boundsInRoot.bottom

    /** Unclipped, unlike boundsInRoot: a text below the fold keeps its real place. */
    private fun bounds(text: String) = compose.onNodeWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNode()
        .let { Rect(it.positionInRoot, it.size.toSize()) }

    @Test
    fun asInPng08_theWholeCardShows() {
        show(IndexPreviewData.png)

        assertWithMessage("bottom of the document name").that(bounds("Manual de procesos.pdf").bottom).isAtMost(viewportBottom())
    }

    @Test
    fun withTheTimeLeftAndMoreDocumentsQueued_theDocumentNameStillShows() {
        show(IndexPreviewData.running)

        assertWithMessage("bottom of «Procesando ahora»")
            .that(bounds(compose.activity.getString(R.string.index_processing_now)).bottom).isAtMost(viewportBottom())
        val firstLine = with(compose.density) { 20.dp.toPx() }
        assertWithMessage("first line of the document name").that(bounds("Manual de procesos.pdf").top + firstLine).isAtMost(viewportBottom())
    }
}

package com.lisdatasolutions.microrag.feature.document

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.toSize
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * F08 closing: as in PNG 07, the actions show without scrolling on the reference phone, above its own
 * navigation bar, also with a first chunk as long as the real ones. Only there, as IndexScreenLayoutTest.
 */
class DocumentScreenLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The screen behind the navigation bar does not count: the scroll runs under it (edge to edge). */
    private fun visibleBottom(): Float {
        val navigation = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        return compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - navigation
    }

    /** Unclipped, unlike boundsInRoot: a text below the fold keeps its real place. */
    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        .let { Rect(it.positionInRoot, it.size.toSize()) }

    @Test
    fun withALongFirstChunk_theActionsShow() {
        assumeTrue("measured on the reference phone (D-022)", Build.MODEL == "A063")
        compose.setContent { LisdataTheme { DocumentScreen(DocumentPreviewData.longFirstChunk, DocumentActions()) } }

        assertWithMessage("bottom of «Eliminar»")
            .that(bounds(compose.activity.getString(R.string.document_delete)).bottom).isAtMost(visibleBottom())
    }
}

package com.lisdatasolutions.microrag.feature.folders

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
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
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeDocumentRow
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeFolderRow
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeNote
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSelectorUiState
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSheetActions
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSheetContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * F17 T046 (K4, K5, K6, K8; SC-008): the folder dialogs, the folder sheet and the «Buscar en» sheet, light
 * and dark and at 200 %. The sheets are drawn without their window, as their previews.
 */
@RunWith(Parameterized::class)
class FolderSheetsAccessibilityTest(
    private val name: String,
    private val content: @Composable () -> Unit,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme(darkTheme = dark) { content() }
            }
        }
    }

    @Test
    fun passesChecks() {
        compose.enableAccessibilityChecks()
        show()

        compose.onAllNodes(isRoot()).let { roots -> repeat(roots.fetchSemanticsNodes().size) { roots[it].tryPerformAccessibilityChecks() } }
        checkTouchTargets()
    }

    @Test
    fun fitsTheWidthWithTheFontAt200Percent() {
        show(fontScale = 2f)

        checkTouchTargets()
        val width = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.width }
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
    }

    /** K13: 48 dp for rows, checkboxes, the expand buttons and the chip. */
    private fun checkTouchTargets() {
        val minTouch = with(compose.density) { 48.dp.toPx() } - 0.5f
        compose.onAllNodes(hasClickAction().or(isToggleable()), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f && it.size.height - it.boundsInRoot.height <= 1f }
            .forEach { node ->
                val bounds = node.touchBoundsInRoot
                assertWithMessage("$name: touch target ${bounds.width}x${bounds.height}px for ${node.config}")
                    .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
            }
    }

    companion object {
        private val folders = FolderList(listOf(Folder(1, "Legal", 1, 12), Folder(2, "Motores", 2, 5)), unfiledCount = 3)

        private val selector = ScopeSelectorUiState(
            folders = listOf(
                ScopeFolderRow(1, "Legal", 12, ToggleableState.On, selectable = true, expanded = false, documents = null, hasMore = false),
                ScopeFolderRow(
                    2, "Motores", 5, ToggleableState.Indeterminate, selectable = true, expanded = true,
                    documents = listOf(
                        ScopeDocumentRow(DocumentRef(21, 1), "Manual_motor_V8.pdf", checked = true, selectable = true, note = null),
                        ScopeDocumentRow(DocumentRef(22, 2), "Ficha técnica.pdf", checked = false, selectable = true, note = ScopeNote.PENDING),
                        ScopeDocumentRow(DocumentRef(23, 3), "Notas.txt", checked = false, selectable = false, note = ScopeNote.NOT_INCLUDED),
                    ),
                    hasMore = false,
                ),
                ScopeFolderRow(4, "Archivo", 1, ToggleableState.Off, selectable = false, expanded = false, documents = null, hasMore = false),
                ScopeFolderRow(null, null, 3, ToggleableState.Off, selectable = true, expanded = false, documents = null, hasMore = false),
            ),
            whole = false,
            canApply = true,
        )

        private val samples: List<Pair<String, @Composable () -> Unit>> = listOf(
            "delete" to { FolderDialogs(FolderPreviewData.delete, busy = false, FolderDialogActions()) },
            "confirmDocuments" to { FolderDialogs(FolderPreviewData.confirmDocuments, busy = true, FolderDialogActions()) },
            "moveSheet" to { FolderChoiceContent(folders, selected = 2, title = "Mover a carpeta", actionText = "Mover", actions = FolderChoiceActions()) },
            "destinationSheet" to { FolderChoiceContent(folders, selected = null, title = "Carpeta de destino", actionText = "Elegir", actions = FolderChoiceActions()) },
            "scopeSheet" to { ScopeSheetContent(selector, ScopeSheetActions()) },
            "scopeSheetWhole" to { ScopeSheetContent(selector.copy(whole = true, canApply = true), ScopeSheetActions()) },
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={2}")
        fun parameters(): List<Array<Any>> = samples.flatMap { (name, content) ->
            listOf(arrayOf<Any>(name, content, false), arrayOf<Any>(name, content, true))
        }
    }
}

package com.lisdatasolutions.microrag.feature.chat.scope

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F17 T035 (K8, wireframe §6 and §8): the «Buscar en» sheet. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class ScopeSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = ScopeSheetActions(
        onWholeLibrary = { calls += "whole" },
        onToggleFolder = { calls += "folder $it" },
        onToggleDocument = { folder, document -> calls += "document $folder ${document.id}" },
        onExpand = { calls += "expand $it" },
        onApply = { calls += "apply" },
        onDismiss = { calls += "dismiss" },
    )

    private val state = ScopeSelectorUiState(
        folders = listOf(
            ScopeFolderRow(1, "Legal", 12, ToggleableState.On, selectable = true, expanded = false, documents = null, hasMore = false),
            ScopeFolderRow(
                2, "Motores", 5, ToggleableState.Indeterminate, selectable = true, expanded = true,
                documents = listOf(
                    ScopeDocumentRow(DocumentRef(21, 210), "Manual_motor_V8.pdf", checked = true, selectable = true, note = null),
                    ScopeDocumentRow(DocumentRef(22, 220), "Ficha técnica.pdf", checked = false, selectable = true, note = ScopeNote.PENDING),
                    ScopeDocumentRow(DocumentRef(23, 230), "Notas.txt", checked = false, selectable = false, note = ScopeNote.NOT_INCLUDED),
                ),
                hasMore = false,
            ),
            ScopeFolderRow(null, null, 3, ToggleableState.Off, selectable = true, expanded = false, documents = null, hasMore = false),
        ),
        whole = false,
        canApply = true,
    )

    private fun show(shown: ScopeSelectorUiState = state) {
        compose.setContent { LisdataTheme { ScopeSheetContent(shown, actions) } }
    }

    @Test
    fun theSheetHasItsOptionsAsInTheWireframe() {
        show()

        listOf("Buscar en", "Toda la biblioteca", "CARPETAS", "Legal", "12 documentos", "Motores", "Manual_motor_V8.pdf",
            "Ficha técnica.pdf", "Pendiente de indexar", "Notas.txt", "No incluido en RAG", "General", "Cancelar", "Aplicar")
            .forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
    }

    @Test
    fun theRowsAskForTheirActions() {
        show()

        compose.onNodeWithText("Toda la biblioteca").performClick()
        compose.onNodeWithText("Legal").performClick()
        compose.onNodeWithText("Manual_motor_V8.pdf").performClick()
        // The first folder's button, closed.
        compose.onAllNodes(hasContentDescription("Mostrar documentos") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "plegada"))[0]
            .performClick()
        compose.onNodeWithText("Aplicar").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertThat(calls).containsExactly("whole", "folder 1", "document 2 21", "expand 1", "apply", "dismiss").inOrder()
    }

    /** K13: a partly checked folder says so; an excluded document cannot be checked. */
    @Test
    fun theStatesAreRead() {
        show()

        compose.onNode(hasText("Motores") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "algunos documentos"), useUnmergedTree = false)
            .assertExists()
        compose.onNode(hasText("Notas.txt")).assertIsNotEnabled()
    }

    @Test
    fun applyWaitsForSomethingChecked() {
        show(state.copy(canApply = false))

        compose.onNodeWithText("Aplicar").assertIsNotEnabled()
    }

    @Test
    fun theWholeLibraryIsMarked() {
        show(state.copy(whole = true))

        compose.onNodeWithText("Toda la biblioteca").assertIsSelected()
    }

    @Test
    fun withoutFoldersThereIsNoFoldersHeader() {
        show(state.copy(folders = emptyList(), whole = true))

        compose.onNodeWithText("Toda la biblioteca").assertExists()
        compose.onNodeWithText("CARPETAS", useUnmergedTree = true).assertDoesNotExist()
    }
}

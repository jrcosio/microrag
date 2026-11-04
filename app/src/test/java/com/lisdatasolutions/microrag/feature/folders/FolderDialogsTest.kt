package com.lisdatasolutions.microrag.feature.folders

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.library.FolderDeletion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * F17 T016 (wireframe §3, K4): the delete dialogs. The name dialog (K3) runs on a device, in
 * FolderNameDialogTest: a text field inside an AlertDialog never settles under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class FolderDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = FolderDialogActions(
        onNameChange = { calls += "name $it" },
        onSaveName = { calls += "save" },
        onDeletionChoice = { calls += "choice $it" },
        onConfirmDelete = { calls += "confirm" },
        onConfirmDocuments = { calls += "documents" },
        onDismiss = { calls += "dismiss" },
    )

    private fun show(dialog: FolderDialog, busy: Boolean = false) {
        compose.setContent { LisdataTheme { FolderDialogs(dialog, busy, actions) } }
    }

    @Test
    fun delete_movesByDefault_orDeletesTheDocuments() {
        show(FolderPreviewData.delete)

        compose.onNodeWithText("¿Eliminar la carpeta «Legal»?").assertExists()
        compose.onNodeWithText("Mover sus 12 documentos a «General»").assertIsSelected()
        compose.onNodeWithText("Eliminar también sus 12 documentos").performClick()
        compose.onNodeWithText("Eliminar").performClick()

        assertThat(calls).containsExactly("choice ${FolderDeletion.DELETE_DOCUMENTS}", "confirm").inOrder()
    }

    @Test
    fun anEmptyFolderOnlyAsks() {
        show(FolderPreviewData.delete.copy(documents = 0))

        compose.onNodeWithText("¿Eliminar la carpeta «Legal»?").assertExists()
        compose.onNodeWithText("Mover sus 0 documentos a «General»").assertDoesNotExist()
    }

    @Test
    fun theSecondNotice_explainsItCannotBeUndone_andWaitsWhileDeleting() {
        show(FolderPreviewData.confirmDocuments)

        compose.onNodeWithText("¿Eliminar 12 documentos?").assertExists()
        compose.onNodeWithText("Se eliminarán de este teléfono, con su texto y su índice. No se puede deshacer.").assertExists()
        compose.onNodeWithText("Eliminar documentos").performClick()
        assertThat(calls).containsExactly("documents")
    }

    @Test
    fun whileDeleting_theButtonSaysSoAndWaits() {
        show(FolderPreviewData.confirmDocuments, busy = true)

        compose.onNodeWithText("Eliminando…").assertIsNotEnabled()
    }
}

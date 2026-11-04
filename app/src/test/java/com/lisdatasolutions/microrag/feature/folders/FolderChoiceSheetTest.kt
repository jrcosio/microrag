package com.lisdatasolutions.microrag.feature.folders

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F17 T024 and T025 (K5, K6): the folder sheet of the detail and the Ingest. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class FolderChoiceSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val folders = FolderList(listOf(Folder(1, "Legal", 10, 2), Folder(2, "Motores", 20, 0)), unfiledCount = 3)

    private val actions = FolderChoiceActions(
        onSelect = { calls += "select $it" },
        onConfirm = { calls += "confirm" },
        onNewFolder = { calls += "new" },
        onDismiss = { calls += "dismiss" },
    )

    private fun show(selected: Long?, action: String) {
        compose.setContent { LisdataTheme { FolderChoiceContent(folders, selected, "Mover a carpeta", action, actions) } }
    }

    @Test
    fun theCurrentFolderIsMarked_andEveryOptionIsThere() {
        show(selected = 2, action = "Mover")

        compose.onNodeWithText("Mover a carpeta").assertExists()
        compose.onNodeWithText("Motores").assertIsSelected()
        compose.onNodeWithText("Legal").performClick()
        compose.onNodeWithText("General").performClick()
        compose.onNodeWithText("Nueva carpeta…").performClick()
        compose.onNodeWithText("Mover").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertThat(calls).containsExactly("select 1", "select null", "new", "confirm", "dismiss").inOrder()
    }

    @Test
    fun generalIsMarkedForADocumentWithoutAFolder_andTheIngestSaysChoose() {
        show(selected = null, action = "Elegir")

        compose.onNodeWithText("General").assertIsSelected()
        compose.onNodeWithText("Elegir").assertExists()
    }
}

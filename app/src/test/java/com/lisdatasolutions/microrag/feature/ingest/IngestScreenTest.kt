package com.lisdatasolutions.microrag.feature.ingest

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F06 T062: P05 texts, counts, enabled states, proposals P1, P4 and P5, and the detail sheet. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es")
class IngestScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clicked = mutableListOf<QueueKey>()

    private fun show(state: IngestUiState) {
        compose.setContent {
            LisdataTheme { IngestScreen(state, IngestActions(onRowClick = { clicked += it })) }
        }
    }

    private fun startButton() = compose.onNode(hasText(context.getString(R.string.ingest_start)) and hasClickAction())

    @Test
    fun pngTextsInSpanishWithoutDragAndDrop() {
        show(IngestPreviewData.png)

        listOf("Importar documentos", "INGESTA LOCAL", "Amplía tu biblioteca", "Selecciona archivos de tu dispositivo.",
            "Selecciona archivos", "Todo se procesa en tu móvil", "Seleccionar archivos", "Formatos: PDF · TXT · Markdown",
            "COLA DE IMPORTACIÓN", "3 archivos", "Manual técnico.pdf", "Iniciar indexación")
            .forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        compose.onNodeWithText("Arrastra", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithText("Listo para procesar", substring = true, useUnmergedTree = true).assertCountEquals(3)
        startButton().assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "en")
    fun englishTexts() {
        show(IngestPreviewData.png)

        listOf("Import documents", "Grow your library", "Select files from your device.", "3 files", "Start indexing")
            .forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
    }

    @Test
    fun emptyQueueProposalP1() {
        show(IngestPreviewData.empty)

        compose.onNodeWithText("Aún no hay archivos en la cola").assertExists()
        compose.onNodeWithText("0 archivos", useUnmergedTree = true).assertDoesNotExist()
        startButton().assertIsNotEnabled()
    }

    @Test
    fun errorRowsShowTheirReasonAndNothingReadyDisablesStarting() {
        show(IngestPreviewData.errors.copy(queue = IngestPreviewData.errors.queue.copy(items = IngestPreviewData.errors.items.dropLast(1), readyCount = 0, canStart = false)))

        listOf("Archivo cifrado", "Formato no admitido", "Sin texto", "Ya está en tu biblioteca")
            .forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        startButton().assertIsNotEnabled()
    }

    @Test
    fun withoutSystemPdfProposalP4() {
        show(IngestPreviewData.noPdf)

        compose.onNodeWithText("Formatos: TXT · Markdown · PDF requiere actualizar el sistema").assertExists()
    }

    @Test
    fun unavailableLibraryProposalP5() {
        show(IngestPreviewData.libraryUnavailable)

        compose.onNodeWithText("La biblioteca no está disponible").assertExists()
        compose.onNode(hasText("Seleccionar archivos") and hasClickAction()).assertIsNotEnabled()
        startButton().assertIsNotEnabled()
    }

    @Test
    fun aRowOpensItsDetail() {
        show(IngestPreviewData.png)

        compose.onAllNodes(hasClickAction() and hasContentDescription("Manual técnico.pdf", substring = true))[0]
            .performScrollTo()
            .performClick()

        assertThat(clicked).containsExactly(QueueKey.Document(1))
    }

    @Test
    fun theSheetOffersTheActionsTheRowAllows() {
        show(IngestPreviewData.sheet)

        compose.onNodeWithText("El PDF está protegido con contraseña. Quita la protección e impórtalo de nuevo.").assertExists()
        compose.onNodeWithText("Quitar de la cola").assertExists()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }

    @Test
    fun removingADocumentIsConfirmed() {
        show(IngestPreviewData.confirming)

        compose.onNodeWithText("¿Quitar de la cola?").assertIsDisplayed()
        compose.onNodeWithText("Se borrará la copia de «Notas de reunión.md» que guarda la app.", substring = true).assertExists()
    }

    @Test
    fun thePickerOffersTheMvpTypes() {
        assertThat(IngestMimeTypes.ALL.toList()).containsExactly("application/pdf", "text/plain", "text/markdown", "text/x-markdown")
    }

    // F07 T055: the queue during indexing (index-ui §Ingesta).

    @Test
    fun indexingPausedAndFailedRows() {
        show(IngestPreviewData.indexing)

        compose.onNodeWithText("Indexando · 1.250 de 4.300 fragmentos", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("En pausa · 64 de 430 fragmentos", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("No se pudo indexar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Indexación incompleta", useUnmergedTree = true).assertExists()
        startButton().assertIsEnabled()
    }
}

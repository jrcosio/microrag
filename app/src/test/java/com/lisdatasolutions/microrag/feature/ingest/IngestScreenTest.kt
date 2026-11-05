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
import androidx.compose.ui.test.isSelected
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
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.rag.IndexMode
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

    private val modeCalls = mutableListOf<String>()

    private fun show(state: IngestUiState) {
        compose.setContent {
            LisdataTheme {
                IngestScreen(
                    state,
                    IngestActions(
                        onRowClick = { clicked += it },
                        onChooseMode = { modeCalls += "choose" },
                        onModeSelect = { modeCalls += "select:$it" },
                        onDismissMode = { modeCalls += "dismiss" },
                    ),
                )
            }
        }
    }

    // F19 T029 (contracts/multimodal-ui.md U8–U10, wireframe §5 and §8).

    private fun pdfRow(id: Long, mode: IndexMode, state: QueueItemState, pages: Int? = 40) =
        QueueItem(QueueKey.Document(id), "informe-$id.pdf", 2_400_000, DocumentFormat.PDF, state, indexMode = mode, pageCount = pages)

    @Test
    fun withTheModeOff_theRowIsNotThere() {
        show(IngestPreviewData.png)

        compose.onNodeWithText("Modo de los PDF").assertDoesNotExist()
    }

    @Test
    fun withTheModeOn_theRowSaysTheSelection_andOpensTheSheet() {
        show(IngestPreviewData.png.copy(multimodalOn = true, indexMode = IndexMode.BOTH))

        compose.onNodeWithText("Ambos (texto y páginas)", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Modo de los PDF").performClick()

        assertThat(modeCalls).containsExactly("choose")
    }

    @Test
    fun theSheet_isARadioGroup_withTheImagesNote() {
        show(IngestPreviewData.png.copy(multimodalOn = true, indexMode = IndexMode.BOTH, choosingMode = true))

        compose.onNode(hasText("Ambos") and isSelected()).assertExists()
        compose.onNodeWithText("Las imágenes JPG y PNG se indexan siempre como páginas.").assertExists()
        compose.onNodeWithText("Visual").performClick()

        assertThat(modeCalls).containsExactly("select:VISUAL")
    }

    @Test
    fun theQueue_saysTheModeAndThePages() {
        show(
            IngestPreviewData.png.copy(
                multimodalOn = true,
                queue = ImportQueue(
                    items = listOf(
                        pdfRow(1, IndexMode.BOTH, QueueItemState.Ready),
                        pdfRow(2, IndexMode.VISUAL, QueueItemState.IndexingPages(12, 40)),
                        pdfRow(3, IndexMode.TEXT, QueueItemState.Ready),
                    ),
                    readyCount = 2, pdfSupported = true, indexAvailable = true, canStart = true,
                ),
            ),
        )

        compose.onNodeWithText("Ambos", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Visual", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("40 páginas · unos 3 min", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Indexando páginas · 12 / 40", substring = true, useUnmergedTree = true).assertExists()
        // A Text row keeps the look it had.
        compose.onAllNodesWithText("Texto", useUnmergedTree = true).assertCountEquals(0)
    }

    /** Found in the emulator walkthrough: TalkBack reads the mode and the estimate, and a short one has no «unos». */
    @Test
    fun aRowWithPages_isReadWithItsModeAndEstimate() {
        show(
            IngestPreviewData.png.copy(
                multimodalOn = true,
                queue = ImportQueue(
                    items = listOf(
                        QueueItem(QueueKey.Document(1), "foto.png", 151, DocumentFormat.PNG, QueueItemState.Ready, indexMode = IndexMode.VISUAL, pageCount = 1),
                    ),
                    readyCount = 1, pdfSupported = true, indexAvailable = true, canStart = true,
                ),
            ),
        )

        compose.onNode(hasContentDescription("Visual", substring = true) and hasContentDescription("1 página · menos de 1 min", substring = true))
            .assertExists()
    }

    @Test
    fun anImageWithTheModeOff_saysWhy() {
        show(
            IngestPreviewData.png.copy(
                queue = ImportQueue(
                    items = listOf(
                        QueueItem(QueueKey.Attempt("a"), "foto.jpg", 1_000, DocumentFormat.JPEG, QueueItemState.ImportFailed(IngestError.IMAGE_NEEDS_MULTIMODAL)),
                    ),
                    readyCount = 0, pdfSupported = true, indexAvailable = true, canStart = false,
                ),
            ),
        )

        compose.onNodeWithText("Imagen no admitida", useUnmergedTree = true).assertExists()
    }

    @Test
    fun thePicker_offersImagesOnlyWithTheModeOn() {
        assertThat(IngestMimeTypes.of(false).toList()).containsNoneOf("image/jpeg", "image/png")
        assertThat(IngestMimeTypes.of(true).toList()).containsAtLeast("application/pdf", "image/jpeg", "image/png")
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

    // F17 T025 (K6, wireframe §5).

    @Test
    fun theDestinationRowSaysTheFolder_andOpensTheSheet() {
        val legal = com.lisdatasolutions.microrag.domain.rag.Folder(1, "Legal", 10, 0)
        val folders = com.lisdatasolutions.microrag.domain.rag.FolderList(listOf(legal), unfiledCount = 0)
        val opened = mutableListOf<String>()
        compose.setContent {
            LisdataTheme {
                IngestScreen(
                    IngestPreviewData.png.copy(folders = folders, destination = 1),
                    IngestActions(onChooseDestination = { opened += "destination" }),
                )
            }
        }

        compose.onNodeWithText("Carpeta de destino", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Legal", useUnmergedTree = true).performClick()
        assertThat(opened).containsExactly("destination")
    }

    @Test
    fun withoutAFolderTheDestinationIsGeneral_andEveryRowSaysItsFolder() {
        show(IngestPreviewData.png.copy(folders = com.lisdatasolutions.microrag.domain.rag.FolderList(emptyList(), 0)))

        compose.onAllNodesWithText("General", substring = true, useUnmergedTree = true).fetchSemanticsNodes().let {
            // The destination row and the three queue rows.
            assertThat(it.size).isEqualTo(4)
        }
    }

    @Test
    fun aDeletedDestinationSaysSo() {
        show(IngestPreviewData.png.copy(destinationMissing = true))

        compose.onNodeWithText("La carpeta de destino ya no existe. Se importó en «General».").assertExists()
    }
}

package com.lisdatasolutions.microrag.feature.library

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import com.lisdatasolutions.microrag.feature.folders.FoldersUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F08 T017: P06 as in PNG 06, the rows of research R10 and the proposals B1–B4 (contracts/library-ui.md). */
@RunWith(AndroidJUnit4::class)
// The PNG's 390 × 844 dp, so the states fit as they do on a phone.
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class LibraryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = LibraryActions(
        onBack = { calls += "back" },
        onImport = { calls += "import" },
        onOpen = { calls += "open $it" },
        onTextChange = { calls += "text $it" },
        onFilterChange = { calls += "filter $it" },
        onClearFilters = { calls += "clear" },
        onLoadMore = { calls += "more" },
        onRetry = { calls += "retry" },
        onIncludedChange = { id, included -> calls += "include $id $included" },
        onOpenFolder = { calls += "folder $it" },
        onNewFolder = { calls += "new folder" },
        onRenameFolder = { calls += "rename $it" },
        onDeleteFolder = { calls += "delete folder $it" },
    )

    private fun show(state: LibraryUiState, folders: FoldersUiState = LibraryPreviewData.folders) {
        compose.setContent { LisdataTheme { LibraryScreen(state, actions, folders = folders) } }
    }

    private fun exists(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertExists()

    private fun absent(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()

    private fun scrollTo(text: String) =
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    /** Rows are read as one sentence (A1), so they are found by it. */
    private fun scrollToRow(name: String) =
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(name, substring = true))

    /** PNG 06 lives inside a folder since F17 (K2): its name on top, its own search, the filters. */
    @Test
    fun documents_matchPng06InsideAFolder() {
        show(LibraryPreviewData.png)

        listOf(
            "Legal", "Buscar en Legal…",
            "Todos", "Indexados", "Pendientes", "24 DOCUMENTOS", "Incluir en RAG",
            "Manual de procesos.pdf", "PDF · 860 fragmentos",
        ).forEach(::exists)
        absent("Tus documentos")
        listOf(
            "Normativa interna.pdf" to "PDF · 520 fragmentos",
            "Informe anual.pdf" to "PDF · 1.120 fragmentos",
            "Notas del proyecto.md" to "MD · 96 fragmentos",
        ).forEach { (name, meta) ->
            scrollToRow(name)
            exists(name)
            exists(meta)
        }
        scrollTo("en futuras respuestas. No lo elimina.")
        exists("Excluir un archivo evita que se use")
    }

    @Test
    fun rows_showTheirStateAsResearchR10() {
        show(LibraryPreviewData.allStates)

        listOf(
            "Guía de instalación.pdf" to "PDF · Pendiente",
            "Notas de campo.txt" to "TXT · Indexando",
            "Normas antiguas.pdf" to "PDF · Fuera del índice",
            "Contrato escaneado.pdf" to "Error de indexación",
        ).forEach { (name, state) ->
            scrollToRow(name)
            exists(state)
        }
        // A1: each row is one sentence with its name, state and inclusion.
        scrollToRow("Contrato.pdf")
        compose.onNode(hasContentDescription("Contrato.pdf, PDF · 412 fragmentos, incluido")).assertExists()
    }

    // US2 (T025)

    @Test
    fun theSwitch_changesTheInclusion_andIsDisabledUntilIndexed_B5_B6() {
        show(LibraryPreviewData.allStates)

        compose.onAllNodes(hasContentDescription("Incluir en respuestas") and isToggleable())[0].assertIsOn().performClick()
        assertThat(calls).containsExactly("include 10 false")

        // B6: an excluded row keeps its look and says so.
        compose.onNode(hasContentDescription("Informe anual.pdf, PDF · 1.120 fragmentos, excluido de las respuestas")).assertExists()
        // B5: not indexed, the switch is disabled and the row says when it can be included.
        scrollToRow("Guía de instalación.pdf")
        compose.onNode(hasContentDescription("Guía de instalación.pdf, PDF · Pendiente, Se podrá incluir cuando esté indexado"))
            .assertExists()
        scrollToRow("Contrato escaneado.pdf")
        compose.onNode(hasContentDescription("Contrato escaneado.pdf, PDF, Error de indexación, Se podrá incluir cuando esté indexado"))
            .assertExists()
        val disabled = compose.onAllNodes(hasContentDescription("Incluir en respuestas") and isToggleable() and isNotEnabled())
            .fetchSemanticsNodes()
        assertThat(disabled).isNotEmpty()
    }

    // US5 (T048)

    @Test
    fun aReindexingRow_endsItsMetaInBlue_andAFailedOneShowsItsChip_B7() {
        show(LibraryPreviewData.allStates)

        scrollToRow("Normativa interna.pdf")
        exists("PDF · 520 fragmentos · Reindexando")
        compose.onNode(hasContentDescription("Normativa interna.pdf, PDF · 520 fragmentos · Reindexando, incluido")).assertExists()
        scrollToRow("Catálogo 2024.pdf")
        exists("Reindexado fallido")
        compose.onNode(hasContentDescription("Catálogo 2024.pdf, PDF · 240 fragmentos, Reindexado fallido, incluido")).assertExists()
    }

    @Test
    fun plusAndRows_callTheirActions() {
        show(LibraryPreviewData.png)

        compose.onNodeWithContentDescription("Importar a Legal").performClick()
        compose.onNodeWithText("Manual de procesos.pdf", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Indexados").performClick()

        // «more»: the PNG shows 4 of 24, so the next block is asked for at once.
        assertThat(calls.filterNot { it == "more" }).containsExactly("import", "open 1", "filter ${LibraryFilter.INDEXED}").inOrder()
    }

    @Test
    fun loading_showsSkeletonRowsUnderTheFilters_B1() {
        show(LibraryUiState(content = LibraryContent.Loading))

        exists("Buscar documentos…")
        compose.onAllNodesWithContentDescription("Cargando").assertCountEquals(4)
    }

    @Test
    fun empty_invitesToImportWithoutSearchOrFilters_B2() {
        show(LibraryUiState(content = LibraryContent.Empty))

        exists("Aún no tienes documentos")
        exists("Importa PDF, TXT o Markdown para consultarlos sin conexión.")
        absent("Buscar documentos…")
        absent("Todos")
        compose.onNodeWithText("Importar documentos").performClick()
        assertThat(calls).containsExactly("import")
    }

    @Test
    fun noResults_offersToClearTheFilters_B3() {
        show(LibraryUiState(text = "contrato", filter = LibraryFilter.PENDING, content = LibraryContent.NoResults))

        exists("No hay coincidencias")
        exists("Prueba con otro nombre o filtro.")
        exists("contrato")
        compose.onNodeWithText("Limpiar filtros").performClick()
        assertThat(calls).containsExactly("clear")
    }

    @Test
    fun error_offersRetry_butUnavailableDoesNot_B4() {
        show(LibraryUiState(content = LibraryContent.Error))
        compose.onNodeWithText("Reintentar").performClick()
        assertThat(calls).containsExactly("retry")
    }

    @Test
    fun unavailable_hasNoRetry_B4() {
        show(LibraryUiState(content = LibraryContent.Unavailable))

        exists("La biblioteca no está disponible")
        absent("Reintentar")
    }

    @Test
    fun nearTheEnd_asksForTheNextBlock() {
        show(LibraryPreviewData.many(count = 50, total = 120))

        assertThat(calls).doesNotContain("more")
        scrollToRow("Documento 50.pdf")

        assertThat(calls).contains("more")
    }

    @Test
    fun theListPosition_survivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LisdataTheme { LibraryScreen(LibraryPreviewData.many(count = 60, total = 60), actions) } }
        scrollToRow("Documento 40.pdf")
        compose.onNodeWithText("Documento 40.pdf", useUnmergedTree = true).assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Documento 40.pdf", useUnmergedTree = true).assertIsDisplayed()
        absent("Tus documentos")
    }

    // F17 T016 (wireframe §1–§2, contract UI §1): the Library by folders.

    @Test
    fun theRootListsTheFolders_GeneralLast_andNewFolder() {
        show(LibraryPreviewData.root)

        listOf("Tus documentos", "Buscar documentos…", "CARPETAS", "Legal", "24 documentos", "Motores", "5 documentos", "Nueva carpeta")
            .forEach(::exists)
        absent("Todos")
        absent("Manual de procesos.pdf")
        scrollTo("Nueva carpeta")
        exists("General")
        exists("3 documentos")
        // U2: one sentence per folder.
        compose.onNode(hasText("Legal") and hasText("24 documentos")).assertExists()

        compose.onNodeWithText("Legal", useUnmergedTree = true).performClick()
        compose.onNodeWithText("General", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Nueva carpeta", useUnmergedTree = true).performClick()
        assertThat(calls.filterNot { it == "more" }).containsExactly("folder 1", "folder null", "new folder").inOrder()
    }

    @Test
    fun generalIsLeftOutWhenItHasNoDocuments() {
        show(LibraryPreviewData.root, LibraryPreviewData.folders.copy(folders = LibraryPreviewData.folders.folders!!.copy(unfiledCount = 0)))

        scrollTo("Nueva carpeta")
        absent("General")
    }

    /** D-072: with nothing imported, the empty state, and under it the folders that exist. */
    @Test
    fun anEmptyLibraryStillShowsItsFolders() {
        val empty = FoldersUiState(FolderList(listOf(com.lisdatasolutions.microrag.domain.rag.Folder(1, "Legal", 1, 0)), unfiledCount = 0))
        show(LibraryPreviewData.empty, empty)

        exists("Aún no tienes documentos")
        exists("Legal")
        exists("0 documentos")
        exists("Nueva carpeta")
        absent("Buscar documentos…")
    }

    @Test
    fun theRootSearchNamesTheFolderOfEachRow() {
        show(LibraryPreviewData.rootSearch)

        exists("PDF · 860 fragmentos · Legal")
        exists("PDF · 210 fragmentos · General")
        absent("CARPETAS")
        absent("Todos")
    }

    @Test
    fun aFolderHasItsMenu_butGeneralDoesNot() {
        show(LibraryPreviewData.png)

        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Renombrar carpeta").performClick()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Eliminar carpeta").performClick()
        assertThat(calls.filterNot { it == "more" }).containsExactly("rename 1", "delete folder 1").inOrder()
    }

    @Test
    fun generalHasNoMenu() {
        show(LibraryPreviewData.allStates)

        exists("General")
        exists("Buscar en General…")
        compose.onNodeWithContentDescription("Más opciones").assertDoesNotExist()
    }

    @Test
    fun anEmptyFolderInvitesToImportIntoIt() {
        show(LibraryPreviewData.folderEmpty)

        exists("Motores")
        exists("Esta carpeta está vacía")
        compose.onNodeWithText("Importar a Motores").performClick()
        assertThat(calls).containsExactly("import")
    }


    /** F19 T038 (contract U11): type · mode · pages (Visual and Both) · chunks (Text and Both); Text rows as before. */
    @Test
    fun aRowWithPages_saysTypeModePagesAndChunks() {
        val both = LibraryDocument(1, "Informe.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(860, null), true, indexMode = IndexMode.BOTH, pages = 40)
        val visual = LibraryDocument(2, "Plano.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(0, null), true, indexMode = IndexMode.VISUAL, pages = 12)
        val pending = LibraryDocument(3, "Foto.jpg", DocumentFormat.JPEG, LibraryDocumentState.Pending, true, indexMode = IndexMode.VISUAL, pages = 1)
        val text = LibraryDocument(4, "Notas.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(5, null), true)
        compose.setContent { LisdataTheme { Column { listOf(both, visual, pending, text).forEach { Text(it.metaText()) } } } }

        exists("PDF · Ambos · 40 páginas · 860 fragmentos")
        exists("PDF · Visual · 12 páginas")
        exists("Imagen · Visual · Pendiente")
        exists("PDF · 5 fragmentos")
    }
}

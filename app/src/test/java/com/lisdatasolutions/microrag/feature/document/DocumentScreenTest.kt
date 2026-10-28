package com.lisdatasolutions.microrag.feature.document

import android.app.Application
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F08 T030 and T042: P07 as in PNG 07, its figures and preview, and the proposals D1–D11 (contracts/library-ui.md). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class DocumentScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = DocumentActions(
        onBack = { calls += "back" },
        onIncludedChange = { calls += "include $it" },
        onOpenIndex = { calls += "index" },
        onReindex = { calls += "reindex" },
        onRetry = { calls += "retry" },
        onDiscardReindex = { calls += "discard" },
        onDelete = { calls += "delete" },
        onRemoveFromIndex = { calls += "remove" },
        onConfirmDialog = { calls += "confirm" },
        onDismissDialog = { calls += "dismiss" },
    )

    private fun show(state: DocumentUiState) {
        compose.setContent { LisdataTheme { DocumentScreen(state, actions) } }
    }

    private fun exists(text: String, substring: Boolean = false) =
        compose.onNodeWithText(text, substring = substring, useUnmergedTree = true).assertExists()

    private fun absent(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()

    private fun scrollTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    private fun click(text: String) {
        scrollTo(text)
        compose.onNodeWithText(text).performClick()
    }

    // US3 (T030)

    @Test
    fun png_matchesPng07() {
        show(DocumentPreviewData.png)

        listOf(
            "Detalle del documento", "Manual de procesos.pdf", "Indexado", "PÁGINAS", "48", "FRAGMENTOS", "860",
            "TAMAÑO", "IMPORTADO", "Incluir en respuestas", "Disponible para consultas RAG",
        ).forEach { exists(it) }
        exists("12 oct", substring = true)
        exists("MB", substring = true)
        scrollTo("Reindexar documento")
        listOf(
            "VISTA PREVIA DEL CONTENIDO", "Página 12", "Antes de cerrar el proceso, se", "debe registrar la validación final",
            "en el sistema documental.", "Reindexar documento", "Eliminar",
        ).forEach { exists(it) }
        compose.onNodeWithContentDescription("Más opciones").assertExists()
    }

    private fun lines(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().lineCount
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theActions_areSideBySideOnOneLine_asInPng07() {
        show(DocumentPreviewData.png)
        scrollTo("Eliminar")

        assertThat(top("Eliminar")).isEqualTo(top("Reindexar documento"))
        assertThat(lines("Eliminar")).isEqualTo(1)
        assertThat(lines("Reindexar documento")).isEqualTo(1)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "es-w320dp-h800dp")
    fun onANarrowScreen_theActionsAreStacked_neverCut() {
        show(DocumentPreviewData.png)
        scrollTo("Eliminar")

        assertThat(top("Eliminar")).isGreaterThan(top("Reindexar documento"))
        assertThat(lines("Eliminar")).isEqualTo(1)
        assertThat(lines("Reindexar documento")).isEqualTo(1)
    }

    @Test
    fun anExcludedDocument_saysItIsNotUsedInAnswers() {
        show(DocumentPreviewData.excluded)

        exists("No se usa en las respuestas")
        absent("Disponible para consultas RAG")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLongFirstChunk_isCutWithSeeMore_andOpensWhole() {
        show(DocumentPreviewData.longFirstChunk)
        val text = DocumentPreviewData.longText

        scrollTo("Ver más")
        assertThat(lines(text)).isEqualTo(3)
        click("Ver más")
        assertThat(lines(text)).isGreaterThan(3)
        click("Ver menos")
        assertThat(lines(text)).isEqualTo(3)
        exists("Ver más")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "es-w411dp-h817dp")
    fun withALongFirstChunk_theActionsAreInSight_asInPng07() {
        // The A063 (D-022) without its status and three-button navigation bars; DocumentScreenLayoutTest measures it there.
        show(DocumentPreviewData.longFirstChunk)

        val screen = compose.onRoot().getBoundsInRoot().bottom
        assertThat(compose.onNodeWithText("Eliminar", useUnmergedTree = true).getUnclippedBoundsInRoot().bottom).isAtMost(screen)
    }

    @Test
    fun aShortFirstChunk_hasNoSeeMore() {
        show(DocumentPreviewData.firstChunkOnly)

        scrollTo("Eliminar")
        absent("Ver más")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aCitation_isNeverCut() {
        val long = DocumentPreviewData.longText
        show(DocumentPreviewData.png.copy(preview = PreviewContent.Shown(DocumentPreviewData.citation.copy(text = long))))

        scrollTo("Eliminar")
        absent("Ver más")
        assertThat(lines(long)).isGreaterThan(3)
    }

    @Test
    fun theGridAndTheCitation_areReadAsSentences_A2_A3() {
        show(DocumentPreviewData.png)

        compose.onNode(hasContentDescription("Páginas, 48")).assertExists()
        compose.onNode(hasContentDescription("Fragmentos, 860")).assertExists()
        compose.onNode(hasContentDescription("Importado, 12 oct", substring = true)).assertExists()
        scrollTo("Eliminar")
        compose.onNode(hasContentDescription("Fragmento citado: debe registrar la validación final"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aTextDocument_hasNoPageChip_andItsDashIsRead() {
        show(DocumentPreviewData.plainText)

        exists("—")
        compose.onNode(hasContentDescription("Páginas, sin páginas")).assertExists()
        scrollTo("Eliminar")
        exists("Notas tomadas durante la visita a la planta.")
        compose.onNodeWithText("Página", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun pending_explainsWhenItWillBeIndexed_andOnlyOffersDelete_D1() {
        show(DocumentPreviewData.pending)

        exists("Pendiente")
        compose.onNode(hasContentDescription("Fragmentos, sin fragmentos")).assertExists()
        compose.onNode(isToggleable()).assertIsNotEnabled()
        exists("Se podrá incluir cuando esté indexado")
        absent("Disponible para consultas RAG")
        scrollTo("Eliminar")
        exists("Este documento aún no está indexado.")
        exists("Se indexará al pulsar Iniciar indexación en la Ingesta.")
        absent("Reindexar documento")
        compose.onNodeWithContentDescription("Más opciones").assertDoesNotExist()
    }

    @Test
    fun indexing_showsItsRealProgress_andLinksToTheIndexScreen_D2() {
        show(DocumentPreviewData.indexing)

        exists("Indexando")
        scrollTo("Eliminar")
        exists("120 de 430 fragmentos")
        absent("Reindexar documento")
        click("Ver estado de la indexación")
        assertThat(calls).containsExactly("index")
    }

    @Test
    fun failed_showsTheCause_andRetryOnlyWhenItCanSucceed_D3() {
        show(DocumentPreviewData.failed)

        exists("Error")
        scrollTo("Eliminar")
        exists("Sin texto")
        exists("Sin texto extraíble (¿documento escaneado?).")
        absent("Reintentar")
    }

    @Test
    fun failedRetryable_offersRetry_D3() {
        show(DocumentPreviewData.failedRetryable)

        click("Reintentar")

        assertThat(calls).containsExactly("retry")
    }

    @Test
    fun aLostCitation_showsTheNormalPreviewWithANotice_D7() {
        show(DocumentPreviewData.citationMissing)

        scrollTo("Eliminar")
        exists("El fragmento citado ya no está en este documento.")
        exists("Este manual describe los procesos internos de la empresa.")
    }

    @Test
    fun loading_showsTheTopBarAndARing_D9() {
        show(DocumentPreviewData.loading)

        exists("Detalle del documento")
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)).assertExists()
        absent("Eliminar")
    }

    // US4 (T042)

    @Test
    fun outOfIndex_offersReindexAndDelete_withTheSwitchDisabled_D4() {
        show(DocumentPreviewData.outOfIndex)

        exists("Fuera del índice")
        compose.onNode(isToggleable()).assertIsNotEnabled()
        exists("Se podrá incluir cuando esté indexado")
        compose.onNodeWithContentDescription("Más opciones").assertDoesNotExist()
        scrollTo("Eliminar")
        exists("Quitaste este documento del índice.")
        exists("Su archivo sigue guardado en la app.")
        click("Reindexar documento")
        assertThat(calls).containsExactly("reindex")
    }

    @Test
    fun reindexing_showsItsProgress_thePreviousVersionAndThePause_D5() {
        show(DocumentPreviewData.reindexingPaused)

        scrollTo("Ver estado de la indexación")
        exists("Reindexando · 320 de 860 fragmentos")
        exists("Mientras tanto, las respuestas usan la versión anterior.")
        exists("Pausa por temperatura")
        scrollTo("Reindexando…")
        compose.onNodeWithText("Reindexando…").assertIsNotEnabled()
        absent("Reindexar documento")
    }

    @Test
    fun aFailedReindex_offersRetryAndDiscard_D6() {
        show(DocumentPreviewData.reindexFailed)

        scrollTo("Descartar")
        exists("No se pudo reindexar: falló el cálculo de los vectores.")
        exists("Las respuestas siguen usando la versión anterior.")
        click("Reintentar")
        click("Descartar")
        assertThat(calls).containsExactly("retry", "discard").inOrder()
        absent("Reindexar documento")
    }

    @Test
    fun theDeleteDialog_explainsTheConsequences_D8() {
        show(DocumentPreviewData.deleteDialog)

        exists("¿Eliminar «Manual de procesos.pdf»?")
        exists("Se borrarán la copia guardada por la app, su texto, sus fragmentos y vectores, y sus datos. El archivo original de tu teléfono no cambia.")
        exists("Cancelar")
        compose.onAllNodes(hasText("Eliminar")).filterToOne(hasAnyAncestor(isDialog())).performClick()
        assertThat(calls).containsExactly("confirm")
    }

    @Test
    fun theRemoveDialog_explainsTheConsequences_D8() {
        show(DocumentPreviewData.removeDialog)

        exists("¿Quitar del índice?")
        exists("Se borrarán sus fragmentos y vectores, y dejará de usarse en las respuestas. El archivo se conserva para reindexarlo cuando quieras.")
        compose.onNodeWithText("Cancelar").performClick()
        assertThat(calls).containsExactly("dismiss")
    }

    @Test
    fun theMoreMenu_offersRemoveFromIndex() {
        show(DocumentPreviewData.png)

        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Quitar del índice").performClick()

        assertThat(calls).containsExactly("remove")
    }

    @Test
    fun deleting_showsItUntilItEnds_D8() {
        show(DocumentPreviewData.deleting)

        scrollTo("Eliminando…")
        compose.onNodeWithText("Eliminando…").assertIsNotEnabled()
        compose.onNodeWithText("Reindexar documento").assertIsNotEnabled()
    }

    @Test
    fun aMissingCopy_onlyOffersDelete_D10() {
        show(DocumentPreviewData.copyMissing)

        scrollTo("Eliminar")
        exists("No se encuentra el archivo guardado por la app, así que no se puede reindexar.")
        exists("Puedes eliminarlo e importarlo de nuevo.")
        absent("Reindexar documento")
        exists("Este manual describe los procesos internos de la empresa.")
    }

    @Test
    fun changesAreAnnouncedPolitely_A7() {
        show(DocumentPreviewData.png.copy(announcement = DocumentAnnouncement.REMOVED_FROM_INDEX))

        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Quitado del índice") and
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        ).assertExists()
    }

    @Test
    fun unavailable_offersNothingThatWrites_D11() {
        show(DocumentPreviewData.unavailable)

        exists("La biblioteca no está disponible")
        absent("Eliminar")
        absent("Reintentar")
        compose.onNodeWithContentDescription("Más opciones").assertDoesNotExist()
    }
}

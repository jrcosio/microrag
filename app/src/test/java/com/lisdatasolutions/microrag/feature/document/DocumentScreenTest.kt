package com.lisdatasolutions.microrag.feature.document

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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

    @Test
    fun unavailable_offersNothingThatWrites_D11() {
        show(DocumentPreviewData.unavailable)

        exists("La biblioteca no está disponible")
        absent("Eliminar")
        absent("Reintentar")
        compose.onNodeWithContentDescription("Más opciones").assertDoesNotExist()
    }
}

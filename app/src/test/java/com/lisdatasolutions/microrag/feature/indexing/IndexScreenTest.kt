package com.lisdatasolutions.microrag.feature.indexing

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F07 T031: P08 as in PNG 08, and P1, P2, P9 and P12 (contracts/index-ui.md). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es")
class IndexScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: IndexUiState) {
        compose.setContent {
            LisdataTheme {
                IndexScreen(
                    state,
                    IndexActions(
                        onPause = { calls += "pause" },
                        onResume = { calls += "resume" },
                        onRetry = { calls += "retry $it" },
                        onPrepareModels = { calls += "setup" },
                    ),
                )
            }
        }
    }

    private fun exists(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertExists()

    @Test
    fun runningState_matchesPng08() {
        show(IndexPreviewData.png)

        listOf(
            "Estado de indexación", "PROCESAMIENTO LOCAL", "ETAPAS DEL PROCESO",
            "Extracción de texto", "Fragmentación", "Generación de embeddings", "Persistencia vectorial",
            "Procesando ahora", "Manual de procesos.pdf", "Pausar indexación",
        ).forEach(::exists)
        compose.onAllNodesWithText("Completado", useUnmergedTree = true).assertCountEquals(2)
        exists("En curso")
        exists("Pendiente")
        // U1: ring, «68%», «COMPLETADO» and «8.432 / 12.400 fragmentos» are read as one sentence.
        compose.onNode(hasContentDescription("68 % completado, 8.432 de 12.400 fragmentos")).assertExists()

        compose.onNode(hasText("Pausar indexación") and hasClickAction()).assertIsEnabled().performClick()
        assertThat(calls).containsExactly("pause")
    }

    @Test
    fun withTheTotalUnknown_noPercentageIsShown() {
        show(IndexPreviewData.preparing)

        compose.onNode(hasContentDescription("Preparando documentos")).assertExists()
        compose.onNode(hasContentDescription("%", substring = true)).assertDoesNotExist()
        compose.onNodeWithText("%", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("y 3 documentos más en cola", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun remainingTime_andTheQueue() {
        show(IndexPreviewData.running)

        exists("≈ 23 min restantes")
        compose.onNodeWithText("y 2 documentos más en cola", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun idle_isAllUpToDateWithoutAction() {
        show(IndexPreviewData.idle)

        exists("Todo actualizado")
        exists("No hay documentos pendientes de indexar.")
        compose.onNodeWithText("Pausar indexación", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Reanudar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun loading_showsTheLoadingRingOnly() {
        show(IndexPreviewData.loading)

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Procesando…")).assertExists()
        compose.onNodeWithText("ETAPAS DEL PROCESO", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Todo actualizado", useUnmergedTree = true).assertDoesNotExist()
    }

    // F07 T048: P3, P4, P5 and P7.

    @Test
    fun pausedByTheUser_offersResume() {
        show(IndexPreviewData.paused)

        // The chip under the chunks and the stage that was in progress.
        compose.onAllNodesWithText("En pausa", useUnmergedTree = true).assertCountEquals(2)
        compose.onNode(hasText("Reanudar") and hasClickAction()).assertIsEnabled().performClick()
        assertThat(calls).containsExactly("resume")
        compose.onNodeWithText("Pausar indexación", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tooHot_explainsItAndStillOffersPause() {
        show(IndexPreviewData.thermal)

        compose.onAllNodesWithText("Pausa por temperatura", useUnmergedTree = true).assertCountEquals(2)
        exists("El teléfono está muy caliente. La indexación seguirá sola cuando se enfríe.")
        compose.onNode(hasText("Pausar indexación") and hasClickAction()).assertExists()
    }

    @Test
    fun waitingForTheSystem_isExplained() {
        show(IndexPreviewData.waitingForSystem)

        exists("Android ha limitado el trabajo en segundo plano. Seguirá en cuanto lo permita o al abrir la app.")
    }

    @Test
    fun noSpace_guidesAndOffersResume() {
        show(IndexPreviewData.noSpace)

        exists("No queda espacio para seguir indexando. Libera espacio en el teléfono y pulsa Reanudar.")
        compose.onNode(hasText("Reanudar") and hasClickAction()).assertExists()
    }

    @Test
    fun missingModel_leadsToTheModelSetup() {
        show(IndexPreviewData.modelMissing)

        exists("Falta el modelo de embeddings")
        compose.onNode(hasText("Preparar modelos") and hasClickAction()).performClick()
        assertThat(calls).containsExactly("setup")
        compose.onNodeWithText("ETAPAS DEL PROCESO", useUnmergedTree = true).assertDoesNotExist()
    }
}

package com.lisdatasolutions.microrag.debug.retrieval

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.debug.retrieval.RetrievalDiagnosticUiState.Status
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.retrieval.CandidateScore
import com.lisdatasolutions.microrag.domain.retrieval.ContextBlock
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalTimings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F10 T036 (contracts/retrieval-diagnostic.md, rules V5–V6): what the diagnostic shows for each outcome. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class RetrievalDiagnosticScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = RetrievalDiagnosticActions(
        onBack = { calls += "back" },
        onQuestionChange = { calls += "question:$it" },
        onPreviousChange = { calls += "previous:$it" },
        onSearch = { calls += "search" },
    )

    private fun show(state: RetrievalDiagnosticUiState) {
        compose.setContent { LisdataTheme { RetrievalDiagnosticContent(state, actions) } }
    }

    private fun done(outcome: RetrievalOutcome, cuts: Int = 0, followUp: Boolean = false) = RetrievalDiagnosticUiState(
        question = "¿Y el aceite?",
        previousQuestion = "¿Cada cuánto se revisa la bomba?",
        status = Status.Done(
            RetrievalResult(
                searchText = "¿Cada cuánto se revisa la bomba? ¿Y el aceite?",
                followUp = followUp,
                embeddingCuts = cuts,
                timings = RetrievalTimings(118.24, 22.9, 19.36, 3.14, 146.04, coldEngine = true),
                outcome = outcome,
            ),
        ),
    )

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun exists(text: String) {
        scrollTo(text)
        compose.onNodeWithText(text, substring = true, useUnmergedTree = true).assertExists()
    }

    private fun absent(text: String) = compose.onNodeWithText(text, substring = true, useUnmergedTree = true).assertDoesNotExist()

    @Test
    fun blocks_showSummaryTimingsBlocksAndCandidates() {
        show(done(BLOCKS, followUp = true))

        exists("Seguimiento: sí")
        exists("Texto de búsqueda: ¿Cada cuánto se revisa la bomba? ¿Y el aceite?")
        exists("2 bloques · 702 tokens")
        exists("Embedding: 118.2 ms")
        exists("Vectorial: 22.9 ms")
        exists("Léxica: 19.4 ms")
        exists("Selección: 3.1 ms")
        exists("Total: 146.0 ms")
        exists("Motor cargado: no")
        exists("[1] Manual de bombas.pdf")
        exists("Ruta: Mantenimiento › Bombas")
        exists("Página 12")
        exists("Fragmentos 3–4 · ids 815, 816 · 402 tokens")
        exists("Las bombas se revisan cada 500 horas.")
        exists("Fragmentos 7 · ids 900 · 300 tokens")
        exists("#1 · id 815 · RRF 0.0325 · vector 1 (0.123) · léxica 2 · elegido")
        exists("#3 · id 77 · RRF 0.0161 · vector — · léxica 1")
        absent("Recortes del embedding")
    }

    @Test
    fun theCutsRow_onlyWhenTheEmbeddedTextWasCut() {
        show(done(BLOCKS, cuts = 2))
        exists("Recortes del embedding: 2")
        exists("Seguimiento: no")
    }

    @Test
    fun everyOutcomeWithoutBlocks_isSaidInWords() {
        val cases = listOf(
            RetrievalOutcome.NoResults to "Sin resultados",
            RetrievalOutcome.EmptyQuestion to "Pregunta vacía",
            RetrievalOutcome.IndexUnavailable(IndexCompatibility.Incompatible("embedding_dims", "256", "768")) to
                "Índice no disponible: incompatible (embedding_dims)",
            RetrievalOutcome.IndexUnavailable(IndexCompatibility.Unreadable("search: IllegalStateException")) to
                "Índice no disponible: ilegible (search: IllegalStateException)",
            RetrievalOutcome.ModelsNotReady to "Modelos no listos",
            RetrievalOutcome.EmbeddingFailed to "Fallo del embedding",
        )
        var state by mutableStateOf(done(cases.first().first))
        compose.setContent { LisdataTheme { RetrievalDiagnosticContent(state, actions) } }
        cases.forEach { (outcome, words) ->
            state = done(outcome)
            compose.waitForIdle()
            exists(words)
            absent("Bloques")
            absent("Candidatos")
        }
    }

    @Test
    fun whileSearching_theButtonIsOff_andTheSearchIsAnnounced() {
        show(RetrievalDiagnosticUiState(question = "pregunta", status = Status.Searching))

        // The loading button shows its spinner instead of the text, and says «Cargando».
        val loadingButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Cargando")
        compose.onNode(loadingButton).assertIsNotEnabled()
        compose.onNode(hasText("Buscando…") and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertExists()
        absent("Seguimiento")
    }

    @Test
    fun fieldsHaveLabels_andTargetsAreLargeEnough() {
        show(RetrievalDiagnosticUiState())

        compose.onNodeWithText("Pregunta", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Pregunta anterior (opcional)", useUnmergedTree = true).assertExists()
        val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertThat(fields).hasSize(2)
        fields.forEach { node -> assertThat(node.size.height).isAtLeast(with(compose.density) { 48.dp.roundToPx() }) }
        compose.onNodeWithText("Buscar").assertHeightIsAtLeast(48.dp)

        compose.onNodeWithText("Buscar").performClick()
        assertThat(calls).containsExactly("search")
    }

    private companion object {
        val BLOCKS = RetrievalOutcome.Blocks(
            blocks = listOf(
                ContextBlock(4, "Manual de bombas.pdf", "Mantenimiento › Bombas", 12, 3, 4, listOf(815, 816),
                    "Las bombas se revisan cada 500 horas.\n\nEl aceite, cada año.", 402),
                ContextBlock(9, "Guía.md", null, null, 7, 7, listOf(900), "Los filtros se limpian cada mes.", 300),
            ),
            candidates = listOf(
                CandidateScore(815, 1, 0.032522, 1, 0.12345, 2, selected = true),
                CandidateScore(900, 2, 0.016393, 3, 0.2, null, selected = true),
                CandidateScore(77, 3, 0.016129, null, null, 1, selected = false),
            ),
        )
    }
}

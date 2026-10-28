package com.lisdatasolutions.microrag.feature.home

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F09 T009: Home as in PNG 03, and every control asks for its destination (contracts/home-ui.md). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = HomeActions(
        onNewChat = { calls += "chat" },
        onImport = { calls += "import" },
        onLibrary = { calls += "library" },
        onIndex = { calls += "index" },
        onLanguages = { calls += "languages" },
        onSettings = { calls += "settings" },
        onCompleteSetup = { calls += "setup" },
        onRetrySummary = { calls += "retry" },
    )

    private fun show(state: HomeUiState) {
        compose.setContent { LisdataTheme { HomeScreen(state, actions) } }
    }

    private fun exists(text: String, substring: Boolean = false) =
        compose.onNodeWithText(text, substring = substring, useUnmergedTree = true).assertExists()

    private fun absent(text: String, substring: Boolean = false) =
        compose.onNodeWithText(text, substring = substring, useUnmergedTree = true).assertDoesNotExist()

    private fun scrollTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    private fun click(text: String) {
        scrollTo(text)
        compose.onNodeWithText(text).performClick()
    }

    // US1 (T009)

    @Test
    fun png_matchesPng03() {
        show(HomePreviewData.png)

        listOf(
            "¿Qué quieres explorar hoy?", "PREGUNTA A TU BIBLIOTECA", "Una respuesta.\nTodas tus fuentes.", "Nuevo chat",
            "Importar", "Añadir archivos", "Documentos", "Ver biblioteca",
        ).forEach { exists(it) }
        scrollTo("Preferencias")
        listOf("Índice local", "Estado del RAG", "Idiomas", "Preferencias").forEach { exists(it) }
        compose.onNodeWithContentDescription("Ajustes").assertExists()
        compose.onNodeWithContentDescription("Logotipo de LISDATA").assertExists()
    }

    @Test
    fun everyControl_asksForItsDestination() {
        show(HomePreviewData.png)

        click("Nuevo chat")
        click("Importar")
        click("Documentos")
        click("Índice local")
        click("Idiomas")
        compose.onNodeWithContentDescription("Ajustes").performClick()

        assertThat(calls).containsExactly("chat", "import", "library", "index", "languages", "settings").inOrder()
    }

    @Test
    fun theProvisionalEntries_areGone() {
        show(HomePreviewData.png)

        absent("provisional", substring = true)
    }

    // US2 (T015)

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    fun ready_saysTheLocalAiIsAvailable() {
        show(HomePreviewData.png)

        exists("IA local disponible")
        absent("Configuración pendiente")
        absent("Completar configuración")
    }

    @Test
    fun setupPending_offersToCompleteTheSetup_andEverythingElseStillWorks_FR011() {
        show(HomePreviewData.setupPending)

        exists("Configuración pendiente")
        absent("IA local disponible")
        click("Completar configuración")
        click("Nuevo chat")
        click("Importar")
        click("Idiomas")

        assertThat(calls).containsExactly("setup", "chat", "import", "languages").inOrder()
    }

    @Test
    fun whileChecking_noChip_andTheGreetingStaysWhereItIsWhenItArrives_P2() {
        var state by mutableStateOf(HomePreviewData.opening)
        compose.setContent { LisdataTheme { HomeScreen(state, actions) } }

        // An invisible copy of the chip keeps its room; TalkBack (the merged tree) never reads it.
        compose.onNodeWithText("IA local disponible").assertDoesNotExist()
        absent("Configuración pendiente")
        val greeting = top("¿Qué quieres explorar hoy?")

        state = HomePreviewData.png
        compose.waitForIdle()

        exists("IA local disponible")
        assertThat(top("¿Qué quieres explorar hoy?")).isEqualTo(greeting)
    }

    // US3 (T019)

    @Test
    fun theSummary_showsBothCounts_andReadsAsOneSentence() {
        show(HomePreviewData.png)
        // The figures are read as one sentence, so the labels only exist in the unmerged tree.
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("24 documentos y 12.400 fragmentos"))

        listOf("TU BIBLIOTECA", "24", "documentos", "12.400", "fragmentos").forEach { exists(it) }
        compose.onNodeWithContentDescription("24 documentos y 12.400 fragmentos").assertExists()
    }

    @Test
    fun oneDocumentAndOneChunk_areSingular() {
        show(HomePreviewData.png.copy(summary = SummaryState.Loaded(documents = 1, chunks = 1)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("1 documento y 1 fragmento"))

        exists("documento")
        exists("fragmento")
        compose.onNodeWithContentDescription("1 documento y 1 fragmento").assertExists()
    }

    @Test
    fun anEmptyLibrary_offersTheFirstImport_withoutFigures_P3() {
        show(HomePreviewData.empty)
        scrollTo("Importar primer documento")

        exists("Tu biblioteca está vacía")
        exists("Importa tus PDF, TXT o Markdown para empezar a preguntar.")
        absent("documentos")
        click("Importar primer documento")
        assertThat(calls).containsExactly("import")
    }

    @Test
    fun whileLoading_thereIsASkeleton_andNoFigures_P4() {
        show(HomePreviewData.opening)
        scrollTo("TU BIBLIOTECA")

        compose.onNodeWithContentDescription("Cargando").assertExists()
        absent("documentos")
        absent("fragmentos")
    }

    @Test
    fun anError_offersARetry_andTheQuickActionsStillWork_P4() {
        show(HomePreviewData.summaryFailed)
        scrollTo("Reintentar")
        exists("No se pudo cargar el resumen")
        click("Reintentar")
        click("Importar")

        assertThat(calls).containsExactly("retry", "import").inOrder()
    }

    @Test
    fun anUnavailableLibrary_saysSo() {
        show(HomePreviewData.summaryUnavailable)
        scrollTo("Reintentar")

        exists("La biblioteca no está disponible")
        absent("No se pudo cargar el resumen")
    }

    // US4 (T023)

    @Test
    fun anActiveRun_showsItsCard_betweenTheHeroAndTheQuickActions_P5() {
        show(HomePreviewData.indexing)

        exists("Indexando documentos")
        exists("320 de 860 fragmentos")
        assertThat(top("Indexando documentos")).isGreaterThan(top("Nuevo chat"))
        assertThat(top("Indexando documentos")).isLessThan(top("Importar"))
    }

    @Test
    fun aRunStillChunking_isPreparing() {
        // The summary still loading, so no other «fragmentos» is on screen.
        show(HomePreviewData.indexing.copy(summary = SummaryState.Loading, indexing = IndexingCard(done = 0, total = null, pause = null)))

        exists("Preparando los documentos")
        absent("fragmentos", substring = true)
    }

    @Test
    fun aPausedRun_saysWhy_withTheTextsOfTheIndexScreen() {
        var state by mutableStateOf(HomePreviewData.indexing)
        compose.setContent { LisdataTheme { HomeScreen(state, actions) } }
        val titles = mapOf(
            PauseReason.USER to "En pausa",
            PauseReason.THERMAL to "Pausa por temperatura",
            PauseReason.SYSTEM_LIMIT to "Esperando al sistema",
            PauseReason.NO_SPACE to "Sin espacio",
            PauseReason.MODEL_MISSING to "Falta el modelo de embeddings",
        )

        for ((pause, title) in titles) {
            state = HomePreviewData.indexing.copy(indexing = IndexingCard(done = 320, total = 860, pause = pause))
            compose.waitForIdle()
            exists(title)
            exists("320 de 860 fragmentos")
            absent("Indexando documentos")
        }
    }

    @Test
    fun theCard_opensTheIndex_andReadsAsOneSentence_A4() {
        show(HomePreviewData.indexing)

        // One button that TalkBack reads with its title and figures, and the action it performs.
        val card = compose.onNode(hasClickLabel("Ver estado de la indexación"))
        card.assert(hasText("Indexando documentos") and hasText("320 de 860 fragmentos"))
        card.performClick()
        assertThat(calls).containsExactly("index")
    }

    @Test
    fun withoutARun_thereIsNoCard() {
        show(HomePreviewData.png)

        absent("Indexando documentos")
        absent("Ver estado de la indexación")
    }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("onClickLabel = $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    // US5 (T025): real text measures (Robolectric's default is 1 dp per character).

    private fun showAt(state: HomeUiState, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme { HomeScreen(state, actions) }
            }
        }
    }

    private fun layout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private val tileTexts = listOf(
        "Importar", "Añadir archivos", "Documentos", "Ver biblioteca", "Índice local", "Estado del RAG", "Idiomas", "Preferencias",
    )

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atTheNormalSize_theQuickActionsAreA2x2Grid_andTheFiguresSideBySide() {
        showAt(HomePreviewData.png, fontScale = 1f)

        assertThat(top("Documentos")).isEqualTo(top("Importar"))
        assertThat(top("Idiomas")).isEqualTo(top("Índice local"))
        assertThat(top("12.400")).isEqualTo(top("24"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun from150Percent_theQuickActionsAreOneColumn_andTheFiguresStack_A7() {
        showAt(HomePreviewData.png, fontScale = 1.5f)

        assertThat(top("Documentos")).isGreaterThan(top("Importar"))
        assertThat(top("Índice local")).isGreaterThan(top("Documentos"))
        assertThat(top("12.400")).isGreaterThan(top("24"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun at200Percent_noQuickActionTextIsCut_A7() {
        showAt(HomePreviewData.png, fontScale = 2f)

        for (text in tileTexts) {
            scrollTo(text)
            // The tiles cut with an ellipsis, and a word too long for its line is split: neither may happen.
            val result = layout(text)
            assertWithMessage("$text ellipsized").that((0 until result.lineCount).none { result.isLineEllipsized(it) }).isTrue()
            val splitWords = (0 until result.lineCount - 1).filterNot { line -> text[result.getLineEnd(line) - 1].isWhitespace() }
            assertWithMessage("$text split inside a word at lines $splitWords").that(splitWords).isEmpty()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "es-w844dp-h390dp")
    fun inLandscape_everythingIsReachableByScrolling_A8() {
        show(HomePreviewData.indexing)

        for (text in listOf("Indexando documentos", "Preferencias")) {
            scrollTo(text)
            compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("24 documentos y 12.400 fragmentos"))
        compose.onNodeWithContentDescription("24 documentos y 12.400 fragmentos").assertIsDisplayed()
    }

    @Test
    fun theScrollPosition_survivesRecreation_A8() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LisdataTheme { HomeScreen(HomePreviewData.png, actions) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("24 documentos y 12.400 fragmentos"))
        val scrolled = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertThat(scrolled).isGreaterThan(0f)

        restoration.emulateSavedInstanceStateRestore()

        assertThat(compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
            .isEqualTo(scrolled)
    }
}

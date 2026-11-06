package com.lisdatasolutions.microrag.feature.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F11 T026: PNG 04 and the main path (contracts/chat-ui.md §1 and §3). */
/**
 * What the chat tab loses in landscape besides its own bars, measured on the emulator (T064): the app
 * shell's bottom bar (80 dp) and the system bars (24 dp).
 */
private val BOTTOM_BAR_HEIGHT = 104.dp

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class ChatScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val opened = mutableListOf<SourceTarget>()
    private val copiedTexts = mutableListOf<String>()

    private val actions = ChatActions(
        onBack = { calls += "back" },
        onSend = { calls += "send" },
        onStop = { calls += "stop" },
        onRetry = { calls += "retry $it" },
        onCopy = { id, text ->
            calls += "copy $id"
            copiedTexts += text
        },
        onOpenSource = { opened += it },
        onNewChat = { calls += "new" },
        onOpenHistory = { calls += "history" },
        onCompleteSetup = { calls += "setup" },
        onImport = { calls += "import" },
        onOpenIndex = { calls += "index" },
        onRetryAvailability = { calls += "check" },
        onDelete = { calls += "delete" },
        onConfirmDelete = { calls += "confirm" },
        onDismissDelete = { calls += "dismiss" },
    )

    /** The list shows its end: the latest answer and its sources. */
    private fun assertAtTheEnd() {
        val range = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertThat(range.value()).isWithin(1f).of(range.maxValue())
    }

    /** The question of PNG 04's sample, above the fold since F17 put the scope chip above the composer. */
    private fun scrollToQuestion(description: String = "Tú: ¿Qué indica el protocolo sobre la validación final?") {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description))
    }

    private fun show(state: ChatUiState) {
        compose.setContent { LisdataTheme { ChatScreen(state, actions) } }
    }

    private fun exists(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertExists()

    private fun absent(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()

    /** A source row reads as one sentence: «Fuente N: documento, Página P, Fragmento M». */
    private fun row(sentence: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(sentence))
        return compose.onNodeWithContentDescription(sentence)
    }

    /** Chip [n] of the line that starts with [line]: a link inside its text. */
    private fun chip(line: String) =
        compose.onNode(hasClickAction() and hasParent(hasText(line, substring = true)), useUnmergedTree = true)

    @Test
    fun png_matchesPng04() {
        show(ChatPreviewData.png)
        // F17 K7: the scope chip above the composer leaves the question just above the fold.
        scrollToQuestion()

        listOf(
            "Chat con documentos", "Gemma 4 · Procesamiento local", "¿Qué indica el protocolo sobre la validación final?",
            "El protocolo establece tres comprobaciones antes del cierre:", "Copiar respuesta", "2 fuentes", "FUENTES CONSULTADAS",
            "Pregunta a tus documentos…",
        ).forEach { exists(it) }
        listOf("Verificar la documentación", "Confirmar las aprobaciones", "Registrar la validación final").forEach {
            compose.onNodeWithText(it, substring = true, useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithContentDescription("Más opciones").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tú: ¿Qué indica el protocolo sobre la validación final?").assertExists()
        row("Fuente 1: Manual de procesos.pdf, Página 12, Fragmento 86").assertIsDisplayed()
        row("Fuente 2: Protocolo interno.pdf, Página 8, Fragmento 41").assertIsDisplayed()
        // Nothing typed: send is off.
        compose.onNodeWithContentDescription("Enviar").assertIsNotEnabled()
    }

    @Test
    fun theMoreMenu_offersANewChat_andDeletingASavedConversation() {
        show(ChatPreviewData.png)

        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Nuevo chat").assertIsDisplayed()
        compose.onNodeWithText("Borrar conversación").performClick()

        assertThat(calls).containsExactly("delete")
    }

    @Test
    fun aNewConversation_offersANewChat_andTheHistory() {
        show(ChatPreviewData.empty)

        compose.onNodeWithContentDescription("Más opciones").performClick()

        compose.onNodeWithText("Nuevo chat").assertIsDisplayed()
        absent("Borrar conversación")
        compose.onNodeWithText("Historial").performClick()
        assertThat(calls).containsExactly("history")
    }

    /** F13 H1 (G13): «Nuevo chat», «Historial» and «Borrar conversación», in that order. */
    @Test
    fun theMoreMenu_listsHistoryBetweenNewChatAndDelete() {
        show(ChatPreviewData.png)
        compose.onNodeWithContentDescription("Más opciones").performClick()

        val tops = listOf("Nuevo chat", "Historial", "Borrar conversación").map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().top }
        assertThat(tops).isInStrictOrder()
    }

    @Test
    fun theSourcesChip_scrollsToTheFirstRow() {
        val turn = ChatPreviewData.completedTurn
        val long = AnswerContent(List(6) { turn.content.blocks }.flatten())
        show(ChatPreviewData.png.copy(turns = listOf(turn.copy(content = long))))
        val first = compose.onNodeWithContentDescription("Fuente 1: Manual de procesos.pdf, Página 12, Fragmento 86")
        // From the question, just enough to see the chip at the end of the card: the sources stay below.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("2 fuentes").performScrollTo()
        assertThat(runCatching { first.assertIsDisplayed() }.isSuccess).isFalse()

        compose.onNodeWithText("2 fuentes").performClick()
        compose.waitForIdle()

        first.assertIsDisplayed()
    }

    @Test
    fun aChipAndItsRow_openTheSameSource() {
        show(ChatPreviewData.png)

        chip("Verificar la documentación").performClick()
        chip("Confirmar las aprobaciones").performClick()
        row("Fuente 1: Manual de procesos.pdf, Página 12, Fragmento 86").performClick()

        assertThat(opened).containsExactly(ChatPreviewData.manual.target, ChatPreviewData.protocol.target, ChatPreviewData.manual.target).inOrder()
    }

    @Test
    fun copying_sendsTheTurn() {
        show(ChatPreviewData.png)

        compose.onNodeWithText("Copiar respuesta").performClick()

        assertThat(calls).containsExactly("copy 1")
    }

    @Test
    fun whileSearching_theStatusShows_copyIsOff_noChipOrSources_andSendTurnsIntoStop() {
        val state = ChatPreviewData.searching
        show(state.copy(turns = listOf(state.turns.last())))

        exists("Buscando en documentos…")
        compose.onNodeWithText("Copiar respuesta").assertIsNotEnabled()
        listOf("2 fuentes", "FUENTES CONSULTADAS").forEach { absent(it) }
        compose.onNodeWithContentDescription("Detener").performClick()
        assertThat(calls).containsExactly("stop")
    }

    @Test
    fun whileGenerating_theTextShowsWithItsChips_butNoChipOrSources() {
        val state = ChatPreviewData.generating
        show(state.copy(turns = listOf(state.turns.last())))

        exists("Generando respuesta…")
        compose.onNodeWithText("Lo firma la persona responsable", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Copiar respuesta").assertIsNotEnabled()
        listOf("1 fuente", "FUENTES CONSULTADAS").forEach { absent(it) }
        chip("Lo firma la persona responsable").performClick()
        assertThat(opened).containsExactly(ChatPreviewData.manual.target)
    }

    // US2 (T040): the refusal and an answer without citations (C1, C2).

    @Test
    fun theRefusal_showsTheSentence_withoutChipOrSources_andCopiesIt() {
        show(ChatPreviewData.refusal)

        exists("No encuentro información suficiente en tus documentos")
        listOf("1 fuente", "2 fuentes", "FUENTES CONSULTADAS").forEach { absent(it) }
        compose.onNodeWithText("Copiar respuesta").performClick()
        assertThat(copiedTexts).containsExactly("No encuentro información suficiente en tus documentos")
    }

    @Test
    fun anAnswerWithoutCitations_showsTheC2Note() {
        val turn = ChatPreviewData.completedTurn.copy(
            content = AnswerContent.plain("El plazo es de 30 días."), sources = emptyList(), chipTargets = emptyList(),
            notes = listOf(R.string.chat_no_citations),
        )
        show(ChatPreviewData.png.copy(turns = listOf(turn)))

        exists("Esta respuesta no cita ninguna fuente de tus documentos.")
        absent("FUENTES CONSULTADAS")
    }

    @Test
    fun aBlankQuestion_keepsSendOff() {
        show(ChatPreviewData.png.copy(question = "   "))

        compose.onNodeWithContentDescription("Enviar").assertIsNotEnabled()
    }

    // US3 (T043): stop, failures, retry and copy (C7, C8, C10, U10).

    private fun failed(failureText: Int?, canRetry: Boolean = true) = ChatPreviewData.png.copy(
        turns = listOf(
            ChatPreviewData.completedTurn.copy(
                state = AnswerState.Failed, content = AnswerContent.plain("El protocolo establece"), sources = emptyList(),
                failureText = failureText, retryable = true, canRetry = canRetry,
            ),
        ),
    )

    @Test
    fun withoutAnAnswerRunning_theComposerSends() {
        show(ChatPreviewData.png.copy(question = "¿Y para empresas?"))

        compose.onNodeWithContentDescription("Detener").assertDoesNotExist()
        compose.onNodeWithContentDescription("Enviar").performClick()
        assertThat(calls).containsExactly("send")
    }

    @Test
    fun aCancelledAnswer_keepsItsText_andCanBeCopied() {
        show(ChatPreviewData.cancelled)

        exists("Generación cancelada")
        exists("El protocolo establece tres")
        compose.onNodeWithText("Copiar respuesta").performClick()
        assertThat(copiedTexts).containsExactly("El protocolo establece tres")
    }

    @Test
    fun aGenerationFailure_C7_offersRetry() {
        show(failed(failureText = null))
        exists("No se pudo generar la respuesta")
        compose.onNodeWithText("Copiar respuesta").assertIsNotEnabled()
        compose.onNodeWithText("Reintentar").performClick()
        assertThat(calls).containsExactly("retry 1")
    }

    @Test
    fun anIndexFailure_C8_saysSo() {
        show(failed(failureText = R.string.chat_index_failed))

        exists("No se puede abrir el índice de documentos")
        compose.onNodeWithText("Reintentar").assertIsEnabled()
    }

    @Test
    fun retry_isOff_whenItCannotStart() {
        show(failed(failureText = null, canRetry = false))

        compose.onNodeWithText("Reintentar").assertIsNotEnabled()
    }

    @Test
    fun anInterruptedAnswer_offersRetry_andKeepsItsSources_C13() {
        val state = ChatPreviewData.interrupted
        show(state.copy(turns = listOf(state.turns.single().copy(sources = ChatPreviewData.completedTurn.sources))))

        exists("La respuesta se interrumpió")
        exists("Reintentar")
        exists("2 fuentes")
    }

    @Test
    fun copying_givesTheTextWithItsCitations_andTheSources_C10() {
        show(ChatPreviewData.png)

        compose.onNodeWithText("Copiar respuesta").performClick()

        assertThat(copiedTexts.single()).isEqualTo(
            """
            El protocolo establece tres comprobaciones antes del cierre:
            • Verificar la documentación [1]
            • Confirmar las aprobaciones [2]
            • Registrar la validación final [2]

            [1] Manual de procesos.pdf, página 12
            [2] Protocolo interno.pdf, página 8
            """.trimIndent(),
        )
    }

    @Test
    fun upToAndroid12_theFooterSaysCopied_andTalkBackReadsIt_U10() {
        show(ChatPreviewData.png.copy(copiedTurnId = 1, announcement = ChatAnnouncement.COPIED))

        exists("Respuesta copiada")
        absent("Copiar respuesta")
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Respuesta copiada") and
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        ).assertExists()
    }

    // US5 (T050): «⋯», the delete dialog, sources and storage (C11, C15–C17).

    @Test
    fun theDeleteDialog_C15_focusesCancel_andConfirms() {
        show(ChatPreviewData.deleteDialog)

        exists("¿Borrar esta conversación?")
        exists("Se eliminarán sus preguntas y respuestas de este teléfono.")
        compose.onNodeWithText("Cancelar").assertIsFocused()
        compose.onNodeWithText("Borrar").performClick()
        assertThat(calls).containsExactly("confirm")
    }

    @Test
    fun aSourceNoLongerAvailable_isInert() {
        show(ChatPreviewData.sourceGone)

        row("Fuente 2: Protocolo interno.pdf, Fuente ya no disponible").performClick()
        row("Fuente 1: Manual de procesos.pdf, Página 12, Fragmento 86").performClick()

        assertThat(opened).containsExactly(ChatPreviewData.manual.target)
    }

    @Test
    fun aBlockOfSeveralFragments_givesItsRange_C11() {
        val manual = ChatPreviewData.manual.copy(citation = ChatPreviewData.manual.citation.copy(lastFragment = 87))
        show(ChatPreviewData.png.copy(turns = listOf(ChatPreviewData.completedTurn.copy(sources = listOf(manual)))))

        row("Fuente 1: Manual de procesos.pdf, Página 12, Fragmentos 86–87").assertIsDisplayed()
    }

    @Test
    fun anAnswerThatCouldNotBeSaved_carriesItsNote_C16() {
        val turn = ChatPreviewData.completedTurn.copy(id = 0, sources = emptyList(), notes = listOf(R.string.chat_save_failed))
        show(ChatPreviewData.png.copy(conversationId = null, turns = listOf(turn)))

        exists("No se ha podido guardar esta respuesta en el teléfono.")
    }

    @Test
    fun unreadableConversations_sayWhy_C17() {
        show(ChatPreviewData.storageUnavailable)

        exists("No se pueden abrir tus conversaciones guardadas.")
        compose.onNodeWithContentDescription("Enviar").assertIsNotEnabled()
    }

    // US6 (T054): the availability with and without turns (contracts/chat-ui.md §2).

    /** What each availability says and the call its action makes; null when it has no action. */
    private val notices = listOf(
        Triple(ChatAvailability.ModelsNotReady, "Configuración pendiente", "Completar configuración" to "setup"),
        Triple(ChatAvailability.IndexUnavailable, "No se puede abrir el índice de documentos", "Reintentar" to "check"),
        Triple(ChatAvailability.EmptyLibrary, "Aún no hay documentos", "Importar documentos" to "import"),
        Triple(ChatAvailability.WaitingForIndexing, "Se están indexando tus documentos", "Ver indexación" to "index"),
        Triple(ChatAvailability.NothingReady, "Aún no hay documentos listos para preguntar", "Ver indexación" to "index"),
        Triple(ChatAvailability.StorageUnavailable, "No se pueden abrir tus conversaciones guardadas.", null),
    )

    private fun eachAvailability(base: ChatUiState, check: (ChatAvailability) -> Unit) {
        var state by mutableStateOf(base)
        compose.setContent { LisdataTheme { ChatScreen(state, actions) } }
        notices.forEach { (availability, title, action) ->
            calls.clear()
            state = base.copy(availability = availability, question = "¿Plazo?")
            compose.waitForIdle()

            exists(title)
            compose.onNodeWithContentDescription("Enviar").assertIsNotEnabled()
            if (action != null) {
                // Without turns the state scrolls; with turns the notice sits above the composer.
                val button = compose.onNodeWithText(action.first)
                if (base.turns.isEmpty()) button.performScrollTo()
                button.performClick()
                assertThat(calls).containsExactly(action.second)
            } else {
                absent("Reintentar")
            }
            check(availability)
        }
    }

    @Test
    fun withoutTurns_eachAvailabilityFillsTheScreen_withItsAction() {
        eachAvailability(ChatPreviewData.empty) { }
    }

    @Test
    fun withTurns_eachAvailabilityIsANotice_andTheConversationStays() {
        eachAvailability(ChatPreviewData.png) { availability ->
            if (availability != ChatAvailability.StorageUnavailable) {
                scrollToQuestion()
                compose.onNodeWithContentDescription("Tú: ¿Qué indica el protocolo sobre la validación final?").assertExists()
            }
        }
    }

    @Test
    fun cpuMode_saysItInTheSubtitle_C9() {
        show(ChatPreviewData.cpuMode)

        exists("Gemma 4 · Modo CPU, más lento")
        absent("Gemma 4 · Procesamiento local")
    }

    @Test
    fun partialCoverage_isNotedUnderTheAnswer_C6() {
        show(ChatPreviewData.png.copy(turns = listOf(ChatPreviewData.completedTurn.copy(notes = listOf(R.string.chat_partial_coverage)))))

        exists("Algunos documentos aún no están indexados: esta respuesta solo usa los que ya están listos.")
    }

    @Test
    fun whenReady_theComposerSends() {
        show(ChatPreviewData.png.copy(question = "¿Plazo?"))

        compose.onNodeWithContentDescription("Enviar").assertIsEnabled()
    }

    // US7 (T057): sentences, announcements, 200 % in landscape, scroll, keyboard and English (U2–U7).

    @Test
    fun theCard_readsAsMicroRAG_thenTheAnswer_notAsCapitals_U3() {
        show(ChatPreviewData.png)

        compose.onNodeWithContentDescription("MicroRAG").assertExists()
        // TalkBack reads the merged tree, where the capitals are gone.
        compose.onNodeWithText("MICRORAG").assertDoesNotExist()
        exists("El protocolo establece tres comprobaciones antes del cierre:")
    }

    @Test
    fun eachChangeHasItsAnnouncement_andTheGrowingTextIsNeverALiveRegion_U2() {
        var state by mutableStateOf(ChatPreviewData.generating)
        compose.setContent { LisdataTheme { ChatScreen(state, actions) } }
        mapOf(
            ChatAnnouncement.SEARCHING to "Buscando en documentos",
            ChatAnnouncement.GENERATING to "Generando respuesta",
            ChatAnnouncement.COMPLETED to "Respuesta completa",
            ChatAnnouncement.CANCELLED to "Generación cancelada",
            ChatAnnouncement.INTERRUPTED to "La respuesta se interrumpió",
            ChatAnnouncement.FAILED to "No se pudo generar la respuesta",
            ChatAnnouncement.DELETED to "Conversación borrada",
        ).forEach { (announcement, text) ->
            state = ChatPreviewData.generating.copy(announcement = announcement)
            compose.waitForIdle()
            compose.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text) and
                    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            ).assertExists()
        }
        // The only live region is the announcement: the answer is read when the user moves to it.
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true).assertCountEquals(1)
    }

    private fun showAt(state: ChatUiState, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme { ChatScreen(state, actions) }
            }
        }
    }

    private fun layout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    /** Neither cut with an ellipsis nor split inside a word. */
    private fun assertWhole(text: String) {
        val result = layout(text)
        assertWithMessage("$text ellipsized").that((0 until result.lineCount).none { result.isLineEllipsized(it) }).isTrue()
        val split = (0 until result.lineCount - 1).filterNot { line -> text[result.getLineEnd(line) - 1].isWhitespace() }
        assertWithMessage("$text split inside a word at lines $split").that(split).isEmpty()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "es-w844dp-h390dp")
    fun at200PercentInLandscape_nothingIsCut_andEverythingIsReachable_U5() {
        showAt(ChatPreviewData.png, fontScale = 2f)

        listOf("Chat con documentos", "Gemma 4 · Procesamiento local").forEach { assertWhole(it) }
        listOf(
            "¿Qué indica el protocolo sobre la validación final?", "El protocolo establece tres comprobaciones antes del cierre:",
            "Copiar respuesta", "2 fuentes", "FUENTES CONSULTADAS",
        ).forEach { text ->
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
            assertWhole(text)
        }
        row("Fuente 2: Protocolo interno.pdf, Página 8, Fragmento 41").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enviar").assertIsDisplayed()
    }

    @Test
    fun theScrollPosition_survivesRecreation_U7() {
        val turns = List(4) { i -> ChatPreviewData.completedTurn.copy(id = i + 1L, question = "Pregunta ${i + 1}") }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LisdataTheme { ChatScreen(ChatPreviewData.png.copy(turns = turns), actions) } }
        // Opened at the latest answer (its question above the scope chip's fold); the user goes back up.
        assertAtTheEnd()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Tú: Pregunta 2"))

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Tú: Pregunta 2").assertIsDisplayed()
    }

    /** With the keyboard open the screen gets less room (imePadding): the composer stays on screen (U6). */
    @Test
    fun withLessRoom_theComposerStaysOnScreen_U6() {
        compose.setContent { LisdataTheme { Box(Modifier.height(320.dp)) { ChatScreen(ChatPreviewData.typing, actions) } } }

        compose.onNodeWithContentDescription("Enviar").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("¿Y quién firma el cierre?", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w390dp-h844dp")
    fun inEnglish_theScreenSpeaksEnglish() {
        show(ChatPreviewData.png)
        scrollToQuestion("You: ¿Qué indica el protocolo sobre la validación final?")

        listOf("Chat with documents", "Gemma 4 · On-device processing", "Copy answer", "2 sources", "SOURCES CONSULTED", "Ask your documents…")
            .forEach { exists(it) }
        compose.onNodeWithContentDescription("More options").assertExists()
        compose.onNodeWithContentDescription("You: ¿Qué indica el protocolo sobre la validación final?").assertExists()
        row("Source 1: Manual de procesos.pdf, Page 12, Chunk 86").assertIsDisplayed()
        listOf("Chat con documentos", "Copiar respuesta", "FUENTES CONSULTADAS").forEach { absent(it) }
    }

    // T064: in landscape, under the app shell's bottom bar, the chat leaves little height between its bars.

    /** The chat over a spacer as tall as the shell's bottom bar and the system bars. */
    private fun showOverTheBottomBar(state: ChatUiState) {
        compose.setContent {
            LisdataTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { ChatScreen(state, actions) }
                    Spacer(Modifier.height(BOTTOM_BAR_HEIGHT))
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "es-w844dp-h390dp")
    fun inLandscape_aStateWithoutTurns_showsItsTitle_andItsActionIsReachable() {
        showOverTheBottomBar(ChatPreviewData.modelsNotReady)

        compose.onNodeWithText("Configuración pendiente").assertIsDisplayed()
        compose.onNodeWithText("Completar configuración").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "es-w844dp-h390dp")
    fun inLandscape_withTurnsAndANotice_theConversationStillShows() {
        showOverTheBottomBar(ChatPreviewData.waitingWithTurns)

        // The notice is the list's last item, opened at its title; the conversation is above it.
        compose.onNodeWithText("Se están indexando tus documentos").assertIsDisplayed()
        val list = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        assertThat(list.height).isAtLeast(with(compose.density) { 48.dp.toPx() })
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Tú: ¿Qué indica el protocolo sobre la validación final?"))
        compose.onNodeWithContentDescription("Tú: ¿Qué indica el protocolo sobre la validación final?").assertIsDisplayed()
    }

    // --- F13 (research R7, R8) ------------------------------------------------------------------------

    /** Q12: the chip of a deleted document is no link: it reads its number and opens nothing. */
    @Test
    fun aDeletedDocumentsChip_isNoLink() {
        val protocolGone = ChatPreviewData.protocol.copy(citation = ChatPreviewData.protocol.citation.copy(available = false))
        show(
            ChatPreviewData.png.copy(
                turns = listOf(
                    ChatPreviewData.completedTurn.copy(
                        sources = listOf(ChatPreviewData.manual, protocolGone),
                        chipTargets = listOf(ChatPreviewData.manual.target, null),
                    ),
                ),
            ),
        )

        chip("Verificar la documentación").assertExists()
        chip("Confirmar las aprobaciones").assertDoesNotExist()
        chip("Registrar la validación final").assertDoesNotExist()
        compose.onNodeWithText("Confirmar las aprobaciones", substring = true, useUnmergedTree = true).assertExists()
        row("Fuente 2: Protocolo interno.pdf, Fuente ya no disponible").assertIsDisplayed()
    }

    /** R8: a conversation opened from Historial starts at its latest answer, wherever the previous one was. */
    @Test
    fun anotherConversation_startsAtItsLatestAnswer() {
        fun turns(prefix: String) = (1..6).map { i -> ChatPreviewData.completedTurn.copy(id = i.toLong() + if (prefix == "B") 100 else 0, question = "Pregunta $prefix$i") }
        var state by mutableStateOf(ChatPreviewData.png.copy(conversationId = 1, turns = turns("A")))
        compose.setContent { LisdataTheme { ChatScreen(state, actions) } }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Tú: Pregunta A1").assertIsDisplayed()

        state = ChatPreviewData.png.copy(conversationId = 2, turns = turns("B"))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Tú: Pregunta B1").assertIsNotDisplayed()
        compose.onNodeWithText("Copiar respuesta", useUnmergedTree = true).assertExists()
        // At B's latest answer: since F17's scope chip, its question is above the fold.
        assertAtTheEnd()
    }

    // F17 T035 and T044 (K7, K9; FR-014).

    @Test
    fun theChipSummarisesTheScope() {
        val shown = androidx.compose.runtime.mutableStateOf(ChatUiState(availability = ChatAvailability.Ready(false, false)))
        val changes = mutableListOf<String>()
        compose.setContent { LisdataTheme { ChatScreen(shown.value, actions.copy(onChangeScope = { changes += "scope" })) } }
        val cases = listOf(
            ScopeSummary(whole = true) to "Buscar en: Toda la biblioteca",
            ScopeSummary(whole = false, folderNames = listOf("Legal")) to "Buscar en: Legal",
            ScopeSummary(whole = false, folderNames = listOf("Legal", "Motores")) to "Buscar en: Legal y Motores",
            ScopeSummary(whole = false, folderNames = listOf("Legal", null)) to "Buscar en: Legal y General",
            ScopeSummary(whole = false, folderNames = listOf("Legal"), looseDocuments = 2) to "Buscar en: Legal + 2 documentos",
            ScopeSummary(whole = false, looseDocuments = 3) to "Buscar en: 3 documentos",
            ScopeSummary(whole = false, looseDocuments = 1) to "Buscar en: 1 documento",
        )
        cases.forEach { (summary, text) ->
            shown.value = shown.value.copy(scope = summary)
            compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
        }
        // U2: one button, «Buscar en: …», whose action is «Cambiar ámbito».
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Buscar en: 1 documento")).performClick()
        assertThat(changes).containsExactly("scope")
    }

    @Test
    fun aScopeWithNothingReady_offersToChangeIt() {
        val changes = mutableListOf<String>()
        compose.setContent {
            LisdataTheme {
                ChatScreen(
                    ChatUiState(availability = ChatAvailability.NothingInScope, scope = ScopeSummary(whole = false, folderNames = listOf("Legal"))),
                    actions.copy(onChangeScope = { changes += "scope" }),
                )
            }
        }

        compose.onNodeWithText("En este ámbito no hay documentos listos para buscar").assertExists()
        compose.onNodeWithText("Cambiar ámbito").performClick()
        assertThat(changes).containsExactly("scope")
    }

    // F20 T018 (contract U4–U8, wireframe §4 and §10).

    private val withPages = ChatPreviewData.png.copy(
        turns = listOf(
            ChatPreviewData.completedTurn.copy(
                sources = listOf(ChatPreviewData.manual, ChatPreviewData.reportPage, ChatPreviewData.poster),
                chipTargets = listOf(ChatPreviewData.manual.target, ChatPreviewData.reportPage.target, ChatPreviewData.poster.target),
            ),
        ),
    )

    @Test
    fun aPageSource_saysItsPageAndThatItIsAnImage_andALooseImageSaysImage() {
        show(withPages)

        row("Fuente 2: informe-ine.pdf, Página 4, Imagen de la página").assertExists()
        row("Fuente 3: cartel.jpg, Imagen").assertExists()
        exists("Página 4 · Imagen de la página")
        exists("Imagen")
        absent("Página 1")
        // A fragment source is today's.
        row("Fuente 1: Manual de procesos.pdf, Página 12, Fragmento 86").assertExists()
    }

    @Test
    fun aPageSource_opensItsPage_fromTheRowAndFromItsChip() {
        show(withPages)

        row("Fuente 2: informe-ine.pdf, Página 4, Imagen de la página").performClick()

        assertThat(opened).containsExactly(SourceTarget(documentId = 9, chunkId = 0, page = 4))
    }

    @Test
    fun theThumbnailIsLoadedForPages_only() {
        val asked = mutableListOf<SourceTarget>()
        compose.setContent {
            LisdataTheme {
                // Null, as before the decode: Robolectric's legacy graphics cannot make bitmaps here.
                ChatScreen(withPages, actions.copy(loadThumbnail = { asked += it; null }))
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Fuente 3: cartel.jpg, Imagen"))
        compose.waitForIdle()

        assertThat(asked).containsExactly(ChatPreviewData.reportPage.target, ChatPreviewData.poster.target)
    }

    @Test
    fun copyingAnAnswer_withAPageAndAnImage() {
        show(withPages)
        scrollToQuestion()

        compose.onNodeWithText("Copiar respuesta").performClick()

        assertThat(copiedTexts.single()).contains("[2] informe-ine.pdf, página 4")
        assertThat(copiedTexts.single()).contains("[3] cartel.jpg")
        assertThat(copiedTexts.single()).doesNotContain("cartel.jpg, página")
    }

    // F20 T026 (contract U3): «Leyendo 1 página · unos 8,5 s…», with the locale's decimal mark.

    private val readingPage = ChatPreviewData.png.copy(
        turns = listOf(
            ChatPreviewData.completedTurn.copy(
                state = AnswerState.Streaming, status = TurnStatus.READING_PAGE, content = AnswerContent.Empty,
                sources = emptyList(), chipTargets = emptyList(), pageWaitTenths = 85,
            ),
        ),
    )

    @Test
    fun readingAPage_saysItWithItsWait() {
        show(readingPage)

        exists("Leyendo 1 página · unos 8,5 s…")
        absent("Generando respuesta…")
    }

    @Test
    @Config(qualifiers = "en")
    fun readingAPage_inEnglish() {
        show(readingPage)

        exists("Reading 1 page · about 8.5 s…")
    }

    // F20 T044 (contract U5): a page of a deleted document is inert and grey, without its thumbnail.

    @Test
    fun aPageOfADeletedDocument_isUnavailable_andAsksForNoThumbnail() {
        val gone = ChatPreviewData.reportPage.copy(citation = ChatPreviewData.reportPage.citation.copy(available = false))
        val asked = mutableListOf<SourceTarget>()
        compose.setContent {
            LisdataTheme {
                ChatScreen(
                    withPages.copy(turns = listOf(withPages.turns.single().copy(sources = listOf(gone), chipTargets = listOf(null)))),
                    actions.copy(loadThumbnail = { asked += it; null }),
                )
            }
        }

        row("Fuente 2: informe-ine.pdf, Fuente ya no disponible").assertExists().assertHasNoClickAction()
        assertThat(asked).isEmpty()
    }
}

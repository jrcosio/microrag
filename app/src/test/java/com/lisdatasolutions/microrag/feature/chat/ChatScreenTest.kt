package com.lisdatasolutions.microrag.feature.chat

import android.app.Application
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F11 T026: PNG 04 and the main path (contracts/chat-ui.md §1 and §3). */
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
        onDelete = { calls += "delete" },
        onConfirmDelete = { calls += "confirm" },
        onDismissDelete = { calls += "dismiss" },
    )

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

        listOf(
            "Chat con documentos", "Gemma 4 · Procesamiento local", "¿Qué indica el protocolo sobre la validación final?", "MICRORAG",
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
    fun aNewConversation_offersOnlyANewChat() {
        show(ChatPreviewData.empty)

        compose.onNodeWithContentDescription("Más opciones").performClick()

        compose.onNodeWithText("Nuevo chat").assertIsDisplayed()
        absent("Borrar conversación")
    }

    @Test
    fun theSourcesChip_scrollsToTheFirstRow() {
        val turn = ChatPreviewData.completedTurn
        val long = AnswerContent(List(6) { turn.content.blocks }.flatten())
        show(ChatPreviewData.png.copy(turns = listOf(turn.copy(content = long))))
        val first = compose.onNodeWithContentDescription("Fuente 1: Manual de procesos.pdf, Página 12, Fragmento 86")
        // Just enough to see the chip, at the end of the card: the sources stay below.
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
}

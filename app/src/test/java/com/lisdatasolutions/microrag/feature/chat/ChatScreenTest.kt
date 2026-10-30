package com.lisdatasolutions.microrag.feature.chat

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
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

    private val actions = ChatActions(
        onBack = { calls += "back" },
        onSend = { calls += "send" },
        onStop = { calls += "stop" },
        onCopy = { id, _ -> calls += "copy $id" },
        onOpenSource = { opened += it },
        onNewChat = { calls += "new" },
        onDelete = { calls += "delete" },
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
    fun whileSearching_theStatusShows_withoutCopyChipOrSources_andSendTurnsIntoStop() {
        val state = ChatPreviewData.searching
        show(state.copy(turns = listOf(state.turns.last())))

        exists("Buscando en documentos…")
        listOf("Copiar respuesta", "2 fuentes", "FUENTES CONSULTADAS").forEach { absent(it) }
        compose.onNodeWithContentDescription("Detener").performClick()
        assertThat(calls).containsExactly("stop")
    }

    @Test
    fun whileGenerating_theTextShowsWithItsChips_butNoChipOrSources() {
        val state = ChatPreviewData.generating
        show(state.copy(turns = listOf(state.turns.last())))

        exists("Generando respuesta…")
        compose.onNodeWithText("Lo firma la persona responsable", substring = true, useUnmergedTree = true).assertExists()
        listOf("Copiar respuesta", "1 fuente", "FUENTES CONSULTADAS").forEach { absent(it) }
        chip("Lo firma la persona responsable").performClick()
        assertThat(opened).containsExactly(ChatPreviewData.manual.target)
    }
}

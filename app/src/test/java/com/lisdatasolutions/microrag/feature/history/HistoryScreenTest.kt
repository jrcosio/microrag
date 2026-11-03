package com.lisdatasolutions.microrag.feature.history

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationsUseCase
import com.lisdatasolutions.microrag.testing.FakeChatSession
import com.lisdatasolutions.microrag.testing.InMemoryChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F13 T015 (contracts/history-ui.md §1, §2, U2–U6): Historial on Robolectric, in Spanish. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = HistoryActions(
        onBack = { calls += "back" },
        onNewChat = { calls += "new" },
        onOpen = { calls += "open:$it" },
        onDelete = { calls += "delete:$it" },
        onConfirmDelete = { calls += "confirm" },
        onDismissDelete = { calls += "dismiss" },
        onLoadMore = { calls += "more" },
    )

    private fun show(state: HistoryUiState, fontScale: Float = 1f) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            LisdataTheme { HistoryScreen(state, actions) }
        }
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun loading_showsFourSkeletonRows() {
        show(HistoryPreviewData.loading)

        compose.onAllNodesWithContentDescription("Cargando").assertCountEquals(4)
    }

    @Test
    fun empty_offersANewChat() {
        show(HistoryPreviewData.empty)

        compose.onNodeWithText("Aún no hay conversaciones").assertIsDisplayed()
        compose.onNodeWithText("Las conversaciones que tengas en el chat aparecerán aquí.").assertIsDisplayed()
        compose.onNodeWithText("Nuevo chat").performClick()
        compose.onNodeWithContentDescription("Nuevo chat").performClick()
        assertThat(calls).containsExactly("new", "new")
    }

    /** H5 (D-065): the C17 text and no button. */
    @Test
    fun error_hasTheC17Text_andNoButtons() {
        show(HistoryPreviewData.error)

        compose.onNodeWithText("No se pueden abrir tus conversaciones guardadas.").assertIsDisplayed()
        compose.onAllNodesWithText("Reintentar").assertCountEquals(0)
    }

    @Test
    fun content_groupsAreHeadings_andEachRowIsOneSentence() {
        show(HistoryPreviewData.content)

        for (header in listOf("HOY", "AYER", "ÚLTIMOS 7 DÍAS")) compose.onNodeWithText(header).assert(isHeading)
        val open = compose.onNodeWithContentDescription("¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas. Abierta.")
        open.assert(SemanticsMatcher("opens with its label") { it.config[SemanticsActions.OnClick].label == "Abrir conversación" })
        compose.onNodeWithContentDescription("Resume las condiciones de garantía del manual. Hoy, 8:15. 1 pregunta.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Abierta", substring = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Abierta", substring = true).assertCountEquals(1)

        open.performClick()
        compose.onNodeWithContentDescription("Borrar conversación: ¿Cuál es el plazo de devolución?").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        assertThat(calls).containsExactly("open:1", "delete:1", "back").inOrder()
    }

    @Test
    fun theOlderGroups_scrollIntoView() {
        show(HistoryPreviewData.content)

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("12 de octubre de 2026", substring = true))
        compose.onNodeWithText("OCTUBRE DE 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("¿Cuántos días de vacaciones corresponden? 12 de octubre de 2026. 5 preguntas.").assertIsDisplayed()
    }

    @Test
    fun withTheFontAt200_theBinsKeep48dp() {
        show(HistoryPreviewData.content, fontScale = 2f)

        val bin = compose.onNodeWithContentDescription("Borrar conversación: ¿Cuál es el plazo de devolución?").getUnclippedBoundsInRoot()
        assertThat(bin.right - bin.left).isAtLeast(48.dp)
        assertThat(bin.bottom - bin.top).isAtLeast(48.dp)
    }

    @Test
    @Config(qualifiers = "es-w840dp-h390dp")
    fun inLandscape_theEmptyStateFits_withItsButton() {
        show(HistoryPreviewData.empty)

        compose.onNodeWithText("Nuevo chat").assertIsDisplayed()
    }

    @Test
    fun nearTheEnd_theNextBlockIsAskedFor() {
        val more = HistoryPreviewData.content.let {
            it.copy(content = (it.content as HistoryScreenContent.Sections).copy(hasMore = true))
        }
        show(more)
        compose.waitForIdle()

        assertThat(calls).contains("more")
    }

    // --- US3: deleting (H6, U5, U8) --------------------------------------------------------------------

    /** Legacy graphics, as the chat's dialog test: focus in a dialog window is not followed in native mode. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun theDialog_hasTheChatTexts_andTheLineAboutTheAnswer_onlyWhenItStops() {
        var state by mutableStateOf(HistoryPreviewData.content.copy(confirmDeleteId = 2))
        compose.setContent { LisdataTheme { HistoryScreen(state, actions) } }

        compose.onNodeWithText("¿Borrar esta conversación?").assertIsDisplayed()
        compose.onNodeWithText("Se eliminarán sus preguntas y respuestas de este teléfono.").assertIsDisplayed()
        compose.onNodeWithText("La respuesta en curso se detendrá.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Cancelar").assertIsFocused()

        state = HistoryPreviewData.confirming
        compose.waitForIdle()
        compose.onNodeWithText("La respuesta en curso se detendrá.", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertThat(calls).containsExactly("confirm", "dismiss").inOrder()
    }

    /** Through the route and its ViewModel, as the app: the snackbars, once each, and one dialog per double tap. */
    @Test
    fun deletingThroughTheRoute_saysHowItWent() {
        val repository = InMemoryChatRepository()
        val session = FakeChatSession(repository)
        val ids = runBlocking {
            listOf("¿Cuál es el plazo de devolución?", "¿Horario?").map { question ->
                repository.now = 5_000
                session.ask(null, question).also { session.active.value = null }
            }
        }
        val vm = HistoryViewModel(
            savedState = SavedStateHandle(mapOf("openConversationId" to null)),
            observeConversations = ObserveConversationsUseCase(repository),
            deleteConversation = DeleteConversationUseCase(session),
            observeAnswerInProgress = ObserveAnswerInProgressUseCase(session),
            backgroundDispatcher = Dispatchers.Unconfined,
        )
        compose.setContent { LisdataTheme { HistoryRoute(onBack = {}, onOpenConversation = {}, onNewChat = {}, viewModel = vm) } }

        compose.onNodeWithContentDescription("Borrar conversación: ¿Horario?").performTouchInput {
            click()
            advanceEventTime(100)
            click()
        }
        compose.onAllNodesWithText("¿Borrar esta conversación?").assertCountEquals(1)
        compose.onNodeWithText("Borrar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Conversación borrada").assertIsDisplayed()
        assertThat(runBlocking { repository.conversationExists(ids[1]) }).isFalse()

        // The first snackbar goes away before the second one comes.
        compose.mainClock.advanceTimeBy(SNACKBAR_MS)
        compose.onNodeWithText("Conversación borrada").assertDoesNotExist()

        repository.failWrites = true
        compose.onNodeWithContentDescription("Borrar conversación: ¿Cuál es el plazo de devolución?").performClick()
        compose.onNodeWithText("Borrar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No se pudo borrar la conversación.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Borrar conversación: ¿Cuál es el plazo de devolución?").assertIsDisplayed()
    }

    private companion object {
        /** Longer than a short snackbar (4 s). */
        const val SNACKBAR_MS = 5_000L
    }
}

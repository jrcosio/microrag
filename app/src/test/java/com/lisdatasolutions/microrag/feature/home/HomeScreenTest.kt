package com.lisdatasolutions.microrag.feature.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
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
}

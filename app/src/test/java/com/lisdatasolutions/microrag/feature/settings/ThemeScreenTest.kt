package com.lisdatasolutions.microrag.feature.settings

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

/** F16 T017 and T028: Tema as in the approved wireframe §3–§4 (A2–A5). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class ThemeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = ThemeActions(
        onBack = { calls += "back" },
        onSelect = { calls += "select:$it" },
        onSave = { calls += "save" },
        onSaveFailureShown = { calls += "failureShown" },
    )

    private fun show(state: ThemeUiState) {
        compose.setContent { LisdataTheme { ThemeScreen(state = state, actions = actions) } }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val group = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)

    // --- US1 ------------------------------------------------------------------------------------------

    @Test
    fun structureOfTheWireframe() {
        show(ThemePreviewData.system)

        compose.onNodeWithText("Tema").assertExists()
        for (text in listOf(
            "APARIENCIA", "Elige cómo se ve la app", "TEMA DE LA APLICACIÓN",
            "Sistema", "Sigue el modo oscuro del teléfono", "Claro", "Oscuro", "Guardar preferencias",
        )) {
            scrollTo(text)
            compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
        }
        compose.onNode(isHeading() and hasText("Elige cómo se ve la app")).assertExists()
        // A2: no hero card and no other card than the read failure's.
        compose.onAllNodesWithText("Tus documentos.").assertCountEquals(0)
        compose.onAllNodesWithText("No se pudieron leer tus preferencias.", substring = true).assertCountEquals(0)
    }

    @Test
    fun u7_threeRadioButtons_inOneSelectableGroup() {
        show(ThemePreviewData.system)

        compose.onAllNodes(group).assertCountEquals(1)
        compose.onAllNodes(radio).assertCountEquals(3)
        compose.onNode(radio and hasText("Sistema", substring = true)).assertIsSelected()
        compose.onNode(radio and hasText("Claro")).assertIsNotSelected()
        compose.onNode(radio and hasText("Oscuro")).assertIsNotSelected()
    }

    @Test
    fun choosingAndSaving_askTheViewModel() {
        show(ThemePreviewData.system)

        compose.onNode(radio and hasText("Oscuro")).performClick()
        scrollTo("Guardar preferencias")
        compose.onNodeWithText("Guardar preferencias").performClick()

        assertThat(calls).containsExactly("select:DARK", "save").inOrder()
    }

    @Test
    fun theSelectionShows_evenBeforeItIsSaved() {
        show(ThemePreviewData.dark)

        compose.onNode(radio and hasText("Oscuro")).assertIsSelected()
        compose.onNode(radio and hasText("Sistema", substring = true)).assertIsNotSelected()
    }

    @Test
    fun whileSaving_theButtonSaysSo_andIsOff() {
        show(ThemePreviewData.saving)
        scrollTo("Guardando…")

        compose.onNodeWithText("Guardando…").assertIsNotEnabled()
        compose.onAllNodesWithText("Guardar preferencias").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "en-w390dp-h844dp")
    fun inEnglish() {
        show(ThemePreviewData.system)

        compose.onNodeWithText("Theme").assertExists()
        for (text in listOf("APPEARANCE", "Choose how the app looks", "APP THEME", "System", "Follows your phone's dark mode", "Light", "Dark")) {
            scrollTo(text)
            compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
        }
    }

    // --- US4 ------------------------------------------------------------------------------------------

    @Test
    fun a5_anUnreadableCopy_showsTheNotice_withSystemMarked() {
        show(ThemePreviewData.readFailed)
        scrollTo("No se pudieron leer tus preferencias.")

        compose.onNodeWithText("No se pudieron leer tus preferencias. Se usan las de por defecto.", useUnmergedTree = true).assertExists()
        compose.onNode(radio and hasText("Sistema", substring = true)).assertIsSelected()
    }

    @Test
    fun a4_aFailedSave_saysSoOnce_andTheButtonWorksAgain() {
        show(ThemePreviewData.saveFailed)
        compose.waitForIdle()

        compose.onNodeWithText("No se pudieron guardar las preferencias. Se mantienen las anteriores.").assertExists()
        assertThat(calls.count { it == "failureShown" }).isEqualTo(1)
        compose.onNode(radio and hasText("Oscuro")).assertIsSelected()
        scrollTo("Guardar preferencias")
        compose.onNodeWithText("Guardar preferencias").performClick()
        assertThat(calls).contains("save")
    }
}

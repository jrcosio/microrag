package com.lisdatasolutions.microrag.feature.settings

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.LanguageSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F12 T048: Idiomas as in PNG 10, with «Español» as the only answer language (S12) and the card of D-062. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class LanguagesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = LanguagesActions(
        onBack = { calls += "back" },
        onSelectApp = { calls += "select:$it" },
        onSave = { calls += "save" },
    )

    private val spanish = LanguagesUiState(saved = LanguageSettings(AppLanguage.SPANISH), selectedApp = AppLanguage.SPANISH)

    private fun show(state: LanguagesUiState) {
        compose.setContent { LisdataTheme { LanguagesScreen(state = state, actions = actions) } }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val group = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)

    @Test
    fun structureOfPng10() {
        show(spanish)

        compose.onNodeWithText("Idiomas").assertExists()
        for (text in listOf(
            "CONFIGURACIÓN LINGÜÍSTICA", "Habla en tu idioma", "Tus documentos.", "Tu idioma.", "IDIOMA DE LA APLICACIÓN",
            "English", "IDIOMA DE LAS RESPUESTAS", "Búsqueda multilingüe",
            "Consulta documentos en español o en inglés. Funciona mejor si preguntas en el idioma del documento.",
            "Guardar preferencias",
        )) {
            scrollTo(text)
            compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
        }
        compose.onAllNodesWithText("Automático").assertCountEquals(0)
    }

    @Test
    fun u3_twoGroupsOfRadioButtons_andOneAnswerOption() {
        show(spanish)

        compose.onAllNodes(group).assertCountEquals(2)
        compose.onAllNodes(radio).assertCountEquals(3)
        compose.onAllNodes(radio and hasText("Español")).assertCountEquals(2)
        compose.onNode(radio and hasText("English")).assertIsNotSelected()
        compose.onAllNodes(radio and hasText("Español")).fetchSemanticsNodes().forEach { node ->
            assertThat(node.config[SemanticsProperties.Selected]).isTrue()
        }
        scrollTo("IDIOMA DE LAS RESPUESTAS")
        val answerGroup = compose.onAllNodes(group).fetchSemanticsNodes().last()
        assertThat(answerGroup.children.count { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }).isEqualTo(1)
    }

    @Test
    fun choosingAndSaving_askTheViewModel() {
        show(spanish)

        compose.onNode(radio and hasText("English")).performClick()
        scrollTo("Guardar preferencias")
        compose.onNodeWithText("Guardar preferencias").performClick()
        compose.onNodeWithText("Idiomas").assertExists()

        assertThat(calls).containsExactly("select:ENGLISH", "save").inOrder()
    }

    @Test
    fun theSelectionShows_evenBeforeItIsSaved() {
        show(spanish.copy(selectedApp = AppLanguage.ENGLISH))

        compose.onNode(radio and hasText("English")).assertIsSelected()
    }

    @Test
    fun whileSaving_theButtonSaysSo() {
        show(spanish.copy(saving = true))
        scrollTo("Guardando…")

        compose.onNodeWithText("Guardando…").assertExists()
        compose.onAllNodesWithText("Guardar preferencias").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "en-w390dp-h844dp")
    fun s11_inEnglish_theLanguagesKeepTheirNames_andTheAnswerSaysSpanish() {
        show(spanish)

        compose.onNode(radio and hasText("Español")).assertExists()
        compose.onNode(radio and hasText("English")).assertExists()
        scrollTo("ANSWER LANGUAGE")
        compose.onNode(radio and hasText("Spanish")).assertIsSelected()
        compose.onNodeWithText("Save preferences").assertExists()
    }
}

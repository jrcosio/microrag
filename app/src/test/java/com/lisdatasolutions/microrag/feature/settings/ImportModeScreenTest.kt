package com.lisdatasolutions.microrag.feature.settings

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F19 T047 (wireframe §4, contract U6): the three modes, the images note and «Guardar». */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es")
class ImportModeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: ImportModeUiState) {
        compose.setContent {
            LisdataTheme {
                ImportModeScreen(
                    state,
                    ImportModeActions(onBack = { calls += "back" }, onSelect = { calls += "select:$it" }, onSave = { calls += "save" }),
                )
            }
        }
    }

    @Test
    fun theThreeModes_withTheirSubtitles_andTheSavedOneMarked() {
        show(ImportModeUiState(saved = IndexMode.BOTH, selected = IndexMode.BOTH))

        compose.onNodeWithText("Modo al importar PDF").assertExists()
        compose.onNode(hasText("Ambos") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Texto y páginas. Lo más completo y lo más lento.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Como hasta ahora.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Solo páginas. Para escaneados y documentos con muchas tablas.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Las imágenes JPG y PNG se indexan siempre como páginas.").assertExists()
    }

    @Test
    fun markingAndSaving_askTheViewModel() {
        show(ImportModeUiState(saved = IndexMode.BOTH, selected = IndexMode.BOTH))

        compose.onNodeWithText("Visual").performClick()
        compose.onNodeWithText("Guardar").performClick()

        assertThat(calls).containsExactly("select:VISUAL", "save").inOrder()
    }
}

package com.lisdatasolutions.microrag.feature.settings

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F20 T032 (wireframe §2, contract U2): the three details with their waits, the note and «Guardar». */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es")
class PageDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: PageDetailUiState) {
        compose.setContent {
            LisdataTheme {
                PageDetailScreen(
                    state,
                    PageDetailActions(onBack = { calls += "back" }, onSelect = { calls += "select:$it" }, onSave = { calls += "save" }),
                )
            }
        }
    }

    @Test
    fun theThreeDetails_withTheirWaits_andTheSavedOneMarked() {
        show(PageDetailUiState(saved = PageDetail.BALANCED, selected = PageDetail.BALANCED))

        compose.onNodeWithText("Detalle de las páginas").assertExists()
        compose.onNodeWithText("Cuánto detalle lee Gemma en la página que entra en cada respuesta.").assertExists()
        compose.onNode(hasText("Equilibrado") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Menos detalle. Unos 6,5 s por respuesta.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("El detalle con que se indexan las páginas. Unos 8,5 s por respuesta.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Para letra pequeña y tablas densas. Unos 14,5 s por respuesta.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Tiempos medidos en el teléfono de referencia. Las respuestas sin páginas no cambian.").assertExists()
    }

    @Test
    fun markingAndSaving_askTheViewModel() {
        show(PageDetailUiState(saved = PageDetail.BALANCED, selected = PageDetail.BALANCED))

        compose.onNodeWithText("Rápido").performClick()
        // Below the three longer rows: scrolled to first.
        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        assertThat(calls).containsExactly("select:FAST", "save").inOrder()
    }

    @Test
    @Config(qualifiers = "en")
    fun inEnglish_theWaitsUseAPoint() {
        show(PageDetailUiState(saved = PageDetail.DETAILED, selected = PageDetail.DETAILED))

        compose.onNodeWithText("Page detail").assertExists()
        compose.onNodeWithText("For small print and dense tables. About 14.5 s per answer.", useUnmergedTree = true).assertExists()
    }
}

package com.lisdatasolutions.microrag.feature.document

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F20 T037 (contract U12–U15, wireframe §6 and §10): the page viewer. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es")
class PageViewerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var closed = 0

    private fun show(state: PageImageState, image: Boolean = false, name: String = "informe-ine.pdf") {
        compose.setContent {
            LisdataTheme { PageViewerScreen(page = 4, documentName = name, image = image, state = state, onClose = { closed++ }) }
        }
    }

    @Test
    fun theTitleNamesThePage_andCloseCloses() {
        show(PageImageState.Loading)

        compose.onNodeWithText("informe-ine.pdf · Página 4").assertExists()
        compose.onNodeWithContentDescription("Cerrar").performClick()

        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun aLooseImage_isTitledByItsNameOnly() {
        show(PageImageState.Loading, image = true, name = "cartel.jpg")

        compose.onNodeWithText("cartel.jpg").assertExists()
        compose.onNodeWithText("cartel.jpg · Página 4").assertDoesNotExist()
    }

    @Test
    fun anUnreadablePage_saysItIsGone() {
        show(PageImageState.Missing)

        compose.onNodeWithText("La página citada ya no está en este documento.").assertExists()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun thePage_hasItsDescription_andTheZoomActionsForTalkBack() {
        show(PageImageState.Shown(ImageBitmap(60, 84)))

        val node = compose.onNodeWithContentDescription("Página 4 de informe-ine.pdf")
        val actions = node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertThat(actions.map { it.label }).containsExactly("Ampliar", "Reducir", "Restablecer").inOrder()
        compose.runOnIdle {
            assertThat(actions[1].action()).isFalse()
            assertThat(actions[0].action()).isTrue()
            assertThat(actions[1].action()).isTrue()
        }
    }
}

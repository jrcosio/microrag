package com.lisdatasolutions.microrag.core.designsystem.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F13 T006 (research R10, contracts/history-ui.md §2, U3, U4, U6): the history row. Native graphics, so the
 * texts are measured with real fonts.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConversationRowTest {

    @get:Rule
    val compose = createComposeRule()

    private var opened = 0
    private var deleted = 0

    private fun show(
        width: Dp = 358.dp,
        title: String = "¿Cuál es el plazo de devolución?",
        fontScale: Float = 1f,
        sentence: String? = null,
    ) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            LisdataTheme {
                Box(Modifier.width(width)) {
                    LisdataConversationRow(
                        title = title,
                        meta = "10:42 · 3 preguntas",
                        onOpen = { opened++ },
                        onDelete = { deleted++ },
                        deleteLabel = "Borrar conversación: $title",
                        chipText = "Abierta",
                        accessibilityText = sentence,
                        onClickLabel = "Abrir conversación",
                    )
                }
            }
        }
    }

    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun theRowAndItsBin_areTwoNodes_withTheirOwnSentences() {
        val sentence = "¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas. Abierta."
        show(sentence = sentence)

        val row = compose.onNodeWithContentDescription(sentence)
        row.assert(SemanticsMatcher("opens with its label") { it.config[SemanticsActions.OnClick].label == "Abrir conversación" })
        compose.onAllNodesWithContentDescription(sentence).assertCountEquals(1)
        val bin = compose.onNodeWithContentDescription("Borrar conversación: ¿Cuál es el plazo de devolución?")
        val binBounds = bin.getUnclippedBoundsInRoot()
        assertThat(binBounds.right - binBounds.left).isAtLeast(48.dp)
        assertThat(binBounds.bottom - binBounds.top).isAtLeast(48.dp)

        row.performClick()
        bin.performClick()
        assertThat(opened).isEqualTo(1)
        assertThat(deleted).isEqualTo(1)
    }

    @Test
    fun aLongTitle_takesTwoLinesAtMost() {
        val title = "Diferencias entre la versión 2 y la versión 3 del procedimiento de devoluciones para clientes de empresa y particulares"
        show(title = title)

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertThat(layouts.single().lineCount).isAtMost(2)
    }

    @Test
    fun theChip_goesBesideTheDate() {
        show()

        assertThat(bounds("Abierta").left).isGreaterThan(bounds("10:42 · 3 preguntas").right)
    }

    @Test
    fun withTheFontAt200_theChipGoesUnderTheDate_andTheBinKeepsItsSize() {
        show(fontScale = 2f)

        assertThat(bounds("Abierta").top).isAtLeast(bounds("10:42 · 3 preguntas").bottom)
        val bin = compose.onNodeWithContentDescription("Borrar conversación: ¿Cuál es el plazo de devolución?").getUnclippedBoundsInRoot()
        assertThat(bin.right - bin.left).isAtLeast(48.dp)
    }
}

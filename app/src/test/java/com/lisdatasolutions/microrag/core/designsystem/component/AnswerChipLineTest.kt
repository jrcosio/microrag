package com.lisdatasolutions.microrag.core.designsystem.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A citation chip is never split across two lines: its background would cover both (found on the emulator
 * in F13). Every width from 200 to 400 dp, with real fonts, for a tappable chip and an inert one.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w400dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnswerChipLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val content = AnswerContent(
        listOf(
            AnswerBlock.Paragraph(
                listOf(
                    AnswerSpan.Text("Según el documento, el plazo es de 30 días "), AnswerSpan.Citation(1),
                    AnswerSpan.Text(". Las empresas tienen 15 días "), AnswerSpan.Citation(2), AnswerSpan.Text("."),
                ),
            ),
        ),
    )

    @Test
    fun aChip_isNeverSplitAcrossTwoLines() {
        var width by mutableStateOf(200)
        compose.setContent {
            LisdataTheme {
                Box(Modifier.width(width.dp)) {
                    LisdataAnswerCard(content, AnswerState.Completed, onCopy = {}, unavailableCitations = setOf(2))
                }
            }
        }
        for (dp in 200..400 step 4) {
            width = dp
            compose.waitForIdle()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Según el documento", substring = true, useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            for (n in 1..2) {
                val start = Regex("[\\u2009\\u202F]$n[\\u2009\\u202F]").find(text)?.range?.first ?: -1
                assertWithMessage("chip $n is in the text").that(start).isAtLeast(0)
                assertWithMessage("chip $n at $dp dp").that(layout.getLineForOffset(start + 2)).isEqualTo(layout.getLineForOffset(start))
            }
        }
    }
}

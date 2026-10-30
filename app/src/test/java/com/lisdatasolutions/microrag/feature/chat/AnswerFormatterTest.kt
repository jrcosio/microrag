package com.lisdatasolutions.microrag.feature.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock.Bullet
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock.Paragraph
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan.Citation
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan.Text
import com.lisdatasolutions.microrag.rag.answer.AnswerReading
import org.junit.Test

/** F11 T024 (research R3, FR-021, FR-022, D-053, C10): the raw answer as the card draws it. */
class AnswerFormatterTest {

    @Test
    fun paragraphsBulletsNumberedItemsBoldAndHeadings() {
        val raw = "# Plazos\nEl plazo es de **30 días** [1].\n- Primero [2]\n* Segundo\n• Tercero\n2) Cuarto\n\n`código`"

        assertThat(AnswerFormatter.format(raw, blockCount = 2).blocks).containsExactly(
            Paragraph(listOf(Text("Plazos", bold = true))),
            Paragraph(listOf(Text("El plazo es de "), Text("30 días", bold = true), Text(" "), Citation(1), Text("."))),
            Bullet(listOf(Text("Primero "), Citation(2))),
            Bullet(listOf(Text("Segundo"))),
            Bullet(listOf(Text("Tercero"))),
            Bullet(listOf(Text("Cuarto")), marker = "2."),
            Paragraph(listOf(Text("código"))),
        ).inOrder()
    }

    @Test
    fun chipsAreNumberedByFirstCitation_asTheSavedSources() {
        val raw = "Uno [3]. Dos [1, 3]. Tres [3]."

        assertThat(AnswerFormatter.format(raw, blockCount = 4).blocks.single()).isEqualTo(
            Paragraph(listOf(Text("Uno "), Citation(1), Text(". Dos "), Citation(2), Citation(1), Text(". Tres "), Citation(1), Text("."))),
        )
        assertThat(AnswerReading.citedOrder(raw, 4)).containsExactly(3, 1).inOrder()
    }

    @Test
    fun outOfRangeMarksDisappear_withTheSpaceBeforeThem() {
        assertThat(AnswerFormatter.format("El plazo es de 30 días [7]. Otro [0].", blockCount = 4).blocks.single())
            .isEqualTo(Paragraph(listOf(Text("El plazo es de 30 días"), Text(". Otro"), Text("."))))
        assertThat(AnswerFormatter.format("[9]", blockCount = 4).blocks).isEmpty()
    }

    @Test
    fun aGrowingTextKeepsItsNumbers() {
        val full = "A [2]. B [1]. C [2] y [3]."
        val final = AnswerFormatter.format(full, blockCount = 3)
        (1..full.length).forEach { end ->
            val partial = AnswerFormatter.format(full.substring(0, end), blockCount = 3)
            val chips = partial.blocks.flatMap { (it as Paragraph).spans }.filterIsInstance<Citation>()
            val finalChips = final.blocks.flatMap { (it as Paragraph).spans }.filterIsInstance<Citation>()
            assertThat(finalChips.take(chips.size)).isEqualTo(chips)
        }
    }

    @Test
    fun copyText_showsTheChipsAsNumbers_andListsTheSources() {
        val content = AnswerFormatter.format("Pasos:\n- Verificar [2]\n1. Confirmar [1]", blockCount = 2)

        assertThat(AnswerFormatter.copyText(content, listOf("[1] Protocolo.pdf, página 8", "[2] Manual.pdf")))
            .isEqualTo("Pasos:\n• Verificar [1]\n1. Confirmar [2]\n\n[1] Protocolo.pdf, página 8\n[2] Manual.pdf")
    }

    @Test
    fun copyText_withoutSources_isTheTextShown() {
        val refusal = AnswerFormatter.format("No encuentro información suficiente en tus documentos", blockCount = 0)
        assertThat(AnswerFormatter.copyText(refusal, emptyList())).isEqualTo("No encuentro información suficiente en tus documentos")
    }
}

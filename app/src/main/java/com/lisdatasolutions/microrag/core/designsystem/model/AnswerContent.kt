package com.lisdatasolutions.microrag.core.designsystem.model

/**
 * The body of an assistant answer as the card draws it (F11 data-model §6): paragraphs and bullets, with
 * bold runs and citation chips. The chat formats the model's text into it; the card never parses text.
 */
data class AnswerContent(val blocks: List<AnswerBlock>) {
    val isEmpty: Boolean get() = blocks.isEmpty()

    companion object {
        val Empty = AnswerContent(emptyList())

        /** A single plain paragraph, for texts the app writes (the refusal, previews). */
        fun plain(text: String) = if (text.isEmpty()) Empty else AnswerContent(listOf(AnswerBlock.Paragraph(listOf(AnswerSpan.Text(text)))))
    }
}

sealed interface AnswerBlock {
    data class Paragraph(val spans: List<AnswerSpan>) : AnswerBlock

    /** [marker] is the number of an ordered item ("1."); null draws the dot of the PNG. */
    data class Bullet(val spans: List<AnswerSpan>, val marker: String? = null) : AnswerBlock
}

sealed interface AnswerSpan {
    data class Text(val text: String, val bold: Boolean = false) : AnswerSpan

    /** A tappable chip with the display [number] of a source (D-053). */
    data class Citation(val number: Int) : AnswerSpan
}

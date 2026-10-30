package com.lisdatasolutions.microrag.feature.chat

import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan
import com.lisdatasolutions.microrag.rag.answer.AnswerReading

/**
 * The model's raw text as the answer card draws it (F11 research R3, FR-021, FR-022). Each line is a
 * paragraph, a bullet (`- `, `* `, `• `, or a numbered `1. `) or a bold heading (`#`); `**bold**` runs
 * are kept and backticks dropped. Citations are read as judge.py does; the valid ones become chips
 * numbered by first citation (D-053), the same order the session saves the sources in, and the rest
 * disappear. Formatting a growing text while it streams keeps the numbers stable.
 */
object AnswerFormatter {
    private val BULLET = Regex("""^\s*[-*•]\s+""")
    private val NUMBERED = Regex("""^\s*(\d+)[.)]\s+""")
    private val HEADING = Regex("""^\s*#+\s*""")

    /** [blockCount] is the number of blocks in the prompt: the valid range of `[n]`. */
    fun format(raw: String, blockCount: Int): AnswerContent {
        val order = AnswerReading.citedOrder(raw, blockCount)
        val blocks = raw.split("\n").filter { it.isNotBlank() }.map { line -> block(line, order) }
        return AnswerContent(blocks.filterNot { it.spansOf().isEmpty() })
    }

    /**
     * What «Copiar respuesta» puts in the clipboard (C10): the text shown, chips as `[n]`, bullets kept,
     * and after a blank line the [sourceLines] («[1] Manual.pdf, página 12»).
     */
    fun copyText(content: AnswerContent, sourceLines: List<String>): String {
        val body = content.blocks.joinToString("\n") { block ->
            val text = block.spansOf().joinToString("") { span ->
                when (span) {
                    is AnswerSpan.Text -> span.text
                    is AnswerSpan.Citation -> "[${span.number}]"
                }
            }
            when (block) {
                is AnswerBlock.Paragraph -> text
                is AnswerBlock.Bullet -> "${block.marker ?: "•"} $text"
            }
        }
        return if (sourceLines.isEmpty()) body else body + "\n\n" + sourceLines.joinToString("\n")
    }

    private fun block(line: String, order: List<Int>): AnswerBlock {
        HEADING.find(line)?.takeIf { it.range.first == 0 }?.let { heading ->
            return AnswerBlock.Paragraph(spans(line.substring(heading.range.last + 1), order).map { it.bold() })
        }
        NUMBERED.find(line)?.let { numbered ->
            return AnswerBlock.Bullet(spans(line.substring(numbered.range.last + 1), order), marker = "${numbered.groupValues[1]}.")
        }
        BULLET.find(line)?.let { bullet ->
            return AnswerBlock.Bullet(spans(line.substring(bullet.range.last + 1), order))
        }
        return AnswerBlock.Paragraph(spans(line.trim(), order))
    }

    /** Text and chips of one line; bold runs between `**`, invalid citations dropped with the space before them. */
    private fun spans(line: String, order: List<Int>): List<AnswerSpan> {
        val out = mutableListOf<AnswerSpan>()
        var bold = false
        var cursor = 0

        fun text(segment: String) {
            segment.split("**").forEachIndexed { i, part ->
                if (i > 0) bold = !bold
                val clean = part.replace("`", "")
                if (clean.isNotEmpty()) out += AnswerSpan.Text(clean, bold)
            }
        }

        AnswerReading.marks(line).forEach { mark ->
            text(line.substring(cursor, mark.range.first))
            val chips = mark.numbers.mapNotNull { n -> order.indexOf(n).takeIf { it >= 0 }?.let { AnswerSpan.Citation(it + 1) } }
            if (chips.isEmpty()) {
                // An out-of-range mark leaves no trace: not even the space that preceded it.
                (out.lastOrNull() as? AnswerSpan.Text)?.let { last ->
                    out[out.lastIndex] = last.copy(text = last.text.trimEnd())
                    if (out.last().let { it is AnswerSpan.Text && it.text.isEmpty() }) out.removeAt(out.lastIndex)
                }
            }
            out += chips
            cursor = mark.range.last + 1
        }
        text(line.substring(cursor))
        return out
    }

    private fun AnswerSpan.bold(): AnswerSpan = if (this is AnswerSpan.Text) copy(bold = true) else this

    private fun AnswerBlock.spansOf(): List<AnswerSpan> = when (this) {
        is AnswerBlock.Paragraph -> spans
        is AnswerBlock.Bullet -> spans
    }
}

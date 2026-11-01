package com.lisdatasolutions.microrag.rag.answer

import com.lisdatasolutions.microrag.rag.ingest.PyText
import java.text.Normalizer
import java.util.Locale

/**
 * How an answer is read, exactly as `offline/eval/judge.py` (F11 research R3): its `[n]` citations, the
 * normalization and the refusal sentence. Python's Unicode classes are kept: `\d` is category Nd, `\w`
 * is any letter or number or `_`, `\s` is Python whitespace. No regex: Android's ICU engine has other
 * classes than the JVM's and rejects `(?U)`, so the code points are read with [PyText] on both.
 */
object AnswerReading {
    private val NORM_REFUSALS by lazy { setOf(norm(PromptBuilder.REFUSAL), norm(PromptBuilder.REFUSAL_EN)) }

    /** One `[n]` or `[n, m]` mark: where it is in the text and the numbers it cites. */
    data class CitationMark(val range: IntRange, val numbers: List<Int>)

    /** The citation marks of [answer] in order, as `\[(\d+(?:\s*,\s*\d+)*)\]` of judge.py finds them. */
    fun marks(answer: String): List<CitationMark> {
        val marks = mutableListOf<CitationMark>()
        var start = answer.indexOf('[')
        while (start >= 0) {
            val mark = markAt(answer, start)
            if (mark != null) marks += mark
            start = answer.indexOf('[', (mark?.range?.last ?: start) + 1)
        }
        return marks
    }

    /**
     * The mark that opens at [start], or null. Backtracking could not find another: no part of the
     * pattern gives back a character that the next part accepts.
     */
    private fun markAt(text: String, start: Int): CitationMark? {
        val numbers = mutableListOf<Int>()
        var end = decimalsEnd(text, start + 1) ?: return null
        numbers += decimal(text.substring(start + 1, end))
        while (true) {
            val comma = spacesEnd(text, end)
            if (text.getOrNull(comma) != ',') break
            val from = spacesEnd(text, comma + 1)
            val next = decimalsEnd(text, from) ?: break
            numbers += decimal(text.substring(from, next))
            end = next
        }
        return if (text.getOrNull(end) == ']') CitationMark(start..end, numbers) else null
    }

    /** The end of the run of `\d` at [from], or null when there is none. */
    private fun decimalsEnd(text: String, from: Int): Int? = runEnd(text, from, PyText::isDecimal).takeIf { it > from }

    private fun spacesEnd(text: String, from: Int): Int = runEnd(text, from, PyText::isSpace)

    private fun runEnd(text: String, from: Int, accepts: (Int) -> Boolean): Int {
        var i = from
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!accepts(cp)) break
            i += Character.charCount(cp)
        }
        return i
    }

    /** Every cited number in order, repeats kept: `[1, 2][3]` gives 1, 2, 3. */
    fun citations(answer: String): List<Int> = marks(answer).flatMap { it.numbers }

    /** `judge.norm`: no thousands separators, lower case, no accents, words separated by one space. */
    fun norm(text: String): String {
        val plain = Normalizer.normalize(dropThousands(text).lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val out = StringBuilder(plain.length)
        var pendingSpace = false
        var i = 0
        while (i < plain.length) {
            val cp = plain.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.getType(cp) == Character.NON_SPACING_MARK.toInt() -> Unit
                isWord(cp) -> {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    pendingSpace = false
                    out.appendCodePoint(cp)
                }
                else -> pendingSpace = true
            }
        }
        return out.toString()
    }

    /** The answer is one of the two refusal sentences, Spanish or English, after [norm]. */
    fun isRefusal(answer: String): Boolean = norm(answer) in NORM_REFUSALS

    /** Valid numbers (1..[blockCount]) in the order of their first citation, without repeats (D-053). */
    fun citedOrder(answer: String, blockCount: Int): List<Int> = citations(answer).filter { it in 1..blockCount }.distinct()

    /** `(?<=\d)[.,\s](?=\d{3}\b)`, matched on the original text as `re.sub` does. */
    private fun dropThousands(text: String): String {
        val cps = text.codePoints().toArray()
        val out = StringBuilder(text.length)
        for (i in cps.indices) {
            val cp = cps[i]
            val separator = cp == '.'.code || cp == ','.code || PyText.isSpace(cp)
            val drop = separator && i > 0 && PyText.isDecimal(cps[i - 1]) &&
                (1..3).all { k -> i + k < cps.size && PyText.isDecimal(cps[i + k]) } &&
                (i + 4 >= cps.size || !isWord(cps[i + 4]))
            if (!drop) out.appendCodePoint(cp)
        }
        return out.toString()
    }

    /** Python's `\w`: a letter, a number (Nd, Nl, No) or `_`. */
    private fun isWord(cp: Int): Boolean = Character.isLetter(cp) || cp == '_'.code || when (Character.getType(cp)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }

    /** `int()` of Unicode decimal digits. */
    private fun decimal(digits: String): Int = digits.codePoints().toArray().fold(0) { acc, cp -> acc * 10 + Character.digit(cp, 10) }
}

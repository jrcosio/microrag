package com.lisdatasolutions.microrag.rag.answer

import com.lisdatasolutions.microrag.rag.ingest.PyText
import java.text.Normalizer
import java.util.Locale

/**
 * How an answer is read, exactly as `offline/eval/judge.py` (F11 research R3): its `[n]` citations, the
 * normalization and the refusal sentence. Python's Unicode classes are kept: `\d` is category Nd, `\w`
 * is any letter or number or `_`, `\s` is Python whitespace.
 */
object AnswerReading {
    // (?U): Unicode classes, so \d and \s match what they match in Python.
    private val CITATION = Regex("""(?U)\[(\d+(?:\s*,\s*\d+)*)]""")
    private val NUMBER = Regex("""(?U)\d+""")
    private val NORM_REFUSAL by lazy { norm(PromptBuilder.REFUSAL) }

    /** Every cited number in order, repeats kept: `[1, 2][3]` gives 1, 2, 3. */
    fun citations(answer: String): List<Int> =
        CITATION.findAll(answer).flatMap { match -> NUMBER.findAll(match.groupValues[1]).map { decimal(it.value) } }.toList()

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

    fun isRefusal(answer: String): Boolean = norm(answer) == NORM_REFUSAL

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

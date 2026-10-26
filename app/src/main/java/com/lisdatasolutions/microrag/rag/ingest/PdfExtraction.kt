package com.lisdatasolutions.microrag.rag.ingest

/**
 * PDF pages into blocks, a port of `pdf_paragraphs` and `extract_pdf` in offline/eval/extract.py
 * (F06 research R8): a blank line, a bullet, or a sentence end followed by an uppercase letter or a
 * digit opens a paragraph; hyphenated line ends are joined; bare page numbers are dropped.
 */
internal object PdfExtraction {

    private const val SENTENCE_END = ".:;?!"
    private const val BULLET_MARKS = "-•*·▪–"
    private const val NUMBER_MARKS = ".)º"
    private const val LETTER_MARKS = ".)"

    /** The block of one page (1-based [page]), or null when it has no paragraph: a page without text. */
    fun pageBlock(pageText: String, page: Int): RawBlock? {
        val paragraphs = paragraphs(PyText.splitLines(pageText))
        return if (paragraphs.isEmpty()) null else RawBlock(emptyList(), paragraphs.joinToString("\n\n"), page)
    }

    fun paragraphs(lines: List<String>): List<String> {
        val paragraphs = ArrayList<String>()
        var current = ""
        for (raw in lines) {
            val line = PyText.collapseSpaces(raw)
            if (line.isEmpty() || PyText.isDigitString(line)) {
                if (line.isEmpty() && current.isNotEmpty()) {
                    paragraphs += current
                    current = ""
                }
                continue
            }
            val first = PyText.firstCodePoint(line)
            val startsNew = isBullet(line) ||
                (current.isNotEmpty() && current.last() in SENTENCE_END && (PyText.isUpper(first) || PyText.isDigit(first)))
            current = when {
                current.isEmpty() -> line
                startsNew -> {
                    paragraphs += current
                    line
                }
                current.endsWith("-") && current.codePointCount(0, current.length) > 1 &&
                    PyText.isAlpha(current.codePointBefore(current.length - 1)) && PyText.isLower(first) ->
                    current.dropLast(1) + line
                else -> "$current $line"
            }
        }
        if (current.isNotEmpty()) paragraphs += current
        return paragraphs
    }

    /** `^([-•*·▪–]|\d+[.)º]|[a-z][.)])\s` with Python's `\d` and `\s`. */
    fun isBullet(line: String): Boolean {
        if (line.isEmpty()) return false
        var i = 0
        when {
            line[0] in BULLET_MARKS -> i = 1
            PyText.isDecimal(line.codePointAt(0)) -> {
                while (i < line.length && PyText.isDecimal(line.codePointAt(i))) i += Character.charCount(line.codePointAt(i))
                if (i >= line.length || line[i] !in NUMBER_MARKS) return false
                i++
            }
            line[0] in 'a'..'z' -> {
                if (line.length < 2 || line[1] !in LETTER_MARKS) return false
                i = 2
            }
            else -> return false
        }
        return i < line.length && PyText.isSpace(line[i].code)
    }
}

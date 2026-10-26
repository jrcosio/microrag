package com.lisdatasolutions.microrag.rag.ingest

/**
 * TXT and Markdown extraction, a port of `extract_text` and `extract_markdown` in
 * offline/eval/extract.py (F06 research R8, golden/extraction_golden.jsonl). Lines arrive already
 * split as Python's `str.splitlines()` does.
 */
internal class PlainTextExtractor(
    private val maxBlockChars: Int = ExtractionLimits.MAX_BLOCK_CHARS,
    private val hardBlockChars: Int = maxBlockChars * 4,
) {
    fun extract(lines: Sequence<String>, emit: (RawBlock) -> Unit) {
        val section = SectionBuffer(maxBlockChars, hardBlockChars) { text -> emit(RawBlock(emptyList(), text, null)) }
        lines.forEach(section::add)
        section.flush()
    }
}

internal class MarkdownExtractor(
    private val maxBlockChars: Int = ExtractionLimits.MAX_BLOCK_CHARS,
    private val hardBlockChars: Int = maxBlockChars * 4,
) {
    fun extract(lines: Sequence<String>, emit: (RawBlock) -> Unit) {
        var stack = emptyList<Heading>()
        val section = SectionBuffer(maxBlockChars, hardBlockChars) { text -> emit(RawBlock(stack.map { it.title }, text, null)) }
        for (line in lines) {
            val heading = parseHeading(line)
            if (heading == null) {
                section.add(line)
            } else {
                section.flush()
                stack = stack.filter { it.level < heading.level } + heading
            }
        }
        section.flush()
    }

    data class Heading(val level: Int, val title: String)

    companion object {
        /**
         * `^(#{1,6})\s+(.+?)\s*#*\s*$` with Python's `\s`, written out because Java regexes use another
         * `\s`. The lazy title keeps at least one character, so `#  ` is a heading with an empty title
         * while `# ` is not a heading, and `## ###` has the title `#`.
         */
        fun parseHeading(line: String): Heading? {
            var hashes = 0
            while (hashes < line.length && line[hashes] == '#') hashes++
            if (hashes !in 1..6) return null
            var spaces = 0
            while (hashes + spaces < line.length && PyText.isSpace(line[hashes + spaces].code)) spaces++
            if (spaces == 0) return null
            val rest = line.substring(hashes + spaces)
            if (rest.isEmpty()) {
                // The title takes back one space from `\s+`, which needs another one.
                return if (spaces >= 2) Heading(hashes, "") else null
            }
            var end = rest.length
            while (end > 0 && PyText.isSpace(rest[end - 1].code)) end--
            while (end > 0 && rest[end - 1] == '#') end--
            while (end > 0 && PyText.isSpace(rest[end - 1].code)) end--
            val title = rest.substring(0, maxOf(end, Character.charCount(rest.codePointAt(0))))
            return Heading(hashes, PyText.strip(title))
        }
    }
}

/**
 * Lines of one section turned into block text as `_paragraph_text` does: lines without trailing
 * spaces, outer line breaks removed and runs of blank lines shortened to one.
 *
 * Above [maxChars] the section is emitted in several blocks, cut at a blank line, so memory stays
 * bounded; the chunker sees the same paragraphs in the same order (research R10). A paragraph without
 * any blank line beyond [hardChars] is cut at a line: the only case where the app's blocks differ from
 * the desktop's, for files no real document produces.
 */
internal class SectionBuffer(
    private val maxChars: Int,
    private val hardChars: Int = maxChars * 4,
    private val emit: (String) -> Unit,
) {
    private val lines = ArrayList<String>()
    private var chars = 0
    private var lastBlank = -1

    fun add(line: String) {
        val trimmed = PyText.rstrip(line)
        lines += trimmed
        chars += trimmed.length + 1
        if (trimmed.isEmpty()) lastBlank = lines.size - 1
        when {
            chars > maxChars && lastBlank > 0 -> emitUntil(lastBlank)
            chars > hardChars -> emitUntil(lines.size)
        }
    }

    fun flush() = emitUntil(lines.size)

    private fun emitUntil(index: Int) {
        val text = MULTIPLE_BLANK_LINES.replace(lines.subList(0, index).joinToString("\n").trim('\n'), "\n\n")
        if (PyText.strip(text).isNotEmpty()) emit(text)
        val rest = ArrayList(lines.subList(index, lines.size))
        lines.clear()
        lines.addAll(rest)
        chars = rest.sumOf { it.length + 1 }
        lastBlank = rest.indexOfLast { it.isEmpty() }
    }

    private companion object {
        val MULTIPLE_BLANK_LINES = Regex("\n{3,}")
    }
}

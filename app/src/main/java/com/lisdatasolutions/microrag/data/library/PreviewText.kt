package com.lisdatasolutions.microrag.data.library

import java.text.BreakIterator
import java.util.Locale

/**
 * The sentences shown around a cited chunk (F08 research R7): the last one of the chunk before and the
 * first one of the chunk after. Sentences come from the platform's [BreakIterator] for Spanish; a
 * break right after a common abbreviation («pág.», «art.», «Sr.») is joined back, as the JDK and
 * Android's ICU would otherwise differ on it. Line breaks end sentences. Very long sentences are cut
 * at a word, so the cited chunk stays the centre of the preview.
 */
object PreviewText {

    /** Characters kept of a neighbouring sentence, before the ellipsis. */
    const val MAX_CONTEXT = 240

    private val SPANISH = Locale.forLanguageTag("es")
    private val ABBREVIATIONS = setOf(
        "pág", "págs", "art", "arts", "núm", "núms", "cap", "caps", "apdo", "vol", "fig", "ej", "aprox", "tel",
        "av", "sr", "sra", "srta", "sres", "dr", "dra", "ud", "uds", "d", "dña",
    )
    private val LAST_WORD = Regex("""(\p{L}+)\.$""")

    /** How the Chunker joins the paragraphs of a chunk. */
    private const val PARAGRAPH_BREAK = "\n\n"

    fun firstSentence(text: String): String? = sentences(text).firstOrNull()?.let(::head)

    fun lastSentence(text: String): String? = sentences(text).lastOrNull()?.let(::tail)

    /**
     * The last sentence of [previous] that is not in [cited]: the Chunker carries a short last paragraph
     * into the next chunk, so that paragraph is left out.
     */
    fun before(previous: String, cited: String): String? {
        val carried = previous.substringAfterLast(PARAGRAPH_BREAK)
        return lastSentence(if (startsWithParagraph(cited, carried)) previous.dropLast(carried.length) else previous)
    }

    /** The first sentence of [next] that is not in [cited]: the paragraph carried from [cited] is left out. */
    fun after(cited: String, next: String): String? {
        val carried = cited.substringAfterLast(PARAGRAPH_BREAK)
        return firstSentence(if (startsWithParagraph(next, carried)) next.drop(carried.length) else next)
    }

    private fun startsWithParagraph(text: String, paragraph: String) =
        paragraph.isNotBlank() && (text == paragraph || text.startsWith(paragraph + PARAGRAPH_BREAK))

    private fun sentences(text: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(SPANISH).apply { setText(text) }
        val raw = buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                add(text.substring(start, end))
                start = end
                end = iterator.next()
            }
        }
        val joined = mutableListOf<String>()
        raw.forEach { piece ->
            val previous = joined.lastOrNull()
            if (previous != null && endsWithAbbreviation(previous)) joined[joined.lastIndex] = previous + piece else joined += piece
        }
        return joined.flatMap { it.split('\n', '\r') }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** «la pág. » ends with an abbreviation; a line break after it does end the sentence. */
    private fun endsWithAbbreviation(piece: String): Boolean {
        val trimmed = piece.trimEnd(' ', '\t')
        if (trimmed.endsWith('\n') || trimmed.endsWith('\r')) return false
        val word = LAST_WORD.find(trimmed)?.groupValues?.get(1) ?: return false
        return word.lowercase(SPANISH) in ABBREVIATIONS
    }

    private fun head(sentence: String): String {
        if (sentence.length <= MAX_CONTEXT) return sentence
        val cut = sentence.lastIndexOf(' ', MAX_CONTEXT).takeIf { it > 0 } ?: MAX_CONTEXT
        return sentence.substring(0, cut).trimEnd() + "…"
    }

    private fun tail(sentence: String): String {
        if (sentence.length <= MAX_CONTEXT) return sentence
        val from = sentence.length - MAX_CONTEXT
        val cut = sentence.indexOf(' ', from).takeIf { it in from until sentence.length - 1 } ?: from
        return "…" + sentence.substring(cut).trimStart()
    }
}

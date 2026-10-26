package com.lisdatasolutions.microrag.rag.ingest

import com.lisdatasolutions.microrag.domain.rag.RagParameters

/**
 * The report's Chunker (§Parsing y chunking) with the parameters fixed in F05 (D-034), checked against
 * offline/golden/chunker_golden.jsonl. Kotlin semantics on purpose: `String.length` counts UTF-16
 * units, `trim()` uses `isWhitespace`, and `\s` in these regexes is ASCII.
 *
 * [Session] keeps the state between blocks so a document can be chunked while it is read; [chunk]
 * runs one session over a list.
 */
class Chunker(
    private val targetChars: Int = RagParameters.CHUNK_TARGET_CHARS,
    private val maxChars: Int = RagParameters.CHUNK_MAX_CHARS,
) {

    fun chunk(blocks: List<RawBlock>): List<Chunk> {
        val out = ArrayList<Chunk>()
        val session = Session()
        blocks.forEach { session.add(it, out::add) }
        session.finish(out::add)
        return out
    }

    inner class Session {
        private var count = 0
        private var buf = StringBuilder()
        private var path = ""
        private var page: Int? = null
        private var lastParagraph = ""

        fun add(block: RawBlock, emit: (Chunk) -> Unit) {
            val blockPath = block.headingPath.joinToString(" › ")
            if (blockPath != path) {
                flush(carry = false, emit)
                path = blockPath
                page = block.page
            }
            for (paragraph in splitParagraphs(block.text, maxChars)) {
                if (buf.length + paragraph.length > targetChars && buf.isNotEmpty()) flush(carry = true, emit)
                if (buf.isEmpty()) page = block.page
                buf.append(paragraph).append("\n\n")
                lastParagraph = paragraph
            }
        }

        fun finish(emit: (Chunk) -> Unit) = flush(carry = false, emit)

        private fun flush(carry: Boolean, emit: (Chunk) -> Unit) {
            val text = buf.toString().trim()
            if (text.isNotEmpty()) emit(Chunk(count++, path, text, page))
            buf = StringBuilder()
            if (carry && lastParagraph.length < targetChars / 3) buf.append(lastParagraph).append("\n\n")
        }
    }

    private fun splitParagraphs(text: String, max: Int): List<String> =
        text.split(PARAGRAPH_BREAK).map { it.trim() }.filter { it.isNotEmpty() }
            .flatMap { p -> if (p.length <= max) listOf(p) else splitBySentence(p, max) }

    private fun splitBySentence(paragraph: String, max: Int): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        for (sentence in paragraph.split(SENTENCE_BREAK)) {
            if (cur.length + sentence.length > max && cur.isNotEmpty()) {
                parts += cur.toString()
                cur.clear()
            }
            cur.append(sentence).append(' ')
        }
        if (cur.isNotEmpty()) parts += cur.toString().trim()
        return parts
    }

    private companion object {
        val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
        val SENTENCE_BREAK = Regex("(?<=[.!?;:])\\s+")
    }
}

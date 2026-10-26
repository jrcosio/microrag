package com.lisdatasolutions.microrag.rag.ingest

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * F06 T043 (research R10): cutting long sections into several blocks at blank lines gives the same
 * chunks as one block per section, on every text and Markdown case of the golden and a long text.
 */
class StreamingEquivalenceTest {

    private val chunker = Chunker()

    /** The hard cut inside a paragraph is off here: it is the documented exception (see below). */
    private fun chunksOf(lines: Sequence<String>, markdown: Boolean, maxBlockChars: Int): List<Chunk> {
        val blocks = mutableListOf<RawBlock>()
        val hard = Int.MAX_VALUE / 8
        if (markdown) {
            MarkdownExtractor(maxBlockChars, hard).extract(lines, blocks::add)
        } else {
            PlainTextExtractor(maxBlockChars, hard).extract(lines, blocks::add)
        }
        return chunker.chunk(blocks)
    }

    @Test
    fun smallBlocksGiveTheSameChunks() {
        val cases = GoldenFiles.cases(File("../offline/golden/extraction_golden.jsonl").readText())
            .filter { it.getValue("kind").jsonPrimitive.content in setOf("text", "markdown") }
        val inputs = cases.map { it.getValue("case").jsonPrimitive.content to (it.getValue("input").jsonPrimitive.content to (it.getValue("kind").jsonPrimitive.content == "markdown")) } +
            ("synthetic" to (buildString { repeat(3_000) { append("Párrafo $it con texto. Otra frase más.\nSigue la línea $it.\n\n") } } to false))

        for ((name, input) in inputs) {
            val (text, markdown) = input
            val lines = PyText.splitLines(text)
            val whole = chunksOf(lines.asSequence(), markdown, Int.MAX_VALUE / 8)
            val cut = chunksOf(lines.asSequence(), markdown, maxBlockChars = 200)
            assertWithMessage(name).that(cut).isEqualTo(whole)
        }
    }

    @Test
    fun aParagraphWithoutBlankLinesIsCutAtALineBeyondTheHardLimit() {
        val lines = (1..100).map { "Línea $it de un párrafo sin líneas en blanco." }
        val blocks = mutableListOf<RawBlock>()

        PlainTextExtractor(maxBlockChars = 200, hardBlockChars = 1_000).extract(lines.asSequence(), blocks::add)

        assertWithMessage("blocks").that(blocks.size).isGreaterThan(1)
        assertWithMessage("every line kept").that(blocks.flatMap { it.text.split("\n") }).isEqualTo(lines)
    }
}

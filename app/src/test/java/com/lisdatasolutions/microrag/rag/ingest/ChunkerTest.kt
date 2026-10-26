package com.lisdatasolutions.microrag.rag.ingest

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F06 T042: the report's chunking rules (FR-022), with small sizes so they are easy to see. */
class ChunkerTest {

    private val chunker = Chunker(targetChars = 60, maxChars = 80)

    private fun block(text: String, vararg path: String, page: Int? = null) = RawBlock(path.toList(), text, page)

    @Test
    fun paragraphsStayWholeAndOnlyLongOnesAreSplitBySentence() {
        val long = "Primera frase del párrafo largo. Segunda frase que lo alarga bastante. Tercera frase final."
        val chunks = chunker.chunk(listOf(block("Corto uno.\n\n$long")))

        assertThat(chunks.joinToString("|") { it.content }).doesNotContain("Primera frase del párrafo largo. Segunda frase que lo alarga bastante. Tercera")
        assertThat(chunks.flatMap { it.content.split("\n\n") }).contains("Corto uno.")
        chunks.flatMap { it.content.split("\n\n") }.forEach { assertThat(it.length).isAtMost(80) }
    }

    @Test
    fun aNewHeadingOpensAChunk() {
        val chunks = chunker.chunk(listOf(block("Uno.", "A"), block("Dos.", "A", "B"), block("Tres.", "A", "B")))

        assertThat(chunks.map { it.headingPath to it.content }).containsExactly("A" to "Uno.", "A › B" to "Dos.\n\nTres.").inOrder()
    }

    @Test
    fun aShortLastParagraphIsCarriedAsOverlap() {
        val chunks = chunker.chunk(listOf(block("Párrafo inicial bastante largo para llenar.\n\nCorto.\n\nOtro párrafo que ya no cabe aquí.")))

        assertThat(chunks).hasSize(2)
        assertThat(chunks[1].content).startsWith("Corto.")
    }

    @Test
    fun aChunkExceedsTheMaximumOnlyByTheCarriedOverlap() {
        val carried = "Breve."
        val full = "x".repeat(79) + "."
        val chunks = chunker.chunk(listOf(block("${"y".repeat(55)}.\n\n$carried\n\n$full")))

        val overflow = chunks.filter { it.content.length > 80 }
        assertThat(overflow).hasSize(1)
        assertThat(overflow.single().content).isEqualTo("$carried\n\n$full")
        assertThat(overflow.single().content.length).isAtMost(80 + 60 / 3 + 2)
    }

    @Test
    fun ordinalsAreConsecutiveAndPagesAreTheStartPage() {
        val chunks = chunker.chunk((1..6).map { block("Texto de la página $it con contenido suficiente.", page = it) })

        assertThat(chunks.map { it.ordinal }).isEqualTo(chunks.indices.toList())
        assertThat(chunks.first().page).isEqualTo(1)
        chunks.zipWithNext().forEach { (a, b) -> assertThat(b.page!!).isAtLeast(a.page!!) }
    }

    @Test
    fun theSessionGivesTheSameChunksAsTheList() {
        val blocks = listOf(block("Uno.\n\nDos.", "A", page = 1), block("Tres largo ${"z".repeat(70)}.", "A", page = 2), block("Cuatro.", "B"))
        val streamed = mutableListOf<Chunk>()
        val session = chunker.Session()
        blocks.forEach { session.add(it, streamed::add) }
        session.finish(streamed::add)

        assertThat(streamed).isEqualTo(chunker.chunk(blocks))
    }
}

package com.lisdatasolutions.microrag.rag.retrieval

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F10 T021 (research R2): the cases of offline/tests/test_selector.py, and our own. */
class ContextSelectorTest {

    private val store = (1L..4L).flatMap { doc -> (0 until 10).map { view(doc, it) } }.associateBy { it.documentId to it.ordinal }
    private val neighbor: suspend (Long, Int) -> ChunkView? = { doc, ordinal -> store[doc to ordinal] }
    private fun at(doc: Long, ordinal: Int) = store.getValue(doc to ordinal)

    // --- test_selector.py, with the report's starting values (6 blocks, 3 per document, 2,200 tokens, 2 neighbours)

    @Test
    fun atMostThreePerDocument() = runTest {
        val ranked = listOf(0, 2, 4, 6).map { at(1, it) } + at(2, 0)
        val blocks = report(expandTop = 0).select(ranked, neighbor)
        assertThat(blocks.map { it.documentId to it.firstOrdinal }).containsExactly(1L to 0, 1L to 2, 1L to 4, 2L to 0).inOrder()
    }

    @Test
    fun theNeighbourOfTheTwoBestIsAddedAndMerged() = runTest {
        val blocks = report().select(listOf(at(1, 4), at(2, 7), at(3, 1)), neighbor)
        assertThat(blocks.map { Triple(it.documentId, it.firstOrdinal, it.lastOrdinal) })
            .containsExactly(Triple(1L, 4, 5), Triple(2L, 7, 8), Triple(3L, 1, 1)).inOrder()
        assertThat(blocks[0].tokens).isEqualTo(200)
        assertThat(blocks[0].text).isEqualTo("Doc 1 fragmento 4.\n\nDoc 1 fragmento 5.")
        assertThat(blocks[0].chunkIds).containsExactly(at(1, 4).id, at(1, 5).id).inOrder()
    }

    @Test
    fun mergeDropsTheOverlappedParagraph() = runTest {
        val a = view(1, 0, content = "Primero.\n\nSolape común.")
        val b = view(1, 1, content = "Solape común.\n\nSegundo.")
        val blocks = report(expandTop = 0).select(listOf(a, b), neighbor)
        assertThat(blocks.single().text).isEqualTo("Primero.\n\nSolape común.\n\nSegundo.")
    }

    @Test
    fun budgetAndBlockLimit() = runTest {
        val ranked = (1L..4L).map { at(it, 0) }
        assertThat(report(expandTop = 0, budgetTokens = 250).select(ranked, neighbor)).hasSize(2)
        assertThat(report(expandTop = 0, maxBlocks = 3).select(ranked, neighbor)).hasSize(3)
    }

    @Test
    fun blocksAreOrderedByDocumentAndOrdinal() = runTest {
        val blocks = report(expandTop = 0).select(listOf(at(3, 2), at(1, 8), at(3, 0)), neighbor)
        assertThat(blocks.map { it.documentId to it.firstOrdinal }).containsExactly(1L to 8, 3L to 0, 3L to 2).inOrder()
    }

    // --- our own cases

    @Test
    fun aNeighbourAlreadyChosen_isNotAddedTwice() = runTest {
        // (1, 5) comes in as the neighbour of (1, 4) and then as ranked: counted once, and it still brings (1, 6), as in Python.
        val blocks = report().select(listOf(at(1, 4), at(1, 5)), neighbor)
        assertThat(blocks.single().chunkIds).containsExactly(at(1, 4).id, at(1, 5).id, at(1, 6).id).inOrder()
        assertThat(blocks.single().tokens).isEqualTo(300)
    }

    @Test
    fun aNeighbourThatDoesNotFit_isLeftOut() = runTest {
        val small = view(1, 0, tokens = 100)
        val big = view(1, 1, tokens = 500)
        val blocks = report(budgetTokens = 300).select(listOf(small)) { doc, ordinal -> if (doc to ordinal == 1L to 1) big else null }
        assertThat(blocks.single().chunkIds).containsExactly(small.id)
    }

    @Test
    fun withoutNeighbours_onlyRankedChunksAreChosen_andNeighbourIsNeverAsked() = runTest {
        var asked = 0
        val blocks = ContextSelector(expandTop = 0).select(listOf(at(1, 4), at(2, 7))) { _, _ -> asked++; null }
        assertThat(asked).isEqualTo(0)
        assertThat(blocks.map { it.chunkIds }).containsExactly(listOf(at(1, 4).id), listOf(at(2, 7).id)).inOrder()
    }

    @Test
    fun aChunkThatDoesNotFit_doesNotStopTheWalk() = runTest {
        val big = view(1, 0, tokens = 2_000)
        val blocks = ContextSelector().select(listOf(big, at(2, 0), at(3, 0)), neighbor)
        assertThat(blocks.map { it.documentId }).containsExactly(2L, 3L).inOrder()
    }

    @Test
    fun theDefaultsAreTheDecidedParameters() = runTest {
        val ranked = (1L..4L).flatMap { doc -> listOf(0, 2).map { at(doc, it) } }
        val blocks = ContextSelector().select(ranked, neighbor)
        assertThat(blocks).hasSize(RagParameters.MAX_BLOCKS)
        assertThat(blocks.sumOf { it.tokens }).isAtMost(RagParameters.BUDGET_TOKENS)
    }

    @Test
    fun aBlockKeepsTheNameHeadingAndPageOfItsFirstChunk() = runTest {
        val first = view(1, 4).copy(headingPath = "Capítulo › Sección", page = 7)
        val second = view(1, 5).copy(headingPath = "Otra", page = 8)
        val block = report(expandTop = 0).select(listOf(first, second), neighbor).single()
        assertThat(block.documentName).isEqualTo("Doc 1")
        assertThat(block.headingPath).isEqualTo("Capítulo › Sección")
        assertThat(block.page).isEqualTo(7)
        assertThat(block.firstOrdinal to block.lastOrdinal).isEqualTo(4 to 5)
    }

    @Test
    fun mergeWithoutOverlap_withAndWithoutTheRepeatedParagraph() {
        assertThat(ContextSelector.mergeWithoutOverlap("Uno.\n\nDos.  ", "  Dos.\n\nTres.")).isEqualTo("Uno.\n\nDos.\n\nTres.")
        assertThat(ContextSelector.mergeWithoutOverlap("Uno.\n\nDos.", "  Otra cosa.\n")).isEqualTo("Uno.\n\nDos.\n\nOtra cosa.")
        assertThat(ContextSelector.mergeWithoutOverlap("", " Texto ")).isEqualTo("\n\nTexto")
        assertThat(ContextSelector.mergeWithoutOverlap("Solo.", "")).isEqualTo("Solo.\n\n")
    }

    private fun report(maxBlocks: Int = 6, budgetTokens: Int = 2_200, expandTop: Int = 2) =
        ContextSelector(maxBlocks = maxBlocks, maxPerDocument = 3, budgetTokens = budgetTokens, expandTop = expandTop)

    private companion object {
        fun view(doc: Long, ordinal: Int, tokens: Int = 100, content: String? = null) = ChunkView(
            id = doc * 100 + ordinal, documentId = doc, ordinal = ordinal, headingPath = null, page = null,
            content = content ?: "Doc $doc fragmento $ordinal.", tokenEstimate = tokens, documentName = "Doc $doc",
        )
    }
}

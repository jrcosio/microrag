package com.lisdatasolutions.microrag.rag.retrieval

import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.retrieval.ContextBlock

/**
 * The report's context selector, exact port of offline/eval/selector.py (F10 research R2): chunks in
 * fusion order, at most [maxPerDocument] per document, the next neighbour of the [expandTop] best ones,
 * a token budget, consecutive chunks merged without the chunker overlap, and blocks ordered by document
 * and ordinal. Each block keeps the ids of its chunks, so the answer cites real chunks (constitution III).
 */
class ContextSelector(
    private val maxBlocks: Int = RagParameters.MAX_BLOCKS,
    private val maxPerDocument: Int = RagParameters.MAX_PER_DOCUMENT,
    private val budgetTokens: Int = RagParameters.BUDGET_TOKENS,
    private val expandTop: Int = RagParameters.EXPAND_TOP,
) {

    /** [ranked] in fusion order; [neighbor] loads the chunk at (document, ordinal), or null. */
    suspend fun select(ranked: List<ChunkView>, neighbor: suspend (Long, Int) -> ChunkView?): List<ContextBlock> {
        val chosen = LinkedHashMap<Pair<Long, Int>, ChunkView>()
        val perDocument = HashMap<Long, Int>()
        var used = 0

        fun tryAdd(chunk: ChunkView): Boolean {
            val key = chunk.documentId to chunk.ordinal
            if (key in chosen) return true
            if ((perDocument[chunk.documentId] ?: 0) >= maxPerDocument) return false
            if (used + chunk.tokenEstimate > budgetTokens) return false
            chosen[key] = chunk
            perDocument[chunk.documentId] = (perDocument[chunk.documentId] ?: 0) + 1
            used += chunk.tokenEstimate
            return true
        }

        for ((rank, chunk) in ranked.withIndex()) {
            if (chosen.size >= maxBlocks + expandTop) break
            if (tryAdd(chunk) && rank < expandTop) {
                neighbor(chunk.documentId, chunk.ordinal + 1)?.let { tryAdd(it) }
            }
        }

        val blocks = mutableListOf<ContextBlock>()
        for (chunk in chosen.values.sortedWith(compareBy({ it.documentId }, { it.ordinal }))) {
            val last = blocks.lastOrNull()
            blocks += if (last != null && last.documentId == chunk.documentId && last.lastOrdinal + 1 == chunk.ordinal) {
                blocks.removeAt(blocks.lastIndex)
                last.copy(
                    lastOrdinal = chunk.ordinal,
                    chunkIds = last.chunkIds + chunk.id,
                    text = mergeWithoutOverlap(last.text, chunk.content),
                    tokens = last.tokens + chunk.tokenEstimate,
                )
            } else {
                ContextBlock(
                    documentId = chunk.documentId,
                    documentName = chunk.documentName,
                    headingPath = chunk.headingPath,
                    page = chunk.page,
                    firstOrdinal = chunk.ordinal,
                    lastOrdinal = chunk.ordinal,
                    chunkIds = listOf(chunk.id),
                    text = chunk.content,
                    tokens = chunk.tokenEstimate,
                )
            }
        }
        return blocks.take(maxBlocks)
    }

    companion object {
        /** Drops the paragraph the chunker repeats at the start of [b] (`_merge_without_overlap`). */
        fun mergeWithoutOverlap(a: String, b: String): String {
            val aEnd = a.trimEnd()
            val lastParagraph = aEnd.substringAfterLast("\n\n")
            val bStart = b.trimStart()
            val rest = if (bStart.startsWith(lastParagraph)) bStart.substring(lastParagraph.length) else b
            return aEnd + "\n\n" + rest.trim()
        }
    }
}

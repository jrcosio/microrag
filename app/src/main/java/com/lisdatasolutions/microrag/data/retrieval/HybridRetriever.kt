package com.lisdatasolutions.microrag.data.retrieval

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.retrieval.CandidateScore
import com.lisdatasolutions.microrag.domain.retrieval.ContextRetriever
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalTimings
import com.lisdatasolutions.microrag.rag.retrieval.ContextSelector
import com.lisdatasolutions.microrag.rag.retrieval.FusedCandidate
import com.lisdatasolutions.microrag.rag.retrieval.ReciprocalRankFusion
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Hybrid retrieval of the context of a question (F10 research R6), as the desktop harness evaluated it
 * (D-034): the lexical search starts with the query embedding, then the vector search; their RRF fusion
 * goes to the context selector, loading only chunks that are still searchable. Keeps nothing between
 * calls, so concurrent retrievals never mix (FR-022).
 */
class HybridRetriever(
    private val index: RagIndex,
    private val embedder: QueryEmbedder,
    private val logger: AppLogger,
    private val clock: () -> Long = System::nanoTime,
    private val selector: ContextSelector = ContextSelector(),
) : ContextRetriever {

    override suspend fun retrieve(request: RetrievalRequest): RetrievalResult {
        val started = clock()
        val question = request.question
        val coldEngine = !embedder.loaded
        return coroutineScope {
            val text = async { timed { index.textSearch(question, RagParameters.CANDIDATES) } }
            val embedding = timed { embedder.embed(question, null) }
            val vector = timed { index.vectorSearch(embedding.value.vector, RagParameters.CANDIDATES) }
            val lexical = text.await()
            val selection = timed { select(vector.value, lexical.value) }
            RetrievalResult(
                searchText = question,
                followUp = false,
                embeddingCuts = embedding.value.cuts,
                timings = RetrievalTimings(
                    embeddingMs = embedding.ms,
                    vectorMs = vector.ms,
                    textMs = lexical.ms,
                    selectionMs = selection.ms,
                    totalMs = elapsedMs(started),
                    coldEngine = coldEngine,
                ),
                outcome = selection.value,
            )
        }
    }

    /** Fusion, the first FUSED_LIMIT loaded if still searchable, and the selector (FR-015). */
    private suspend fun select(vector: List<ScoredChunk>, text: List<Long>): RetrievalOutcome {
        val fused = ReciprocalRankFusion.fuse(listOf(vector.map { it.id }, text), RagParameters.RRF_K).take(RagParameters.FUSED_LIMIT)
        val views = index.loadChunks(fused.map { it.id }, searchableOnly = true)
        val blocks = selector.select(fused.mapNotNull { views[it.id] }) { document, ordinal ->
            index.neighbor(document, ordinal, searchableOnly = true)
        }
        if (blocks.isEmpty()) return RetrievalOutcome.NoResults
        val selected = blocks.flatMapTo(HashSet()) { it.chunkIds }
        return RetrievalOutcome.Blocks(blocks, fused.mapIndexed { i, candidate -> candidate.score(i + 1, vector, selected) })
    }

    private fun FusedCandidate.score(fusedRank: Int, vector: List<ScoredChunk>, selected: Set<Long>): CandidateScore {
        val vectorRank = ranks[0]
        return CandidateScore(
            chunkId = id,
            fusedRank = fusedRank,
            rrfScore = score,
            vectorRank = vectorRank,
            vectorDistance = vectorRank?.let { vector[it - 1].distance },
            textRank = ranks[1],
            selected = id in selected,
        )
    }

    private class Timed<T>(val value: T, val ms: Double)

    private suspend fun <T> timed(block: suspend () -> T): Timed<T> {
        val started = clock()
        val value = block()
        return Timed(value, elapsedMs(started))
    }

    private fun elapsedMs(startedNanos: Long): Double = (clock() - startedNanos) / NANOS_PER_MS

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
    }
}

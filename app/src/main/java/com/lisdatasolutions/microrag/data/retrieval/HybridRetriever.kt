package com.lisdatasolutions.microrag.data.retrieval

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
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
import com.lisdatasolutions.microrag.rag.retrieval.FollowUpRule
import com.lisdatasolutions.microrag.rag.retrieval.FusedCandidate
import com.lisdatasolutions.microrag.rag.retrieval.ReciprocalRankFusion
import com.lisdatasolutions.microrag.rag.runtime.QueryEmbedding
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Hybrid retrieval of the context of a question (F10 research R6), as the desktop harness evaluated it
 * (D-034). In order: an empty question, the index and the model are checked, so a retrieval that cannot
 * search never loads the engine. Then a follow-up is searched after the previous question; the lexical
 * search starts with the query embedding, then the vector search; their RRF fusion goes to the context
 * selector, loading only chunks that are still searchable (FR-015).
 *
 * Never throws but CancellationException: every failure is an outcome (FR-016). Keeps nothing between
 * calls, so concurrent retrievals never mix (FR-022). Logs one line of numbers per call, never text (FR-030).
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
        if (question.isBlank()) return unsearched(started, question, RetrievalOutcome.EmptyQuestion)

        // A blank previous question counts as none, so it never joins the search text.
        val searchText = FollowUpRule.searchText(question, request.previousQuestion?.takeIf { it.isNotBlank() })
        val followUp = searchText != question

        val state = try {
            index.open()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            IndexCompatibility.Unreadable("open: ${e.javaClass.simpleName}")
        }
        if (state != IndexCompatibility.Compatible) return unsearched(started, searchText, RetrievalOutcome.IndexUnavailable(state), followUp)
        if (!modelReady()) return unsearched(started, searchText, RetrievalOutcome.ModelsNotReady, followUp)

        val coldEngine = !embedder.loaded
        val search = try {
            search(question, request.previousQuestion.takeIf { followUp }, searchText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmbeddingFailure) {
            return unsearched(started, searchText, RetrievalOutcome.EmbeddingFailed, followUp, coldEngine, e.cause)
        } catch (e: IndexUnavailableException) {
            return unsearched(started, searchText, RetrievalOutcome.IndexUnavailable(e.state), followUp, coldEngine, e)
        } catch (e: Exception) {
            val unreadable = IndexCompatibility.Unreadable("search: ${e.javaClass.simpleName}")
            return unsearched(started, searchText, RetrievalOutcome.IndexUnavailable(unreadable), followUp, coldEngine, e)
        }
        val result = RetrievalResult(
            searchText = searchText,
            followUp = followUp,
            embeddingCuts = search.embedding.cuts,
            timings = RetrievalTimings(
                embeddingMs = search.embeddingMs,
                vectorMs = search.vectorMs,
                textMs = search.textMs,
                selectionMs = search.selectionMs,
                totalMs = elapsedMs(started),
                coldEngine = coldEngine,
            ),
            outcome = search.outcome,
        )
        log(result, search)
        return result
    }

    private suspend fun modelReady(): Boolean = try {
        embedder.ready()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** The lexical search overlaps the embedding (rule Q7); only the embedded text may be cut (rule Q8). */
    private suspend fun search(question: String, previous: String?, searchText: String): Search = coroutineScope {
        val text = async { timed { index.textSearch(searchText, RagParameters.CANDIDATES) } }
        val embedding = timed {
            try {
                embedder.embed(question, previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw EmbeddingFailure(e)
            }
        }
        val vector = timed { index.vectorSearch(embedding.value.vector, RagParameters.CANDIDATES) }
        val lexical = text.await()
        val fused = ReciprocalRankFusion.fuse(listOf(vector.value.map { it.id }, lexical.value), RagParameters.RRF_K)
        val selection = timed { select(fused.take(RagParameters.FUSED_LIMIT), vector.value) }
        Search(
            embedding = embedding.value, vectorHits = vector.value.size, textHits = lexical.value.size, fused = fused.size,
            outcome = selection.value, embeddingMs = embedding.ms, vectorMs = vector.ms, textMs = lexical.ms, selectionMs = selection.ms,
        )
    }

    /** The first FUSED_LIMIT candidates, loaded only if still searchable, and the selector (FR-015). */
    private suspend fun select(fused: List<FusedCandidate>, vector: List<ScoredChunk>): RetrievalOutcome {
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

    /** A retrieval that ended before or during the searches: the steps that did not run count 0. */
    private fun unsearched(
        started: Long,
        searchText: String,
        outcome: RetrievalOutcome,
        followUp: Boolean = false,
        coldEngine: Boolean = false,
        error: Throwable? = null,
    ): RetrievalResult {
        val result = RetrievalResult(searchText, followUp, 0, RetrievalTimings(totalMs = elapsedMs(started), coldEngine = coldEngine), outcome)
        // The error's class only: messages may quote the input (constitution I).
        val reason = when (outcome) {
            is RetrievalOutcome.IndexUnavailable -> " state=${outcome.state.javaClass.simpleName}"
            else -> ""
        } + (error?.let { " error=${it.javaClass.simpleName}" } ?: "")
        val line = "retrieval outcome=${outcome.name()}$reason follow_up=$followUp cold=$coldEngine total_ms=${ms(result.timings.totalMs)}"
        if (error == null) logger.i(TAG) { line } else logger.w(TAG) { line }
        return result
    }

    private fun log(result: RetrievalResult, search: Search) {
        val blocks = (result.outcome as? RetrievalOutcome.Blocks)?.blocks.orEmpty()
        val t = result.timings
        logger.i(TAG) {
            "retrieval outcome=${result.outcome.name()} follow_up=${result.followUp} cuts=${result.embeddingCuts} " +
                "vector=${search.vectorHits} text=${search.textHits} fused=${search.fused} blocks=${blocks.size} " +
                "tokens=${blocks.sumOf { it.tokens }} cold=${t.coldEngine} embed_ms=${ms(t.embeddingMs)} vector_ms=${ms(t.vectorMs)} " +
                "text_ms=${ms(t.textMs)} select_ms=${ms(t.selectionMs)} total_ms=${ms(t.totalMs)}"
        }
    }

    private fun RetrievalOutcome.name(): String = javaClass.simpleName

    private fun ms(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private class Search(
        val embedding: QueryEmbedding,
        val vectorHits: Int,
        val textHits: Int,
        val fused: Int,
        val outcome: RetrievalOutcome,
        val embeddingMs: Double,
        val vectorMs: Double,
        val textMs: Double,
        val selectionMs: Double,
    )

    /** Marks a failure of the query engine, to tell it from the index's. */
    private class EmbeddingFailure(cause: Throwable) : Exception(cause)

    private class Timed<T>(val value: T, val ms: Double)

    private suspend fun <T> timed(block: suspend () -> T): Timed<T> {
        val started = clock()
        val value = block()
        return Timed(value, elapsedMs(started))
    }

    private fun elapsedMs(startedNanos: Long): Double = (clock() - startedNanos) / NANOS_PER_MS

    private companion object {
        const val TAG = "MicroRAG"
        const val NANOS_PER_MS = 1_000_000.0
    }
}

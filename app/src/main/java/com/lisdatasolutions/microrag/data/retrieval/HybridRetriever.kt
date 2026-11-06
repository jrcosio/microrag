package com.lisdatasolutions.microrag.data.retrieval

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.PageFollowUp
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.rag.ScoredPage
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.domain.retrieval.CandidateScore
import com.lisdatasolutions.microrag.domain.retrieval.ContextRetriever
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalLists
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
    /** F14 (research R10, contract G8): the vector of a follow-up's page search. */
    private val pageFollowUp: PageFollowUp = RagParameters.PAGE_FOLLOW_UP,
) : ContextRetriever {

    override suspend fun retrieve(request: RetrievalRequest): RetrievalResult {
        val started = clock()
        val question = request.question
        // Reading whether it is loaded never loads it; outcomes without a search report it too.
        val coldEngine = !embedder.loaded
        if (question.isBlank()) return unsearched(started, question, RetrievalOutcome.EmptyQuestion, coldEngine = coldEngine)

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
        if (state != IndexCompatibility.Compatible) {
            return unsearched(started, searchText, RetrievalOutcome.IndexUnavailable(state), followUp, coldEngine)
        }
        if (!modelReady()) return unsearched(started, searchText, RetrievalOutcome.ModelsNotReady, followUp, coldEngine)

        val search = try {
            search(question, request.previousQuestion.takeIf { followUp }, searchText, request.scope, request.withPages)
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
                pagesMs = search.pagesMs,
            ),
            outcome = search.outcome,
            lists = search.lists,
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

    /**
     * The lexical search overlaps the embedding (rule Q7); only the embedded text may be cut (rule Q8).
     * F17: both searches and the selection keep to [scope]. F20 (research R2): with [withPages], the page search
     * runs with the same vector, beside the vector search, and is never fused (D-076).
     */
    private suspend fun search(question: String, previous: String?, searchText: String, scope: SearchScope, withPages: Boolean): Search = coroutineScope {
        val text = async { timed { index.textSearch(searchText, RagParameters.CANDIDATES, scope) } }
        val embedding = timed {
            try {
                embedder.embed(question, previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw EmbeddingFailure(e)
            }
        }
        val pages = if (withPages) async { timed { pageSearch(question, previous, embedding.value.vector, scope) } } else null
        val vector = timed { index.vectorSearch(embedding.value.vector, RagParameters.CANDIDATES, scope) }
        val lexical = text.await()
        val pageHits = pages?.await()
        val fused = ReciprocalRankFusion.fuse(listOf(vector.value.map { it.id }, lexical.value), RagParameters.RRF_K)
        val selection = timed { select(fused.take(RagParameters.FUSED_LIMIT), vector.value, scope, pageHits?.value.orEmpty()) }
        Search(
            embedding = embedding.value, vectorHits = vector.value.size, textHits = lexical.value.size, fused = fused.size,
            outcome = selection.value, embeddingMs = embedding.ms, vectorMs = vector.ms, textMs = lexical.ms, selectionMs = selection.ms,
            pageHits = pageHits?.value?.size, pagesMs = pageHits?.ms, lists = RetrievalLists(vector.value, lexical.value),
        )
    }

    /**
     * F14 (research R10, contract G8): a follow-up's page search. Only when the follow-up joined the previous question
     * ([previous] not null) and the lever is not [PageFollowUp.JOINED] does it embed the new question alone, once more.
     */
    private suspend fun pageSearch(question: String, previous: String?, joined: FloatArray, scope: SearchScope): List<ScoredPage> {
        val k = RagParameters.PAGE_CANDIDATES
        if (previous == null || pageFollowUp == PageFollowUp.JOINED) return index.pageSearch(joined, k, scope)
        val alone = embedder.embed(question, null).vector
        if (pageFollowUp == PageFollowUp.NEW_ONLY) return index.pageSearch(alone, k, scope)
        return (index.pageSearch(joined, k, scope) + index.pageSearch(alone, k, scope))
            .groupBy { it.documentId to it.page }.values.map { same -> same.minBy { it.distance } }
            .sortedBy { it.distance }.take(k)
    }

    /** The first FUSED_LIMIT candidates, loaded only if still searchable, and the selector (FR-015); [pages] go apart. */
    private suspend fun select(fused: List<FusedCandidate>, vector: List<ScoredChunk>, scope: SearchScope, pages: List<ScoredPage>): RetrievalOutcome {
        val views = index.loadChunks(fused.map { it.id }, searchableOnly = true, scope = scope)
        val blocks = selector.select(fused.mapNotNull { views[it.id] }) { document, ordinal ->
            index.neighbor(document, ordinal, searchableOnly = true, scope = scope)
        }
        if (blocks.isEmpty() && pages.isEmpty()) return RetrievalOutcome.NoResults
        val selected = blocks.flatMapTo(HashSet()) { it.chunkIds }
        return RetrievalOutcome.Blocks(blocks, fused.mapIndexed { i, candidate -> candidate.score(i + 1, vector, selected) }, pages)
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
        coldEngine: Boolean,
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
                "text_ms=${ms(t.textMs)} select_ms=${ms(t.selectionMs)} total_ms=${ms(t.totalMs)}" +
                // F20 (research R18): only when the pages were searched, so the line without them is today's.
                (search.pageHits?.let { " page_hits=$it pages_ms=${ms(t.pagesMs ?: 0.0)}" } ?: "")
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
        val pageHits: Int?,
        val pagesMs: Double?,
        val lists: RetrievalLists,
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

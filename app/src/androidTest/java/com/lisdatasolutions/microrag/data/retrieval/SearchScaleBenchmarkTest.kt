package com.lisdatasolutions.microrag.data.retrieval

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.rag.DefaultRagIndex
import com.lisdatasolutions.microrag.data.rag.toLeBlob
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.rag.retrieval.FollowUpRule
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.InstalledEmbeddingModel
import com.lisdatasolutions.microrag.testing.bench.BenchBank
import com.lisdatasolutions.microrag.testing.bench.BenchReport
import com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample
import com.lisdatasolutions.microrag.testing.bench.DevFiles
import com.lisdatasolutions.microrag.testing.bench.DeviceConditions
import com.lisdatasolutions.microrag.testing.bench.FixedModels
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T020 (research R2 and R3, contract B2, US1, FR-006–FR-008, FR-013, SC-002, SC-003): the search over banks of
 * 10k, 50k and 100k chunks built on the desktop, in their own databases. Per bank: the vector search alone and inside
 * the hybrid one, the lexical one, the whole retrieval and a folder's, with the exact search and with the INT8 copy;
 * the cost of building and preloading the copy, its memory and its Recall@20 against F32, with and without the exact
 * reorder; and, on the 10k bank, the exact vector search over its first 1k, 2.5k and 5k chunks, to fix the size from
 * which the app uses INT8. Every question the bank has, in [rounds] rounds. Skips when `dev-bench` has no bank.
 *
 * Arguments: `f14.bank` (bank-10000,…), `f14.rounds` (2), `f14.embedder` (installed, 270m or none) and
 * `f14.candidates` (the INT8 preselections to reorder, the app's first: `100,50`).
 */
@RunWith(AndroidJUnit4::class)
class SearchScaleBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val banks = arguments.getString("f14.bank")?.split(",") ?: listOf("bank-10000", "bank-50000", "bank-100000")
    private val rounds = arguments.getString("f14.rounds")?.toInt() ?: 2
    private val embedderChoice = arguments.getString("f14.embedder") ?: "installed"
    private val candidateCounts = arguments.getString("f14.candidates")?.split(",")?.map { it.toInt() } ?: listOf(RagParameters.INT8_CANDIDATES)

    @Test
    fun searchAtScale() = runBlocking<Unit> {
        val available = banks.mapNotNull { BenchBank.inDevBench(context, it) }
        assumeTrue("no bank in dev-bench", available.isNotEmpty())
        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(com.lisdatasolutions.microrag.testing.CapturingLogger())
        try {
            val embedder = embedder(coordinator, LiteRtEngineFactory(context, dispatchers))
            available.forEach { bank -> measure(bank, embedder?.second, embedder?.first) }
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    /** The query engine the bench uses, with its label: the installed text embedder, or the 270M from `dev-models`. */
    private fun embedder(coordinator: EngineCoordinator, factory: LiteRtEngineFactory): Pair<String, EngineQueryEmbedder>? = when (embedderChoice) {
        "none" -> null
        "270m" -> DevFiles.verified(context, DevFiles.EG2_270M)?.let { file ->
            "EG2_270M" to EngineQueryEmbedder(coordinator, factory, FixedModels(mapOf(ModelId.EG2_270M to file), mapOf(ModelId.EG2_270M to ComputeBackend.CPU)))
        }
        else -> InstalledEmbeddingModel(context).takeIf { it.isInstalled }?.let { models ->
            models.id.name to EngineQueryEmbedder(coordinator, factory, models, textEmbedder = { models.id })
        }
    }

    private suspend fun measure(bank: BenchBank, embedder: EngineQueryEmbedder?, embedderLabel: String?) {
        check(bank.verify()) { "${bank.dir.name} does not match its manifest" }
        val report = BenchReport(context, "search-${bank.size}", sqliteVector = "?")
        report.start = DeviceConditions.snapshot(context)
        embedderLabel?.let { report.model(it, null, "CPU", null) }
        val need = bank.size.toLong() * BYTES_PER_CHUNK
        val existing = java.io.File(context.cacheDir, "${BenchBank.CACHE}/${bank.dir.name}.db").isFile
        if (!existing && context.cacheDir.usableSpace < need) {
            report.status = BenchReport.STATUS_NO_SPACE
            report.reason = "free=${context.cacheDir.usableSpace} need=$need"
            report.end = DeviceConditions.snapshot(context)
            report.write()
            return
        }
        val questions = bank.questions()
        val vectors = bank.queryVectors()
        val loadStarted = System.nanoTime()
        val opened = bank.open()
        val loadMs = ms(loadStarted)
        report.sqliteVector = opened.vectorVersion()
        val folderScope = SearchScope.Only(setOf(checkNotNull(opened.index.folders().folders.first()).id), general = false, documentIds = emptySet())

        // The exact search: a fresh connection never has the INT8 copy preloaded.
        val exactTop = mutableMapOf<Int, List<Long>>()
        val f32 = report.variant("f32", mapOf("bank" to "${bank.size}", "embedder" to (embedderLabel ?: "none")))
        f32.extra["loadMs"] = JsonPrimitive(loadMs)
        f32.extra["reusedDatabase"] = JsonPrimitive(opened.reused)
        f32.extra["dbBytes"] = JsonPrimitive(opened.bytes())
        searchRounds(f32, opened.index, questions, vectors, embedder, folderScope) { i, ids -> exactTop.putIfAbsent(i, ids) }

        // INT8: rebuilt and preloaded (what a stale copy costs, and how long a search waits for it), then preload only.
        val rebuildStarted = System.nanoTime()
        val rebuilt = opened.index.refreshQuantized(minChunks = 1)
        val rebuildMs = ms(rebuildStarted)
        opened.close()
        val reopened = bank.open(int8Candidates = candidateCounts.first())
        val preloadStarted = System.nanoTime()
        val preloaded = reopened.index.refreshQuantized(minChunks = 1)
        val preloadMs = ms(preloadStarted)
        val int8 = report.variant("int8-rerank", mapOf("bank" to "${bank.size}", "candidates" to "${candidateCounts.first()}"))
        int8.extra["rebuildAndPreloadMs"] = JsonPrimitive(rebuildMs)
        int8.extra["preloadMs"] = JsonPrimitive(preloadMs)
        int8.extra["usable"] = JsonPrimitive(rebuilt.usable && preloaded.usable)
        int8.extra["dbBytes"] = JsonPrimitive(reopened.bytes())
        val rerankHits = mutableListOf<Double>()
        searchRounds(int8, reopened.index, questions, vectors, embedder, folderScope) { i, ids ->
            if (exactTop[i] != null && rerankHits.size < questions.size) rerankHits += recall(exactTop.getValue(i), ids)
        }
        int8.extra["recallAt20"] = JsonPrimitive(rerankHits.average())

        // The bare INT8 scan on its own connection, first streamed from the file, then preloaded; and its recall.
        reopened.raw { db ->
            db.initVector(EmbeddingMath.DIMENSIONS)
            val hidden = db.queryOne(HIDDEN_SQL) { it.getLong(0).toInt() } ?: 0
            fun scan(variant: BenchReport.Variant, k: Int, keep: MutableList<List<Long>>? = null) = repeat(rounds) {
                vectors.forEachIndexed { i, vector ->
                    val started = System.nanoTime()
                    val ids = db.query(DefaultRagIndex.QUANTIZED_SEARCH_SQL, vector.toLeBlob(), k + hidden, k) { it.getLong(0) }
                    variant.add(Sample("vector", ms(started), questions[i].id, DeviceConditions.thermalNow(context)))
                    keep?.add(ids)
                }
            }
            scan(report.variant("int8-nopreload", mapOf("bank" to "${bank.size}")), RagParameters.CANDIDATES)
            db.queryOne("SELECT vector_quantize_preload('chunks', 'embedding')") { }
            val bare = mutableListOf<List<Long>>()
            val int8Bare = report.variant("int8", mapOf("bank" to "${bank.size}"))
            scan(int8Bare, RECALL_K, bare)
            int8Bare.extra["recallAt20"] = JsonPrimitive(bare.take(questions.size).mapIndexed { i, ids -> recall(exactTop.getValue(i), ids) }.average())
            int8Bare.extra["preloadBytes"] = JsonPrimitive(db.queryOne("SELECT vector_quantize_memory('chunks', 'embedding')") { it.getLong(0) } ?: -1L)
        }
        reopened.close()

        // Other preselections for the exact reorder, on the copy already built: their cost and their recall.
        candidateCounts.drop(1).forEach { candidates ->
            bank.open(int8Candidates = candidates).use { other ->
                other.index.refreshQuantized(minChunks = 1)
                val variant = report.variant("int8-rerank", mapOf("bank" to "${bank.size}", "candidates" to "$candidates"))
                val hits = mutableListOf<Double>()
                searchRounds(variant, other.index, questions, vectors, embedder, folderScope) { i, ids ->
                    if (exactTop[i] != null && hits.size < questions.size) hits += recall(exactTop.getValue(i), ids)
                }
                variant.extra["recallAt20"] = JsonPrimitive(hits.average())
            }
        }

        // The exact vector search over the first chunks of the 10k bank: from which size it passes 20 ms (FR-008); and
        // the INT8 one with the reorder on the same subset, from which size it is the faster.
        if (bank.size == SUBSETS_OF) {
            SUBSETS.forEach { limit ->
                bank.open(limit, int8Candidates = candidateCounts.first()).use { subset ->
                    suspend fun vectorRounds(variant: BenchReport.Variant) = repeat(rounds) {
                        vectors.forEachIndexed { i, vector ->
                            val started = System.nanoTime()
                            subset.index.vectorSearch(vector, RagParameters.CANDIDATES)
                            variant.add(Sample("vector", ms(started), questions[i].id, DeviceConditions.thermalNow(context)))
                        }
                    }
                    vectorRounds(report.variant("f32-subset", mapOf("bank" to "$limit")))
                    subset.index.refreshQuantized(minChunks = 1)
                    vectorRounds(report.variant("int8-subset", mapOf("bank" to "$limit", "candidates" to "${candidateCounts.first()}")))
                }
            }
        }
        report.end = DeviceConditions.snapshot(context)
        report.write()
    }

    /**
     * Every question in [rounds] rounds: the vector search alone (its top 20 handed to [top] in the first round), the
     * lexical one alone, and with an engine the embedding alone, the whole retrieval and a folder's.
     */
    private suspend fun searchRounds(
        variant: BenchReport.Variant,
        index: DefaultRagIndex,
        questions: List<BenchBank.Question>,
        vectors: List<FloatArray>,
        embedder: EngineQueryEmbedder?,
        folderScope: SearchScope.Only,
        top: (Int, List<Long>) -> Unit,
    ) {
        val retriever = embedder?.let { HybridRetriever(index, it, com.lisdatasolutions.microrag.testing.CapturingLogger()) }
        repeat(rounds) { round ->
            questions.forEachIndexed { i, q ->
                val thermal = DeviceConditions.thermalNow(context)
                var started = System.nanoTime()
                val hits = index.vectorSearch(vectors[i], RagParameters.CANDIDATES)
                variant.add(Sample("vector", ms(started), q.id, thermal))
                if (round == 0) top(i, hits.take(RECALL_K).map { it.id })
                started = System.nanoTime()
                index.textSearch(FollowUpRule.searchText(q.question, q.previous), RagParameters.CANDIDATES)
                variant.add(Sample("fts", ms(started), q.id, thermal))
                if (retriever != null && embedder != null) {
                    started = System.nanoTime()
                    embedder.embed(q.question, q.previous?.takeIf { FollowUpRule.searchText(q.question, it) != q.question })
                    variant.add(Sample("embed", ms(started), q.id, thermal))
                    val result = retriever.retrieve(RetrievalRequest(q.question, q.previous))
                    variant.add(Sample("embed-in-search", result.timings.embeddingMs, q.id, thermal))
                    variant.add(Sample("vector-in-hybrid", result.timings.vectorMs, q.id, thermal))
                    variant.add(Sample("fts-in-hybrid", result.timings.textMs, q.id, thermal))
                    variant.add(Sample("hybrid", result.timings.totalMs, q.id, thermal))
                    val scoped = retriever.retrieve(RetrievalRequest(q.question, q.previous, folderScope))
                    variant.add(Sample("hybrid-folder", scoped.timings.totalMs, q.id, thermal))
                }
            }
        }
    }

    private fun recall(exact: List<Long>, found: List<Long>): Double =
        if (exact.isEmpty()) 1.0 else found.take(RECALL_K).count { it in exact.toSet() }.toDouble() / exact.size

    private fun ms(startedNanos: Long): Double = (System.nanoTime() - startedNanos) / 1e6

    private companion object {
        const val RECALL_K = 20
        const val SUBSETS_OF = 10_000
        val SUBSETS = listOf(1_000, 2_500, 5_000, 7_500)

        /** About what a chunk takes in the database with its text, vector, FTS rows and INT8 copy. */
        const val BYTES_PER_CHUNK = 6_000L
        const val HIDDEN_SQL = "SELECT COALESCE(SUM((SELECT count(*) FROM chunks c WHERE c.document_id = d.id)), 0) FROM documents d " +
            "WHERE d.status <> 'done' OR d.included = 0"
    }
}

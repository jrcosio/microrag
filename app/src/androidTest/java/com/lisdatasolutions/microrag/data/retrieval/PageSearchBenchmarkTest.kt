package com.lisdatasolutions.microrag.data.retrieval

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.data.rag.toFloatArrayLe
import com.lisdatasolutions.microrag.data.rag.toLeBlob
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.bench.BenchBank
import com.lisdatasolutions.microrag.testing.bench.BenchReport
import com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample
import com.lisdatasolutions.microrag.testing.bench.DevFiles
import com.lisdatasolutions.microrag.testing.bench.DeviceConditions
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T037 (research R10, contract B2, US6, FR-023): the page search over banks of 1k, 5k and 10k page vectors (the
 * F18 visual corpus's real pages, perturbed on the desktop), in a temporary index. The exact search is the app's;
 * the INT8 one is SQL on the same file, because the app's INT8 route for pages is built only if these figures ask for
 * it. The queries are real pages with noise, as a question about a page would land near it.
 */
@RunWith(AndroidJUnit4::class)
class PageSearchBenchmarkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rounds = InstrumentationRegistry.getArguments().getString("f14.rounds")?.toInt() ?: 2

    @Test
    fun pageSearchAtScale() = runBlocking<Unit> {
        val banks = BANKS.mapNotNull { name -> File(DevFiles.benchDir(context), name).takeIf { File(it, BenchBank.MANIFEST).isFile } }
        assumeTrue("no page bank in dev-bench", banks.isNotEmpty())
        banks.forEach { measure(it) }
    }

    private suspend fun measure(dir: File) {
        val manifest = Json.parseToJsonElement(File(dir, BenchBank.MANIFEST).readText()).jsonObject
        val pages = manifest.getValue("m").jsonPrimitive.int
        val vectorsFile = File(dir, "vectors.f32")
        check(DevFiles.sha256(vectorsFile) == manifest.getValue("files").jsonObject.getValue("vectors.f32").jsonPrimitive.content) { "${dir.name} changed" }
        val vectors = BenchBank.vectors(vectorsFile, pages)
        val report = BenchReport(context, "pages-$pages", sqliteVector = "?")
        report.start = DeviceConditions.snapshot(context)
        TempRagIndex().use { temp ->
            vectors.chunked(PAGES_PER_DOCUMENT).forEachIndexed { d, docPages ->
                val doc = temp.index.addDocument(NewDocument(displayName = "Páginas $d.pdf", contentHash = "pages-$d", indexMode = IndexMode.VISUAL))
                docPages.forEachIndexed { p, vector -> temp.index.insertPage(doc, PageInput(p + 1, "p$d-$p.jpg", WIDTH, HEIGHT, SIZE, vector)) }
                temp.index.updateStatus(doc, DocumentStatus.DONE, chunkCount = 0)
            }
            val random = Random(SEED)
            val queries = (0 until QUERIES).map { i ->
                val base = vectors[random.nextInt(vectors.size)]
                EmbeddingMath.truncateAndNormalize(FloatArray(base.size) { base[it] + (random.nextFloat() - 0.5f) * NOISE })
            }
            report.sqliteVector = temp.withRaw { db -> db.queryOne("SELECT vector_version()") { it.getText(0) } ?: "?" }

            val exact = report.variant("f32", mapOf("pages" to "$pages"))
            val exactTop = mutableListOf<List<Int>>()
            repeat(rounds) { round ->
                queries.forEach { q ->
                    val started = System.nanoTime()
                    val hits = temp.index.pageSearch(q, RagParameters.PAGE_CANDIDATES)
                    exact.add(Sample("page-search", ms(started), thermal = DeviceConditions.thermalNow(context)))
                    if (round == 0) exactTop += hits.map { it.page * 100_000 + it.documentId.toInt() }
                }
            }

            temp.withRaw { db ->
                db.initVector(EmbeddingMath.DIMENSIONS)
                val variant = report.variant("int8-rerank", mapOf("pages" to "$pages", "candidates" to "${RagParameters.INT8_CANDIDATES}"))
                val started = System.nanoTime()
                db.queryOne("SELECT vector_quantize('pages', 'embedding', 'qtype=INT8,max_memory=${RagParameters.INT8_MAX_MEMORY_MB}MB')") { }
                db.queryOne("SELECT vector_quantize_preload('pages', 'embedding')") { }
                variant.extra["quantizeAndPreloadMs"] = JsonPrimitive(ms(started))
                val found = mutableListOf<List<Int>>()
                repeat(rounds) { round ->
                    queries.forEach { q ->
                        val t = System.nanoTime()
                        val candidates = db.query(INT8_PAGES_SQL, q.toLeBlob(), RagParameters.INT8_CANDIDATES) { it.getLong(0) }
                        val top = db.query(
                            "SELECT p.id, p.page, p.document_id, p.embedding FROM pages p WHERE p.id IN (${candidates.joinToString(",") { "?" }})",
                            *candidates.toTypedArray(),
                        ) { row -> Triple(row.getLong(2).toInt() + row.getLong(1).toInt() * 100_000, 1.0 - EmbeddingMath.dot(q, row.getBlob(3).toFloatArrayLe()), row.getLong(0)) }
                            .sortedWith(compareBy({ it.second }, { it.third })).take(RagParameters.PAGE_CANDIDATES).map { it.first }
                        variant.add(Sample("page-search", ms(t), thermal = DeviceConditions.thermalNow(context)))
                        if (round == 0) found += top
                    }
                }
                variant.extra["recallAt5"] = JsonPrimitive(found.indices.map { i -> found[i].count { it in exactTop[i] }.toDouble() / exactTop[i].size }.average())
            }
        }
        report.end = DeviceConditions.snapshot(context)
        report.write()
    }

    private fun ms(startedNanos: Long): Double = (System.nanoTime() - startedNanos) / 1e6

    private companion object {
        val BANKS = listOf("pages-1000", "pages-5000", "pages-10000")
        const val PAGES_PER_DOCUMENT = 20
        const val QUERIES = 100
        const val SEED = 2026
        const val NOISE = 0.04f
        const val WIDTH = 543
        const val HEIGHT = 768
        const val SIZE = 90_000L
        const val INT8_PAGES_SQL = "WITH v AS MATERIALIZED (SELECT rowid, distance FROM vector_quantize_scan('pages', 'embedding', ?)) " +
            "SELECT v.rowid FROM v JOIN pages p ON p.id = v.rowid JOIN documents d ON d.id = p.document_id " +
            "WHERE d.status = 'done' AND d.included = 1 ORDER BY v.distance LIMIT ?"
    }
}

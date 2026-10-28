package com.lisdatasolutions.microrag.data.library

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.Percentiles
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F08 T022 (research R11, SC-001): the Library page over 1,000 synthetic documents in a real index.
 * Measures the first page with its count, each next block of 50 as the list grows (research R2), the
 * whole list and a name search; then the same with 50,000 chunks stored, which must not change the
 * times because the page never reads them (rule L1).
 */
@RunWith(AndroidJUnit4::class)
class LibraryBenchmarkTest {

    private val all = DocumentStatus.entries.toSet()

    @Test
    fun libraryPagesWith1000Documents() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            val index = temp.index
            val ids = (0 until DOCUMENTS).map { d ->
                temp.now += 1
                val id = index.addDocument(
                    NewDocument(displayName = name(d), contentHash = "bench-$d", mimeType = "application/pdf", sizeBytes = 1_000_000L + d),
                )
                index.updateStatus(id, STATUSES[d % STATUSES.size], chunkCount = if (STATUSES[d % STATUSES.size] == DocumentStatus.DONE) 1_000 else null)
                id
            }
            val empty = measureAll(index, "chunks=0")

            // 50 documents of 1,000 chunks each: the page must cost the same.
            val insertStarted = System.nanoTime()
            ids.filterIndexed { d, _ -> STATUSES[d % STATUSES.size] == DocumentStatus.DONE }.take(CHUNKED_DOCUMENTS).forEachIndexed { n, id ->
                (0 until CHUNKS_PER_DOCUMENT).chunked(BATCH).forEach { ordinals ->
                    index.insertChunks(id, ordinals.map { o -> chunk(o, content = "Fragmento $o del documento $n.", vector = unitVector(n * 10_000 + o)) })
                }
            }
            assertThat(index.counts().chunks).isEqualTo(CHUNKED_DOCUMENTS * CHUNKS_PER_DOCUMENT)
            Log.i(TAG, "library benchmark chunks inserted=${CHUNKED_DOCUMENTS * CHUNKS_PER_DOCUMENT} in %.1f s".format(Locale.ROOT, (System.nanoTime() - insertStarted) / 1e9))
            val full = measureAll(index, "chunks=${CHUNKED_DOCUMENTS * CHUNKS_PER_DOCUMENT}")

            // SC-001: first page within 1 s and each next block within 100 ms, p95.
            listOf(empty, full).forEach { result ->
                assertThat(Percentiles.of(result.first, 95)).isLessThan(1_000.0)
                assertThat(Percentiles.of(result.blocks, 95)).isLessThan(100.0)
            }
        }
    }

    private class Result(val first: List<Double>, val blocks: List<Double>)

    private suspend fun measureAll(index: RagIndex, label: String): Result {
        val first = measure(RUNS) { page(index, "", LibraryQuery.PAGE) }
        // Each block asks again for everything shown so far, as the ViewModel does (research R2).
        val blocks = (2..DOCUMENTS / LibraryQuery.PAGE).flatMap { block ->
            measure(BLOCK_RUNS) { page(index, "", block * LibraryQuery.PAGE) }
        }
        val whole = measure(BLOCK_RUNS) { page(index, "", DOCUMENTS) }
        val search = measure(RUNS) { page(index, "manual", LibraryQuery.PAGE) }
        assertThat(index.libraryPage("", all, DOCUMENTS)).hasSize(DOCUMENTS)
        Log.i(TAG, "library benchmark device=${Build.MODEL} api=${Build.VERSION.SDK_INT} documents=$DOCUMENTS $label")
        Log.i(TAG, "library benchmark first page+count ${summary(first)}")
        Log.i(TAG, "library benchmark next blocks (100…1000 rows) ${summary(blocks)}")
        Log.i(TAG, "library benchmark whole list (1000 rows) ${summary(whole)}")
        Log.i(TAG, "library benchmark search page+count ${summary(search)}")
        return Result(first, blocks)
    }

    private suspend fun page(index: RagIndex, text: String, limit: Int) {
        index.libraryPage(text, all, limit)
        index.libraryCount(text, all)
    }

    private suspend fun measure(runs: Int, block: suspend () -> Unit): List<Double> {
        repeat(WARMUP) { block() }
        return (0 until runs).map {
            val started = System.nanoTime()
            block()
            (System.nanoTime() - started) / 1e6
        }
    }

    private fun summary(ms: List<Double>) = "p50=%.2f ms p95=%.2f ms max=%.2f ms n=%d".format(
        Locale.ROOT, Percentiles.of(ms, 50), Percentiles.of(ms, 95), ms.max(), ms.size,
    )

    /** Synthetic names with accents, so the folded column is used as in real libraries. */
    private fun name(d: Int) = "${PREFIXES[d % PREFIXES.size]} ${d / PREFIXES.size}.pdf"

    private companion object {
        const val TAG = "MicroRAG"
        const val DOCUMENTS = 1_000
        const val CHUNKED_DOCUMENTS = 50
        const val CHUNKS_PER_DOCUMENT = 1_000
        const val BATCH = 64
        const val WARMUP = 3
        const val RUNS = 50
        const val BLOCK_RUNS = 10

        val PREFIXES = listOf("Manual de procesos", "Guía rápida", "Informe anual", "Catálogo técnico", "Normativa interna", "Acta de reunión")
        val STATUSES = listOf(
            DocumentStatus.DONE, DocumentStatus.DONE, DocumentStatus.DONE, DocumentStatus.DONE, DocumentStatus.PENDING,
            DocumentStatus.DONE, DocumentStatus.ERROR, DocumentStatus.DONE, DocumentStatus.OUT_OF_INDEX, DocumentStatus.PARSED,
        )
    }
}

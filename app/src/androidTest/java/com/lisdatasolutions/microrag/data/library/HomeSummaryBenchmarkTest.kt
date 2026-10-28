package com.lisdatasolutions.microrag.data.library

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.Percentiles
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F09 T021 (research R8, SC-005): the Home summary over 1,000 synthetic documents and 50,000 stored
 * chunks, the data of LibraryBenchmarkTest. One query on `documents`: p95 within 50 ms.
 */
@RunWith(AndroidJUnit4::class)
class HomeSummaryBenchmarkTest {

    @Test
    fun summaryWith1000DocumentsAnd50000Chunks() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            val index = temp.index
            var chunked = 0
            repeat(DOCUMENTS) { d ->
                temp.now += 1
                val id = index.addDocument(
                    NewDocument(displayName = "Documento $d.pdf", contentHash = "home-bench-$d", mimeType = "application/pdf", sizeBytes = 1_000_000L + d),
                )
                val status = STATUSES[d % STATUSES.size]
                val withChunks = status == DocumentStatus.DONE && chunked < CHUNKED_DOCUMENTS
                if (withChunks) {
                    (0 until CHUNKS_PER_DOCUMENT).chunked(BATCH).forEach { ordinals ->
                        index.insertChunks(id, ordinals.map { o -> chunk(o, content = "Fragmento $o del documento $d.", vector = unitVector(chunked * 10_000 + o)) })
                    }
                    chunked++
                }
                index.updateStatus(id, status, chunkCount = if (withChunks) CHUNKS_PER_DOCUMENT else null)
            }
            assertThat(index.librarySummary()).isEqualTo(LibrarySummary(DOCUMENTS, CHUNKED_DOCUMENTS * CHUNKS_PER_DOCUMENT))

            repeat(WARMUP) { index.librarySummary() }
            val times = (0 until RUNS).map {
                val started = System.nanoTime()
                index.librarySummary()
                (System.nanoTime() - started) / 1e6
            }

            Log.i(TAG, "home summary benchmark device=${Build.MODEL} api=${Build.VERSION.SDK_INT} documents=$DOCUMENTS chunks=${CHUNKED_DOCUMENTS * CHUNKS_PER_DOCUMENT}")
            Log.i(
                TAG,
                "home summary benchmark p50=%.2f ms p95=%.2f ms max=%.2f ms n=%d".format(
                    Locale.ROOT, Percentiles.of(times, 50), Percentiles.of(times, 95), times.max(), times.size,
                ),
            )
            assertThat(Percentiles.of(times, 95)).isLessThan(50.0)
        }
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val DOCUMENTS = 1_000
        const val CHUNKED_DOCUMENTS = 50
        const val CHUNKS_PER_DOCUMENT = 1_000
        const val BATCH = 64
        const val WARMUP = 5
        const val RUNS = 50

        val STATUSES = listOf(
            DocumentStatus.DONE, DocumentStatus.DONE, DocumentStatus.DONE, DocumentStatus.DONE, DocumentStatus.PENDING,
            DocumentStatus.DONE, DocumentStatus.ERROR, DocumentStatus.DONE, DocumentStatus.OUT_OF_INDEX, DocumentStatus.PARSED,
        )
    }
}

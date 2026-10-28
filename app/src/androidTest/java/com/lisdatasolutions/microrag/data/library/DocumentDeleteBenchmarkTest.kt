package com.lisdatasolutions.microrag.data.library

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempLibrary
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F08 T043 (research R11, SC-009): the cost of the three big writes of the Library over a document of
 * 4,300 chunks of 256 dimensions (a 2,000-page PDF, F07 research R7), next to another 4,300 chunks that
 * stay. Each one deletes 4,300 FTS rows by trigger. Measured, and reported if over 5 s.
 */
@RunWith(AndroidJUnit4::class)
class DocumentDeleteBenchmarkTest {

    private val library = TempLibrary()
    private val index = library.temp.index

    @After
    fun tearDown() = library.close()

    private var seed = 0

    private suspend fun indexed(name: String): Long {
        val id = index.addDocument(
            NewDocument(displayName = name, contentHash = "$name-${seed++}", mimeType = "application/pdf", localPath = library.copy("$name.pdf", name).path),
        )
        store(id)
        index.updateStatus(id, DocumentStatus.DONE, chunkCount = CHUNKS)
        return id
    }

    private suspend fun store(id: Long) {
        val base = seed++ * 10_000
        (0 until CHUNKS).chunked(BATCH).forEach { ordinals ->
            index.insertChunks(id, ordinals.map { o -> chunk(o, content = "Texto del fragmento $o de la página ${o / 2 + 1} con la bomba y la válvula.", vector = unitVector(base + o)) })
        }
    }

    private suspend fun timed(block: suspend () -> Unit): Double {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1e6
    }

    @Test
    fun deletingRemovingAndPublishing4300Chunks() = runBlocking<Unit> {
        indexed("Otro")
        val delete = mutableListOf<Double>()
        val remove = mutableListOf<Double>()
        val publish = mutableListOf<Double>()
        repeat(RUNS) {
            val a = indexed("Borrar")
            delete += timed { library.repository.delete(a) }

            val b = indexed("Quitar")
            remove += timed { library.repository.removeFromIndex(b) }
            index.deleteDocument(b)

            val c = indexed("Reindexar")
            val shadow = index.addReindexShadow(c)
            store(shadow)
            index.updateStatus(shadow, DocumentStatus.EMBEDDING, chunkCount = CHUNKS)
            publish += timed { assertThat(index.publishReindex(shadow)).isEqualTo(CHUNKS) }
            index.deleteDocument(c)
        }
        val counts = index.counts()
        assertThat(counts.chunks).isEqualTo(CHUNKS)
        assertThat(counts.ftsRows).isEqualTo(CHUNKS)

        Log.i(TAG, "delete benchmark device=${Build.MODEL} api=${Build.VERSION.SDK_INT} chunks=$CHUNKS runs=$RUNS")
        Log.i(TAG, "delete benchmark delete ${summary(delete)}")
        Log.i(TAG, "delete benchmark out-of-index ${summary(remove)}")
        Log.i(TAG, "delete benchmark publish reindex ${summary(publish)}")
    }

    private fun summary(ms: List<Double>) =
        "min=%.0f ms max=%.0f ms mean=%.0f ms".format(Locale.ROOT, ms.min(), ms.max(), ms.average())

    private companion object {
        const val TAG = "MicroRAG"
        const val CHUNKS = 4_300
        const val BATCH = 64
        const val RUNS = 3
    }
}

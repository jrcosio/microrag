package com.lisdatasolutions.microrag.data.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempLibrary
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F08 T024 (SC-003) on SQLite-Vector: the Library switch reaches the searches. An excluded document is
 * not found by vector or text search while its chunks stay stored; included again, it is found again.
 */
@RunWith(AndroidJUnit4::class)
class InclusionSearchTest {

    private val library = TempLibrary()
    private val index = library.temp.index

    @After
    fun tearDown() = library.close()

    private suspend fun doc(name: String, seed: Int, word: String): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        index.insertChunks(id, (0 until CHUNKS).map { chunk(it, content = "Texto sobre $word número $it", vector = unitVector(seed * 100 + it)) })
        index.updateStatus(id, DocumentStatus.DONE, chunkCount = CHUNKS)
        return id
    }

    private suspend fun documentsFound(seed: Int, word: String): Set<Long> {
        val vector = index.vectorSearch(unitVector(seed * 100), k = 10).map { it.id }
        val text = index.textSearch(word, k = 10)
        return index.loadChunks(vector + text).values.mapTo(HashSet()) { it.documentId }
    }

    @Test
    fun excluding_hidesTheDocumentFromSearches_andIncludingBringsItBack() = runBlocking<Unit> {
        val manual = doc("Manual.txt", seed = 1, word = "turbinas")
        val other = doc("Otro.txt", seed = 2, word = "bombas")
        assertThat(documentsFound(seed = 1, word = "turbinas")).contains(manual)

        library.repository.setIncluded(manual, false)

        assertThat(documentsFound(seed = 1, word = "turbinas")).doesNotContain(manual)
        assertThat(index.textSearch("turbinas", k = 10)).isEmpty()
        assertThat(index.storedChunkCounts(listOf(manual)).getValue(manual)).isEqualTo(CHUNKS)
        assertThat(documentsFound(seed = 2, word = "bombas")).containsExactly(other)

        library.repository.setIncluded(manual, true)

        assertThat(documentsFound(seed = 1, word = "turbinas")).contains(manual)
        assertThat(index.vectorSearch(unitVector(100), k = 1).single().distance).isWithin(1e-4).of(0.0)
    }

    private companion object {
        const val CHUNKS = 8
    }
}

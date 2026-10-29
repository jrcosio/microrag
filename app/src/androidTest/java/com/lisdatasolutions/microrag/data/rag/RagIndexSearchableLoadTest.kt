package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F10 T007 (research R7, FR-015): with `searchableOnly`, loading candidates and neighbours follows the
 * search rule, only chunks of documents that are done and included; without it, every stored chunk, as before.
 */
@RunWith(AndroidJUnit4::class)
class RagIndexSearchableLoadTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private suspend fun doc(name: String, status: DocumentStatus, included: Boolean = true): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        index.insertChunks(id, (0 until CHUNKS).map { chunk(it, content = "$name, fragmento $it") })
        index.updateStatus(id, status)
        if (!included) index.setIncluded(id, false)
        return id
    }

    private fun chunkIds(documentId: Long): List<Long> = temp.withRaw { db ->
        db.query("SELECT id FROM chunks WHERE document_id = ? ORDER BY ordinal", documentId) { it.getLong(0) }
    }

    @Test
    fun onlyDoneAndIncludedDocumentsLoad_whenAsked() = runBlocking<Unit> {
        val done = doc("Hecho.txt", DocumentStatus.DONE)
        val excluded = doc("Excluido.txt", DocumentStatus.DONE, included = false)
        val pending = doc("Pendiente.txt", DocumentStatus.EMBEDDING)
        val failed = doc("Con error.txt", DocumentStatus.ERROR)
        val paused = doc("En pausa.txt", DocumentStatus.PAUSED)
        val outOfIndex = doc("Fuera.txt", DocumentStatus.OUT_OF_INDEX)
        val original = doc("Original.txt", DocumentStatus.DONE)
        val shadow = index.addReindexShadow(original)
        index.insertChunks(shadow, (0 until CHUNKS).map { chunk(it, content = "Sombra, fragmento $it") })
        val documents = listOf(done, excluded, pending, failed, paused, outOfIndex, original, shadow)
        val all = documents.flatMap(::chunkIds)

        val searchable = index.loadChunks(all, searchableOnly = true)
        assertThat(searchable.keys).containsExactlyElementsIn(chunkIds(done) + chunkIds(original))
        assertThat(index.loadChunks(all).keys).containsExactlyElementsIn(all)
        assertThat(index.loadChunks(all, searchableOnly = false).keys).containsExactlyElementsIn(all)

        documents.forEach { id ->
            val expected = id == done || id == original
            assertWithMessage("document $id").that(index.neighbor(id, 1, searchableOnly = true) != null).isEqualTo(expected)
            assertWithMessage("document $id").that(index.neighbor(id, 1)).isNotNull()
        }
        assertThat(index.neighbor(done, 1, searchableOnly = true)!!.id).isEqualTo(chunkIds(done)[1])
        assertThat(index.neighbor(done, CHUNKS, searchableOnly = true)).isNull()
    }

    @Test
    fun theFilterFollowsInclusionRemovalAndPublishedReindex() = runBlocking<Unit> {
        val done = doc("Hecho.txt", DocumentStatus.DONE)
        val removed = doc("Quitado.txt", DocumentStatus.DONE)
        val original = doc("Original.txt", DocumentStatus.DONE)
        val shadow = index.addReindexShadow(original)
        index.insertChunks(shadow, (0 until CHUNKS).map { chunk(it, content = "Sombra, fragmento $it") })
        val shadowChunks = chunkIds(shadow)
        val before = listOf(done, removed, original).flatMap(::chunkIds)

        index.setIncluded(done, false)
        assertThat(index.loadChunks(chunkIds(done), searchableOnly = true)).isEmpty()
        assertThat(index.neighbor(done, 0, searchableOnly = true)).isNull()
        index.setIncluded(done, true)
        assertThat(index.loadChunks(chunkIds(done), searchableOnly = true).keys).containsExactlyElementsIn(chunkIds(done))

        assertThat(index.removeFromIndex(removed)).isEqualTo(CHUNKS)
        assertThat(index.neighbor(removed, 0, searchableOnly = true)).isNull()

        assertThat(index.loadChunks(shadowChunks, searchableOnly = true)).isEmpty()
        assertThat(index.publishReindex(shadow)).isEqualTo(CHUNKS)
        // The shadow's chunks keep their ids and now belong to the done original; the old ones are gone.
        assertThat(index.loadChunks(shadowChunks, searchableOnly = true).keys).containsExactlyElementsIn(shadowChunks)
        assertThat(index.loadChunks(before + shadowChunks, searchableOnly = true).keys)
            .containsExactlyElementsIn(chunkIds(done) + shadowChunks)
        assertThat(index.neighbor(original, 1, searchableOnly = true)!!.content).isEqualTo("Sombra, fragmento 1")
    }

    private companion object {
        const val CHUNKS = 3
    }
}

package com.lisdatasolutions.microrag.data.retrieval

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.data.indexing.FakeTextEmbedder
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F10 T033 (SC-002, SC-006, US3): on SQLite-Vector, with a deterministic query embedder, the context only
 * holds chunks of documents that are done and included, even when the others match the question better.
 */
@RunWith(AndroidJUnit4::class)
class HybridRetrieverVisibilityTest {

    private val temp = TempRagIndex()
    private val index = temp.index
    private val embedder = FakeQueryEmbedder()
    private val retriever = HybridRetriever(index, embedder, temp.logger)
    private val query = FakeTextEmbedder.vectorOf(EmbeddingPrefixes.query(QUESTION))

    @After
    fun tearDown() = temp.close()

    /** [noise] 0 would be the query vector itself: the smaller, the better the chunk matches. */
    private fun near(seed: Int, noise: Float): FloatArray {
        val other = unitVector(seed)
        return EmbeddingMath.truncateAndNormalize(FloatArray(query.size) { query[it] + noise * other[it] })
    }

    private suspend fun document(name: String, chunks: Int, noise: Float, status: DocumentStatus, included: Boolean = true): Long {
        val id = index.addDocument(NewDocument(displayName = "$name.txt", contentHash = name, mimeType = "text/plain"))
        (0 until chunks).chunked(500).forEach { batch ->
            index.insertChunks(id, batch.map {
                ChunkInput(it, null, null, "La bomba hidráulica $name, revisión $it.", 20, near(id.toInt() * 10_000 + it, noise))
            })
        }
        index.updateStatus(id, status)
        if (!included) index.setIncluded(id, false)
        return id
    }

    private fun chunkIds(documentId: Long): Set<Long> = temp.withRaw { db ->
        db.query("SELECT id FROM chunks WHERE document_id = ?", documentId) { it.getLong(0) }.toSet()
    }

    private fun blocks(outcome: RetrievalOutcome) = (outcome as RetrievalOutcome.Blocks).blocks

    @Test
    fun onlyDoneAndIncludedChunksReachTheContext_whateverTheOthersMatch() = runBlocking<Unit> {
        val done = document("hecho", 60, noise = 0.5f, status = DocumentStatus.DONE)
        val original = document("original", 10, noise = 0.5f, status = DocumentStatus.DONE)
        // Every other state matches the question better than the searchable ones.
        val hidden = listOf(
            document("excluido", 20, noise = 0.05f, status = DocumentStatus.DONE, included = false),
            document("pendiente", 20, noise = 0.05f, status = DocumentStatus.EMBEDDING),
            document("error", 20, noise = 0.05f, status = DocumentStatus.ERROR),
            document("pausa", 20, noise = 0.05f, status = DocumentStatus.PAUSED),
            document("fuera", 20, noise = 0.05f, status = DocumentStatus.OUT_OF_INDEX),
            document("a medias", HIDDEN_CHUNKS, noise = 0.05f, status = DocumentStatus.EMBEDDING),
        )
        val shadow = index.addReindexShadow(original)
        index.insertChunks(shadow, (0 until 20).map { ChunkInput(it, null, null, "La bomba hidráulica sombra, revisión $it.", 20, near(90_000 + it, 0.05f)) })
        val searchable = chunkIds(done) + chunkIds(original)

        val result = retriever.retrieve(RetrievalRequest(QUESTION))

        val chosen = blocks(result.outcome).flatMap { it.chunkIds }
        assertThat(chosen).isNotEmpty()
        assertThat(searchable).containsAtLeastElementsIn(chosen)
        (hidden + shadow).forEach { assertWithMessage("document $it").that(chunkIds(it).intersect(chosen.toSet())).isEmpty() }
        // With 4,300 hidden chunks each search still finds its 50 candidates (US3-2).
        assertThat(temp.logger.lines.last { it.startsWith("retrieval ") }).contains(" vector=50 text=50 ")
    }

    @Test
    fun excludingADocument_takesItOutOfTheNextRetrieval() = runBlocking<Unit> {
        val manual = document("manual", 10, noise = 0.1f, status = DocumentStatus.DONE)
        val other = document("otro", 10, noise = 0.5f, status = DocumentStatus.DONE)
        assertThat(blocks(retriever.retrieve(RetrievalRequest(QUESTION)).outcome).map { it.documentId }).contains(manual)

        index.setIncluded(manual, false)
        val after = blocks(retriever.retrieve(RetrievalRequest(QUESTION)).outcome)

        assertThat(after.map { it.documentId }.toSet()).containsExactly(other)
    }

    @Test
    fun anIncompatibleIndex_isUnavailable_withoutTouchingTheEngine() = runBlocking<Unit> {
        document("manual", 5, noise = 0.1f, status = DocumentStatus.DONE)
        index.close()
        temp.withRaw { it.exec("UPDATE rag_meta SET value = ? WHERE key = ?", "768", RagMetaKeys.EMBEDDING_DIMS) }

        val outcome = retriever.retrieve(RetrievalRequest(QUESTION)).outcome

        assertThat(outcome).isInstanceOf(RetrievalOutcome.IndexUnavailable::class.java)
        assertThat((outcome as RetrievalOutcome.IndexUnavailable).state).isInstanceOf(IndexCompatibility.Incompatible::class.java)
        assertThat(embedder.readyCalls.get()).isEqualTo(0)
        assertThat(embedder.embedCalls.get()).isEqualTo(0)
    }

    private companion object {
        const val QUESTION = "¿Cada cuánto hay que revisar la bomba hidráulica según el manual del fabricante?"
        const val HIDDEN_CHUNKS = 4_300
    }
}

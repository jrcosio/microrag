package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F07 T009 (research R7, rule N7, SC-003): searches only return chunks of documents that are done and
 * included, exactly the best k of them, and cost the same as F04 when nothing is half-indexed.
 */
@RunWith(AndroidJUnit4::class)
class RagIndexVisibilityTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private class Doc(val id: Long, val seed: Int)

    /** Twenty chunks per document, all mentioning «bomba», with vectors that differ per document. */
    private suspend fun doc(name: String, seed: Int, status: DocumentStatus, included: Boolean = true): Doc {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        index.insertChunks(id, (0 until CHUNKS).map { chunk(it, content = "La bomba $seed número $it", vector = unitVector(seed * 100 + it)) })
        index.updateStatus(id, status)
        if (!included) index.setIncluded(id, false)
        return Doc(id, seed)
    }

    private fun chunkIds(doc: Doc): Map<Int, Long> = temp.withRaw { db ->
        db.query("SELECT ordinal, id FROM chunks WHERE document_id = ?", doc.id) { it.getLong(0).toInt() to it.getLong(1) }.toMap()
    }

    @Test
    fun onlyDoneAndIncludedDocumentsAreFound_andTheyAreTheExactBestK() = runBlocking<Unit> {
        val done = doc("Hecho.txt", 1, DocumentStatus.DONE)
        val inProgress = doc("En curso.txt", 2, DocumentStatus.EMBEDDING)
        val parsed = doc("Troceado.txt", 3, DocumentStatus.PARSED)
        val failed = doc("Con error.txt", 4, DocumentStatus.ERROR)
        val excluded = doc("Excluido.txt", 5, DocumentStatus.DONE, included = false)
        val hidden = listOf(inProgress, parsed, failed, excluded).flatMap { chunkIds(it).values }.toSet()

        // A query close to the hidden documents: an unfiltered top-k would be all theirs.
        val query = EmbeddingMath.truncateAndNormalize(FloatArray(256) { i -> unitVector(200)[i] + unitVector(300)[i] })
        val found = index.vectorSearch(query, k = K)

        val expected = chunkIds(done).map { (ordinal, id) -> id to dot(query, unitVector(done.seed * 100 + ordinal)) }
            .sortedByDescending { it.second }.take(K).map { it.first }
        assertThat(found.map { it.id }).containsExactlyElementsIn(expected).inOrder()
        assertThat(found.map { it.id }.toSet().intersect(hidden)).isEmpty()

        val text = index.textSearch("bomba", k = 50)
        assertThat(text).isNotEmpty()
        assertThat(text.toSet()).isEqualTo(chunkIds(done).values.toSet())
    }

    @Test
    fun withNothingHidden_everyChunkCompetesAsInF04() = runBlocking<Unit> {
        val one = doc("Uno.txt", 1, DocumentStatus.DONE)
        val two = doc("Dos.txt", 2, DocumentStatus.DONE)
        val query = unitVector(150)

        val all = listOf(one, two).flatMap { d -> chunkIds(d).map { (ordinal, id) -> id to dot(query, unitVector(d.seed * 100 + ordinal)) } }
        val expected = all.sortedByDescending { it.second }.take(K)
        val found = index.vectorSearch(query, k = K)

        assertThat(found.map { it.id }).containsExactlyElementsIn(expected.map { it.first }).inOrder()
        found.zip(expected).forEach { (hit, want) -> assertThat(hit.distance).isWithin(1e-4).of(1.0 - want.second) }
    }

    @Test
    fun theVectorScanRunsOnce() = runBlocking<Unit> {
        doc("Uno.txt", 1, DocumentStatus.DONE)
        val plan = temp.withRaw { db ->
            db.query("EXPLAIN QUERY PLAN ${DefaultRagIndex.VECTOR_SEARCH_SQL}", unitVector(1).toLeBlob(), K, K) { it.getText(3) }
        }
        assertWithMessage(plan.joinToString("\n")).that(plan.count { "VIRTUAL TABLE" in it }).isEqualTo(1)
        assertWithMessage(plan.joinToString("\n")).that(plan.any { "MATERIALIZE" in it }).isTrue()
    }

    private fun dot(a: FloatArray, b: FloatArray): Double = a.indices.sumOf { a[it].toDouble() * b[it] }

    private companion object {
        const val CHUNKS = 20
        const val K = 5
    }
}

package com.lisdatasolutions.microrag.data.rag

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.text.SpanishText
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F17 T006, the gate of research R3: a scoped search is the exact top k of the scope, done with the
 * streaming scan of SQLite-Vector 1.1.2 in a materialized CTE, and the whole library searches as F10 did
 * (FR-017, FR-018, SC-002, SC-003).
 */
@RunWith(AndroidJUnit4::class)
class ScopedSearchTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    private class Doc(val id: Long, val seed: Int, val searchable: Boolean, val chunks: Map<Int, Long>)

    private var legal = 0L
    private var motors = 0L
    private var others = 0L
    private lateinit var docs: Map<String, Doc>

    /** Each chunk has its own length, so no two BM25 scores tie and the order is exact. */
    private var words = 0

    @Before
    fun setUp() = runBlocking<Unit> {
        index.open()
        legal = folder("Legal")
        motors = folder("Motores")
        others = folder("Otros")
        docs = linkedMapOf(
            "legal1" to doc("Contrato.txt", 1, legal),
            "legal2" to doc("Garantía.txt", 2, legal),
            "legalExcluded" to doc("Anexo.txt", 3, legal, included = false),
            "motor1" to doc("Bombas.txt", 4, motors),
            "motorPending" to doc("Turbinas.txt", 5, motors, status = DocumentStatus.EMBEDDING),
            "others1" to doc("Notas.txt", 6, others),
            "general1" to doc("Manual.txt", 7, null),
            "general2" to doc("Guía.txt", 8, null),
            "generalError" to doc("Roto.txt", 9, null, status = DocumentStatus.ERROR),
        )
    }

    @After
    fun tearDown() = temp.close()

    private fun folder(name: String): Long = temp.withRaw { db ->
        db.exec(
            "INSERT INTO folders(name, name_norm, created_at_ms, updated_at_ms) VALUES (?, ?, 1, 1)",
            name, name.lowercase(),
        )
        checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
    }

    private suspend fun doc(
        name: String,
        seed: Int,
        folderId: Long?,
        status: DocumentStatus = DocumentStatus.DONE,
        included: Boolean = true,
    ): Doc {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        index.insertChunks(
            id,
            (0 until CHUNKS).map {
                chunk(it, content = "La bomba $seed número $it " + "texto ".repeat(++words), vector = unitVector(seed * 100 + it))
            },
        )
        index.updateStatus(id, status)
        if (!included) index.setIncluded(id, false)
        temp.withRaw { db -> db.exec("UPDATE documents SET folder_id = ? WHERE id = ?", folderId, id) }
        val chunks = temp.withRaw { db ->
            db.query("SELECT ordinal, id FROM chunks WHERE document_id = ?", id) { it.getLong(0).toInt() to it.getLong(1) }.toMap()
        }
        return Doc(id, seed, searchable = status == DocumentStatus.DONE && included, chunks = chunks)
    }

    private fun d(key: String) = docs.getValue(key)

    /** Scopes with whole folders, «General», loose documents, and one with nothing searchable. */
    private fun scopes(): Map<SearchScope.Only, List<Doc>> = mapOf(
        SearchScope.Only(setOf(legal), general = false, documentIds = emptySet()) to listOf(d("legal1"), d("legal2")),
        SearchScope.Only(emptySet(), general = true, documentIds = emptySet()) to listOf(d("general1"), d("general2")),
        SearchScope.Only(setOf(motors), general = false, documentIds = setOf(d("others1").id)) to listOf(d("motor1"), d("others1")),
        SearchScope.Only(setOf(others), general = true, documentIds = setOf(d("legal2").id)) to
            listOf(d("others1"), d("general1"), d("general2"), d("legal2")),
        SearchScope.Only(emptySet(), general = false, documentIds = setOf(d("legalExcluded").id, d("motorPending").id)) to emptyList(),
    )

    /** A query close to the documents outside most scopes: an unfiltered top k would be all theirs. */
    private val query = EmbeddingMath.truncateAndNormalize(FloatArray(256) { i -> unitVector(500)[i] + unitVector(900)[i] + unitVector(300)[i] })

    private fun handVector(of: List<Doc>, k: Int): List<Pair<Long, Double>> =
        of.flatMap { doc -> doc.chunks.map { (ordinal, id) -> id to 1.0 - dot(query, unitVector(doc.seed * 100 + ordinal)) } }
            .sortedBy { it.second }.take(k)

    /** Every BM25 score of the index, then the scope's documents, sorted: the top k by hand. */
    private fun handText(of: List<Doc>, k: Int): List<Long> {
        val wanted = of.flatMap { it.chunks.values }.toSet()
        return temp.withRaw { db ->
            db.query("SELECT rowid, bm25(chunks_fts, 3.0, 1.0) FROM chunks_fts WHERE chunks_fts MATCH ?", SpanishText.ftsQuery("bomba")) {
                it.getLong(0) to it.getDouble(1)
            }
        }.filter { it.first in wanted }.sortedBy { it.second }.take(k).map { it.first }
    }

    @Test
    fun theStreamingScanExistsInVersion112() {
        val total = docs.values.sumOf { it.chunks.size }
        val counts = temp.withRaw { db ->
            db.initVector(EmbeddingMath.DIMENSIONS)
            val withoutK = db.queryOne("SELECT count(*) FROM vector_full_scan('chunks', 'embedding', ?)", query.toLeBlob()) { it.getLong(0) }
            val module = runCatching {
                db.queryOne("SELECT count(*) FROM vector_full_scan_stream('chunks', 'embedding', ?)", query.toLeBlob()) { it.getLong(0) }
            }
            Log.i(TAG, "streaming scan: without k=$withoutK, vector_full_scan_stream=${module.getOrElse { it.javaClass.simpleName }}")
            withoutK
        }
        // The app uses vector_full_scan without k (research R3); it must return every chunk.
        assertThat(counts).isEqualTo(total.toLong())
    }

    @Test
    fun aScopedVectorSearchIsTheExactTopKOfTheScope() = runBlocking<Unit> {
        for ((scope, inScope) in scopes()) {
            for (k in listOf(K, 50)) {
                val found = index.vectorSearch(query, k = k, scope = scope)
                val expected = handVector(inScope.filter { it.searchable }, k)

                assertWithMessage("$scope k=$k").that(found.map { it.id }).containsExactlyElementsIn(expected.map { it.first }).inOrder()
                found.zip(expected).forEach { (hit, want) -> assertThat(hit.distance).isWithin(1e-4).of(want.second) }
            }
        }
    }

    @Test
    fun aScopedTextSearchIsTheExactTopKOfTheScope() = runBlocking<Unit> {
        for ((scope, inScope) in scopes()) {
            for (k in listOf(K, 50)) {
                val found = index.textSearch("bomba", k = k, scope = scope)

                assertWithMessage("$scope k=$k").that(found).containsExactlyElementsIn(handText(inScope.filter { it.searchable }, k)).inOrder()
            }
        }
    }

    /** SC-002: nothing outside the scope, excluded or not indexed, even when it is the closest. */
    @Test
    fun nothingOutsideTheScopeExcludedOrPendingIsFound() = runBlocking<Unit> {
        for ((scope, inScope) in scopes()) {
            val allowed = inScope.filter { it.searchable }.flatMap { it.chunks.values }.toSet()
            val found = index.vectorSearch(query, k = 300, scope = scope).map { it.id } + index.textSearch("bomba", k = 300, scope = scope)

            assertWithMessage("$scope").that(allowed.containsAll(found)).isTrue()
        }
    }

    /** SC-003: with the whole library, the searches return exactly what F10 returned. */
    @Test
    fun theWholeLibrarySearchesAsBefore() = runBlocking<Unit> {
        val searchable = docs.values.filter { it.searchable }

        val byDefault = index.vectorSearch(query, k = K)
        val whole = index.vectorSearch(query, k = K, scope = SearchScope.All)

        assertThat(whole).isEqualTo(byDefault)
        assertThat(whole.map { it.id }).containsExactlyElementsIn(handVector(searchable, K).map { it.first }).inOrder()
        assertThat(index.textSearch("bomba", k = K, scope = SearchScope.All)).isEqualTo(index.textSearch("bomba", k = K))
        assertThat(index.textSearch("bomba", k = K)).containsExactlyElementsIn(handText(searchable, K)).inOrder()
    }

    /** Analyze H1: inside the materialized CTE, one vector scan and one FTS search, never repeated by the join. */
    @Test
    fun eachScopedSearchScansOnce() {
        val (condition, args) = DefaultRagIndex.scopeCondition(SearchScope.Only(setOf(legal, motors), general = true, documentIds = setOf(d("others1").id)))
        val vectorPlan = temp.withRaw { db ->
            db.query("EXPLAIN QUERY PLAN ${DefaultRagIndex.scopedVectorSearchSql(condition)}", query.toLeBlob(), *args, K) { it.getText(3) }
        }
        val textPlan = temp.withRaw { db ->
            db.query("EXPLAIN QUERY PLAN ${DefaultRagIndex.scopedTextSearchSql(condition)}", "bomba", *args, K) { it.getText(3) }
        }

        for (plan in listOf(vectorPlan, textPlan)) {
            assertWithMessage(plan.joinToString("\n")).that(plan.count { "VIRTUAL TABLE" in it }).isEqualTo(1)
            assertWithMessage(plan.joinToString("\n")).that(plan.any { "MATERIALIZE" in it }).isTrue()
        }
    }

    @Test
    fun loadingAndNeighborsKeepToTheScope() = runBlocking<Unit> {
        val scope = SearchScope.Only(setOf(legal), general = false, documentIds = emptySet())
        val every = docs.values.flatMap { it.chunks.values }

        val loaded = index.loadChunks(every, searchableOnly = true, scope = scope)
        assertThat(loaded.values.map { it.documentId }.toSet()).containsExactly(d("legal1").id, d("legal2").id)
        assertThat(index.loadChunks(every, searchableOnly = false, scope = scope).values.map { it.documentId }.toSet())
            .containsExactly(d("legal1").id, d("legal2").id, d("legalExcluded").id)
        assertThat(index.loadChunks(every).size).isEqualTo(every.size)

        assertThat(index.neighbor(d("legal1").id, 3, searchableOnly = true, scope = scope)).isNotNull()
        assertThat(index.neighbor(d("general1").id, 3, searchableOnly = true, scope = scope)).isNull()
        assertThat(index.neighbor(d("general1").id, 3, searchableOnly = false, scope = scope)).isNull()
        assertThat(index.neighbor(d("general1").id, 3, searchableOnly = true)).isNotNull()
    }

    private fun dot(a: FloatArray, b: FloatArray): Double = a.indices.sumOf { a[it].toDouble() * b[it] }

    private companion object {
        const val TAG = "ScopedSearchTest"
        const val CHUNKS = 33
        const val K = 5
    }
}

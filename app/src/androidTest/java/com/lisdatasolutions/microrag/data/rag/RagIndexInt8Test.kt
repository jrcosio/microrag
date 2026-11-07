package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T012 (research R3, contract I5, FR-008–FR-012, SC-005): the INT8 copy of the chunk vectors with the real
 * SQLite-Vector. It is used only when it is current and preloaded on this connection; any change of the chunks
 * makes the search exact again, and a failed rebuild or preload never breaks it. T049 (research R10, FR-023): the
 * same for the page vectors, from their own minimum.
 */
@RunWith(AndroidJUnit4::class)
class RagIndexInt8Test {

    private val temps = mutableListOf<TempRagIndex>()

    @After
    fun tearDown() = temps.forEach { it.close() }

    private fun temp(quantization: DefaultRagIndex.QuantizationSql = DefaultRagIndex.QuantizationSql()) =
        TempRagIndex(quantization = quantization).also { temps += it }

    /** [DOCUMENTS] done documents of [CHUNKS] chunks, the last one excluded, in a folder and loose. */
    private suspend fun TempRagIndex.fill(): List<Long> = (0 until DOCUMENTS).map { d ->
        val doc = index.addDocument(NewDocument(displayName = "Documento $d.txt", contentHash = "int8-$d"))
        index.insertChunks(doc, (0 until CHUNKS).map { o -> chunk(o, vector = unitVector(d * 1_000 + o)) })
        index.updateStatus(doc, DocumentStatus.DONE, chunkCount = CHUNKS)
        doc
    }.also { docs -> index.setIncluded(docs.last(), false) }

    private fun TempRagIndex.dirty(key: String = RagMetaKeys.QUANT_DIRTY): String? =
        withRaw { db -> db.queryOne("SELECT value FROM rag_meta WHERE key = ?", key) { it.getText(0) } }

    /** [DOCUMENTS] done visual documents of [PAGES] pages, the last one excluded. */
    private suspend fun TempRagIndex.fillPages(): List<Long> = (0 until DOCUMENTS).map { d ->
        val doc = index.addDocument(NewDocument(displayName = "Páginas $d.pdf", contentHash = "int8-pages-$d", indexMode = IndexMode.VISUAL))
        (1..PAGES).forEach { p -> index.insertPage(doc, page(p, unitVector(d * 1_000 + p))) }
        index.updateStatus(doc, DocumentStatus.DONE, chunkCount = 0)
        doc
    }.also { docs -> index.setIncluded(docs.last(), false) }

    private fun page(number: Int, vector: FloatArray) = PageInput(number, "p-$number-${vector.hashCode()}.jpg", 543, 768, 90_000L, vector)

    @Test
    fun theQuantizedSearch_givesTheExactTopK() = runBlocking<Unit> {
        val temp = temp()
        temp.fill()
        val queries = listOf(unitVector(17), unitVector(1_003), unitVector(42_000))
        val exact = queries.map { temp.index.vectorSearch(it, K) }

        val state = temp.index.refreshQuantized(minChunks = 1)
        val quantized = queries.map { temp.index.vectorSearch(it, K) }

        assertThat(state.usable).isTrue()
        assertThat(temp.dirty()).isEqualTo("0")
        quantized.zip(exact).forEach { (q, e) ->
            assertThat(q.map { it.id }).containsExactlyElementsIn(e.map { it.id }).inOrder()
            q.zip(e).forEach { (a, b) -> assertThat(a.distance).isWithin(1e-4).of(b.distance) }
        }
    }

    @Test
    fun everyChangeOfTheChunks_makesItStale() = runBlocking<Unit> {
        val temp = temp()
        val docs = temp.fill()
        val changes: List<Pair<String, suspend () -> Unit>> = listOf(
            "insertChunks" to {
                val doc = temp.index.addDocument(NewDocument(displayName = "Nuevo.txt", contentHash = "int8-new"))
                temp.index.insertChunks(doc, listOf(chunk(0, vector = unitVector(77_000))))
            },
            "deleteChunks" to { temp.index.deleteChunks(docs[1]) },
            "removeFromIndex" to { temp.index.removeFromIndex(docs[0]) },
            "deleteDocument" to { temp.index.deleteDocument(docs[2]) },
            "publishReindex" to {
                val shadow = temp.index.addReindexShadow(docs[3])
                temp.index.insertChunks(shadow, listOf(chunk(0, vector = unitVector(88_000))))
                temp.index.publishReindex(shadow)
            },
        )
        changes.forEach { (name, change) ->
            assertWithMessage("$name: before").that(temp.index.refreshQuantized(minChunks = 1).usable).isTrue()

            change()

            assertWithMessage("$name: usable").that(temp.index.quantizedState().usable).isFalse()
            assertWithMessage("$name: quant_dirty").that(temp.dirty()).isEqualTo("1")
        }
    }

    @Test
    fun aNewChunkEqualToTheQuery_isFoundRightAfterItIsStored() = runBlocking<Unit> {
        val temp = temp()
        temp.fill()
        temp.index.refreshQuantized(minChunks = 1)
        val doc = temp.index.addDocument(NewDocument(displayName = "Nuevo.txt", contentHash = "int8-new"))
        val query = unitVector(99_000)
        temp.index.insertChunks(doc, listOf(chunk(0, vector = query)))
        temp.index.updateStatus(doc, DocumentStatus.DONE, chunkCount = 1)

        val top = temp.index.vectorSearch(query, K).first()

        assertThat(temp.index.storedChunkCounts(listOf(doc))[doc]).isEqualTo(1)
        assertThat(top.distance).isWithin(1e-5).of(0.0)
    }

    @Test
    fun afterReopening_itIsCurrentButNotPreloaded_untilRefreshed() = runBlocking<Unit> {
        val temp = temp()
        temp.fill()
        temp.index.refreshQuantized(minChunks = 1)
        temp.index.close()

        val reopened = temp.index.quantizedState()
        val refreshed = temp.index.refreshQuantized(minChunks = 1)

        assertThat(reopened.current).isTrue()
        assertThat(reopened.preloaded).isFalse()
        assertThat(reopened.usable).isFalse()
        assertThat(refreshed.usable).isTrue()
    }

    @Test
    fun aFailedRebuild_leavesItStale_andTheSearchStillWorks() = runBlocking<Unit> {
        val temp = temp(DefaultRagIndex.QuantizationSql(quantize = "SELECT vector_quantize('chunks', 'embedding', 'qtype=INT8,max_memory=abc')"))
        temp.fill()

        val state = temp.index.refreshQuantized(minChunks = 1)
        val hits = temp.index.vectorSearch(unitVector(17), K)

        assertThat(state.usable).isFalse()
        assertThat(temp.dirty()).isEqualTo("1")
        assertThat(hits.first().distance).isWithin(1e-5).of(0.0)
    }

    @Test
    fun aFailedPreload_neverLeavesAnOlderSnapshotInUse() = runBlocking<Unit> {
        val temp = temp(DefaultRagIndex.QuantizationSql(preload = "SELECT vector_quantize_preload('chunks', 'missing_column')"))
        temp.fill()

        val state = temp.index.refreshQuantized(minChunks = 1)

        assertThat(state.current).isTrue()
        assertThat(state.preloaded).isFalse()
        assertThat(state.usable).isFalse()
        assertThat(temp.index.vectorSearch(unitVector(17), K).first().distance).isWithin(1e-5).of(0.0)
    }

    @Test
    fun aScopeAndTheExcluded_giveWhatTheExactSearchGives() = runBlocking<Unit> {
        val temp = temp()
        val docs = temp.fill()
        val folder = checkNotNull(temp.index.createFolder("Carpeta", "carpeta"))
        temp.index.moveDocument(docs[1], folder)
        val scopes = listOf(
            SearchScope.All,
            SearchScope.Only(setOf(folder), general = false, documentIds = emptySet()),
            SearchScope.Only(emptySet(), general = true, documentIds = emptySet()),
            SearchScope.Only(emptySet(), general = false, documentIds = setOf(docs[0], docs.last())),
        )
        val query = unitVector(1_005)
        val exact = scopes.map { temp.index.vectorSearch(query, K, it) }

        temp.index.refreshQuantized(minChunks = 1)
        val quantized = scopes.map { temp.index.vectorSearch(query, K, it) }

        quantized.zip(exact).forEachIndexed { i, (q, e) ->
            assertWithMessage("scope $i").that(q.map { it.id }).containsExactlyElementsIn(e.map { it.id }).inOrder()
        }
        val excluded = temp.index.loadChunks(quantized.flatten().map { it.id }).values.filter { it.documentId == docs.last() }
        assertThat(excluded).isEmpty()
    }

    // T049 (research R3): the search first reads the head of the copy, twice the candidates, and widens it to every
    // hidden chunk only when the excluded ones leave fewer searchable candidates there.
    @Test
    fun excludedChunksCrowdingTheHead_stillGiveWhatTheExactSearchGives() = runBlocking<Unit> {
        val temp = temp()
        val docs = temp.fill()
        val query = unitVector(1_005)
        val crowd = temp.index.addDocument(NewDocument(displayName = "Excluido.txt", contentHash = "int8-crowd"))
        val near = 2 * RagParameters.INT8_CANDIDATES + 1
        temp.index.insertChunks(crowd, (0 until near).map { o -> chunk(o, vector = nearTo(query, seed = 500_000 + o)) })
        temp.index.updateStatus(crowd, DocumentStatus.DONE, chunkCount = near)
        temp.index.setIncluded(crowd, false)
        val exact = temp.index.vectorSearch(query, K)

        temp.index.refreshQuantized(minChunks = 1)
        val quantized = temp.index.vectorSearch(query, K)

        assertThat(quantized.map { it.id }).containsExactlyElementsIn(exact.map { it.id }).inOrder()
        assertThat(temp.index.loadChunks(quantized.map { it.id }).values.map { it.documentId }).containsNoneOf(crowd, docs.last())
    }

    /** A unit vector a little off [query], nearer to it than any other chunk of [fill]. */
    private fun nearTo(query: FloatArray, seed: Int): FloatArray {
        val noise = unitVector(seed)
        return EmbeddingMath.truncateAndNormalize(FloatArray(query.size) { query[it] + NEAR_NOISE * noise[it] })
    }

    @Test
    fun theQuantizedTables_leaveTheFileCompatible() = runBlocking<Unit> {
        val temp = temp()
        temp.fill()
        temp.index.refreshQuantized(minChunks = 1)
        temp.index.close()

        assertThat(temp.withRaw { it.tableNames() }).contains("vector0_chunks_embedding")
        temp.index.quantizedState()
        assertThat(temp.index.compatibility.value).isEqualTo(IndexCompatibility.Compatible)
    }

    @Test
    fun belowTheMinimum_nothingIsBuilt() = runBlocking<Unit> {
        val temp = temp()
        temp.fill()

        val state = temp.index.refreshQuantized(minChunks = DOCUMENTS * CHUNKS + 1)

        assertThat(state.usable).isFalse()
        assertThat(state.searchableChunks).isEqualTo((DOCUMENTS - 1) * CHUNKS)
        assertThat(temp.dirty()).isEqualTo("1")
        assertThat(temp.withRaw { it.tableNames() }).doesNotContain("vector0_chunks_embedding")
    }

    @Test
    fun theQuantizedPageSearch_givesTheExactTopK_inEveryScope() = runBlocking<Unit> {
        val temp = temp()
        val docs = temp.fillPages()
        val folder = checkNotNull(temp.index.createFolder("Carpeta", "carpeta"))
        temp.index.moveDocument(docs[1], folder)
        val scopes = listOf(SearchScope.All, SearchScope.Only(setOf(folder), general = false, documentIds = emptySet()))
        val queries = listOf(unitVector(17), unitVector(1_003), unitVector(4_020))
        val exact = scopes.flatMap { scope -> queries.map { temp.index.pageSearch(it, PAGE_K, scope) } }

        val state = temp.index.refreshQuantized(minChunks = Int.MAX_VALUE, minPages = 1)
        val quantized = scopes.flatMap { scope -> queries.map { temp.index.pageSearch(it, PAGE_K, scope) } }

        assertThat(state.pagesUsable).isTrue()
        assertThat(state.usable).isFalse()
        assertThat(temp.dirty(RagMetaKeys.PAGES_QUANT_DIRTY)).isEqualTo("0")
        quantized.zip(exact).forEachIndexed { i, (q, e) ->
            assertWithMessage("search $i").that(q).hasSize(e.size)
            q.zip(e).forEach { (a, b) ->
                assertThat(a.copy(distance = 0.0)).isEqualTo(b.copy(distance = 0.0))
                assertThat(a.distance).isWithin(1e-4).of(b.distance)
            }
        }
        assertThat(quantized.flatten().map { it.documentId }).doesNotContain(docs.last())
    }

    @Test
    fun everyChangeOfThePages_makesTheirCopyStale() = runBlocking<Unit> {
        val temp = temp()
        val docs = temp.fillPages()
        val changes: List<Pair<String, suspend () -> Unit>> = listOf(
            "insertPage" to { temp.index.insertPage(docs[0], page(PAGES + 1, unitVector(77_000))) },
            "removeFromIndex" to { temp.index.removeFromIndex(docs[0]) },
            "deleteDocument" to { temp.index.deleteDocument(docs[2]) },
            "publishReindex" to {
                val shadow = temp.index.addReindexShadow(docs[3])
                temp.index.insertPage(shadow, page(1, unitVector(88_000)))
                temp.index.publishReindex(shadow)
            },
        )
        changes.forEach { (name, change) ->
            assertWithMessage("$name: before").that(temp.index.refreshQuantized(minChunks = Int.MAX_VALUE, minPages = 1).pagesUsable).isTrue()

            change()

            assertWithMessage("$name: usable").that(temp.index.quantizedState().pagesUsable).isFalse()
            assertWithMessage("$name: pages_quant_dirty").that(temp.dirty(RagMetaKeys.PAGES_QUANT_DIRTY)).isNull()
        }
        // A page equal to the query is found right after it is stored: never the old copy.
        temp.index.refreshQuantized(minChunks = Int.MAX_VALUE, minPages = 1)
        val query = unitVector(99_000)
        temp.index.insertPage(docs[1], page(PAGES + 2, query))
        assertThat(temp.index.pageSearch(query, PAGE_K).first().distance).isWithin(1e-5).of(0.0)
    }

    @Test
    fun belowThePageMinimum_noPageCopyIsBuilt() = runBlocking<Unit> {
        val temp = temp()
        temp.fillPages()

        val state = temp.index.refreshQuantized(minChunks = Int.MAX_VALUE, minPages = DOCUMENTS * PAGES + 1)

        assertThat(state.pagesUsable).isFalse()
        assertThat(state.pages).isEqualTo(DOCUMENTS * PAGES)
        assertThat(temp.dirty(RagMetaKeys.PAGES_QUANT_DIRTY)).isNull()
        assertThat(temp.withRaw { it.tableNames() }).doesNotContain("vector0_pages_embedding")
    }

    private companion object {
        const val DOCUMENTS = 5
        const val CHUNKS = 60
        const val PAGES = 40
        const val K = 10
        const val PAGE_K = 5
        const val NEAR_NOISE = 0.05f
    }
}

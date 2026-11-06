package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.domain.rag.SearchScope
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
 * makes the search exact again, and a failed rebuild or preload never breaks it.
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

    private fun TempRagIndex.dirty(): String? =
        withRaw { db -> db.queryOne("SELECT value FROM rag_meta WHERE key = ?", RagMetaKeys.QUANT_DIRTY) { it.getText(0) } }

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

    private companion object {
        const val DOCUMENTS = 5
        const val CHUNKS = 60
        const val K = 10
    }
}

package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F08 T007 on real SQLite (research R2, R4–R6): the Library page and count, reindex shadows and their
 * atomic publication, «Quitar del índice» and the INT8 flag on deletes.
 */
@RunWith(AndroidJUnit4::class)
class RagIndexLibraryTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private val all = DocumentStatus.entries.toSet()
    private val notDone = all - DocumentStatus.DONE

    private suspend fun doc(
        name: String,
        status: DocumentStatus = DocumentStatus.DONE,
        chunks: Int = 0,
        seed: Int = 0,
        content: (Int) -> String = { "Texto $seed número $it" },
    ): Long {
        temp.now += 10
        val id = index.addDocument(
            NewDocument(displayName = name, contentHash = name, mimeType = "application/pdf", sizeBytes = 2048, pageCount = 7, localPath = "/copies/$name"),
        )
        if (chunks > 0) {
            index.insertChunks(id, (0 until chunks).map { chunk(it, content = content(it), page = it + 1, vector = unitVector(seed * 1_000 + it)) })
        }
        index.updateStatus(id, status, error = if (status == DocumentStatus.ERROR) "no_text" else null, chunkCount = chunks.takeIf { it > 0 })
        return id
    }

    private fun quantDirtyCleared() = temp.withRaw { it.exec("UPDATE rag_meta SET value = '0' WHERE key = ?", RagMetaKeys.QUANT_DIRTY) }

    private suspend fun quantDirty(): String? = index.meta()[RagMetaKeys.QUANT_DIRTY]

    private fun chunkRows(documentId: Long): List<Triple<Long, Int, String>> = temp.withRaw { db ->
        db.query("SELECT id, ordinal, content FROM chunks WHERE document_id = ? ORDER BY ordinal", documentId) {
            Triple(it.getLong(0), it.getLong(1).toInt(), it.getText(2))
        }
    }

    private fun ftsIntegrityCheck() =
        temp.withRaw { it.exec("INSERT INTO chunks_fts(chunks_fts, rank) VALUES('integrity-check', 1)") }

    private fun ftsRows(): Int = temp.withRaw { db -> db.queryOne("SELECT count(*) FROM chunks_fts_docsize") { it.getLong(0).toInt() } ?: 0 }

    @Test
    fun libraryPage_isNewestFirst_filteredAndLimited() = runBlocking<Unit> {
        val pending = doc("Pendiente.pdf", DocumentStatus.PENDING)
        val done = doc("Indexado.pdf", chunks = 2)
        val failed = doc("Con error.pdf", DocumentStatus.ERROR)
        val out = doc("Fuera.pdf", DocumentStatus.OUT_OF_INDEX)
        val embedding = doc("A medias.pdf", DocumentStatus.EMBEDDING, chunks = 1, seed = 2)

        assertThat(index.libraryPage("", all, limit = 50).map { it.document.id })
            .containsExactly(embedding, out, failed, done, pending).inOrder()
        assertThat(index.libraryPage("", setOf(DocumentStatus.DONE), limit = 50).map { it.document.id }).containsExactly(done)
        assertThat(index.libraryPage("", notDone, limit = 50).map { it.document.id })
            .containsExactly(embedding, out, failed, pending).inOrder()
        assertThat(index.libraryPage("", all, limit = 2).map { it.document.id }).containsExactly(embedding, out).inOrder()

        assertThat(index.libraryCount("", all)).isEqualTo(5)
        assertThat(index.libraryCount("", setOf(DocumentStatus.DONE))).isEqualTo(1)
        assertThat(index.libraryCount("", notDone)).isEqualTo(4)
    }

    @Test
    fun libraryPage_searchesFoldedSubstringsWithLiteralWildcards() = runBlocking<Unit> {
        val guide = doc("Guía de instalación.pdf")
        val catalog = doc("CATÁLOGO 2024.pdf", DocumentStatus.PENDING)
        val percent = doc("Ventas 50% anual.txt")
        val underscore = doc("informe_final.md")
        doc("informeXfinal.md")
        doc("Ventas 50 anual.txt")

        suspend fun ids(text: String, statuses: Set<DocumentStatus> = all) = index.libraryPage(text, statuses, 50).map { it.document.id }

        assertThat(ids("guia")).containsExactly(guide)
        assertThat(ids("  INSTALACION ")).containsExactly(guide)
        assertThat(ids("catálogo")).containsExactly(catalog)
        assertThat(ids("50%")).containsExactly(percent)
        assertThat(ids("e_f")).containsExactly(underscore)
        assertThat(ids("guia", setOf(DocumentStatus.PENDING))).isEmpty()
        assertThat(ids("catalogo", notDone)).containsExactly(catalog)
        assertThat(index.libraryCount("50%", all)).isEqualTo(1)
        assertThat(index.libraryCount("ventas", all)).isEqualTo(2)
    }

    @Test
    fun libraryPage_hidesShadowsAndReportsTheirState() = runBlocking<Unit> {
        val running = doc("Reindexando.pdf", chunks = 3)
        val failing = doc("Falla.pdf", chunks = 2, seed = 1)
        doc("Normal.pdf", chunks = 1, seed = 2)
        val runningShadow = index.addReindexShadow(running)
        val failingShadow = index.addReindexShadow(failing)
        index.updateStatus(failingShadow, DocumentStatus.ERROR, error = "embedding_failed")

        val page = index.libraryPage("", all, 50)

        assertThat(page.map { it.document.id }).containsNoneOf(runningShadow, failingShadow)
        assertThat(page).hasSize(3)
        assertThat(page.single { it.document.id == running }.shadowStatus).isEqualTo(DocumentStatus.PENDING)
        assertThat(page.single { it.document.id == failing }.shadowStatus).isEqualTo(DocumentStatus.ERROR)
        assertThat(page.filter { it.document.id != running && it.document.id != failing }.map { it.shadowStatus }).containsExactly(null)
        assertThat(index.libraryCount("", all)).isEqualTo(3)
        assertThat(index.libraryCount("reindexando", notDone)).isEqualTo(0)
    }

    @Test
    fun addReindexShadow_copiesTheOriginalAndStaysOutOfSearches() = runBlocking<Unit> {
        val original = doc("Manual de motores.pdf", chunks = 3, content = { "Cambio de aceite $it" })
        val before = index.vectorSearch(unitVector(0), k = 3).map { it.id }

        val shadow = index.addReindexShadow(original)

        val stored = index.document(shadow)!!
        val source = index.document(original)!!
        assertThat(stored.replacesId).isEqualTo(original)
        assertThat(stored.status).isEqualTo(DocumentStatus.PENDING)
        assertThat(stored.contentHash).isEqualTo("${source.contentHash}#reindex")
        assertThat(stored.displayName).isEqualTo(source.displayName)
        assertThat(stored.mimeType).isEqualTo(source.mimeType)
        assertThat(stored.sizeBytes).isEqualTo(source.sizeBytes)
        assertThat(stored.pageCount).isEqualTo(source.pageCount)
        assertThat(stored.localPath).isEqualTo(source.localPath)
        assertThat(index.shadowOf(original)?.id).isEqualTo(shadow)
        assertThat(index.shadowOf(shadow)).isNull()
        // The duplicate check of F06 never sees the shadow.
        assertThat(index.documentIdByHash(source.contentHash)).isEqualTo(original)
        // Its chunks are hidden and counted in m: the k best are still the original's.
        index.insertChunks(shadow, (0 until 3).map { chunk(it, content = "Cambio de aceite $it", vector = unitVector(it)) })
        index.updateStatus(shadow, DocumentStatus.EMBEDDING)
        assertThat(index.vectorSearch(unitVector(0), k = 3).map { it.id }).isEqualTo(before)
        assertThat(index.textSearch("aceite", k = 10)).hasSize(3)
        assertThat(index.textSearch("aceite", k = 10).toSet()).isEqualTo(chunkRows(original).map { it.first }.toSet())
    }

    @Test
    fun addReindexShadow_failsWithALiveShadowOrANotDoneOriginal() = runBlocking<Unit> {
        val original = doc("Uno.pdf", chunks = 1)
        val pending = doc("Dos.pdf", DocumentStatus.PENDING)
        val shadow = index.addReindexShadow(original)

        assertThrows(IllegalStateException::class.java) { runBlocking { index.addReindexShadow(original) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { index.addReindexShadow(pending) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { index.addReindexShadow(shadow) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { index.addReindexShadow(9_999) } }
    }

    @Test
    fun publishReindex_swapsTheChunksAtomically() = runBlocking<Unit> {
        val original = doc("Manual.pdf", chunks = 4, content = { "Versión vieja $it bomba" })
        val other = doc("Otro.pdf", chunks = 2, seed = 5, content = { "Otro documento $it" })
        index.setIncluded(original, false)
        index.setIncluded(original, true)
        val shadow = index.addReindexShadow(original)
        index.updateStatus(shadow, DocumentStatus.PARSED, chunkCount = 3, skippedPages = 1)
        index.insertChunks(shadow, (0 until 3).map { chunk(it, content = "Versión nueva $it turbina", page = it + 2, vector = unitVector(500 + it)) })
        index.updateStatus(shadow, DocumentStatus.EMBEDDING)
        val newRows = chunkRows(shadow)
        assertThat(index.textSearch("bomba", k = 10)).hasSize(4)
        assertThat(index.textSearch("turbina", k = 10)).isEmpty()
        quantDirtyCleared()

        assertThat(index.publishReindex(shadow)).isEqualTo(3)

        assertThat(index.document(shadow)).isNull()
        assertThat(index.shadowOf(original)).isNull()
        val published = index.document(original)!!
        assertThat(published.status).isEqualTo(DocumentStatus.DONE)
        assertThat(published.included).isTrue()
        assertThat(published.chunkCount).isEqualTo(3)
        assertThat(published.skippedPages).isEqualTo(1)
        assertThat(chunkRows(original)).isEqualTo(newRows)
        assertThat(chunkRows(other)).hasSize(2)
        assertThat(index.textSearch("bomba", k = 10)).isEmpty()
        assertThat(index.textSearch("turbina", k = 10).toSet()).isEqualTo(newRows.map { it.first }.toSet())
        assertThat(index.vectorSearch(unitVector(500), k = 1).single().id).isEqualTo(newRows.first().first)
        assertThat(index.loadChunks(listOf(newRows.first().first)).getValue(newRows.first().first).documentId).isEqualTo(original)
        assertThat(ftsRows()).isEqualTo(5)
        ftsIntegrityCheck()
        assertThat(quantDirty()).isEqualTo("1")
    }

    @Test
    fun publishReindex_keepsAnExcludedOriginalExcluded() = runBlocking<Unit> {
        val original = doc("Excluido.pdf", chunks = 1)
        index.setIncluded(original, false)
        val shadow = index.addReindexShadow(original)
        index.insertChunks(shadow, listOf(chunk(0, content = "Nuevo", vector = unitVector(9))))
        index.updateStatus(shadow, DocumentStatus.EMBEDDING)

        assertThat(index.publishReindex(shadow)).isEqualTo(1)

        assertThat(index.document(original)!!.included).isFalse()
        assertThat(index.vectorSearch(unitVector(9), k = 5)).isEmpty()
    }

    @Test
    fun publishReindex_withoutShadowOrOriginal_changesNothing() = runBlocking<Unit> {
        val original = doc("Manual.pdf", chunks = 2)
        val shadow = index.addReindexShadow(original)
        index.insertChunks(shadow, listOf(chunk(0, content = "Nuevo", vector = unitVector(9))))

        assertThat(index.publishReindex(original)).isNull()
        assertThat(index.publishReindex(9_999)).isNull()
        index.deleteDocument(original)
        assertThat(index.publishReindex(shadow)).isNull()
        assertThat(index.counts().chunks).isEqualTo(0)
    }

    @Test
    fun removeFromIndex_dropsChunksAndShadowsAndKeepsTheDocument() = runBlocking<Unit> {
        val target = doc("Quitar.pdf", chunks = 5, content = { "Quitar bomba $it" })
        val other = doc("Otro.pdf", chunks = 2, seed = 3, content = { "Otro $it" })
        index.setIncluded(target, false)
        val shadow = index.addReindexShadow(target)
        index.insertChunks(shadow, listOf(chunk(0, content = "Sombra bomba", vector = unitVector(77))))
        quantDirtyCleared()

        assertThat(index.removeFromIndex(target)).isEqualTo(5)

        val removed = index.document(target)!!
        assertThat(removed.status).isEqualTo(DocumentStatus.OUT_OF_INDEX)
        assertThat(removed.chunkCount).isNull()
        assertThat(removed.error).isNull()
        assertThat(removed.included).isFalse()
        assertThat(removed.localPath).isEqualTo("/copies/Quitar.pdf")
        assertThat(index.document(shadow)).isNull()
        assertThat(chunkRows(target)).isEmpty()
        assertThat(chunkRows(other)).hasSize(2)
        assertThat(index.textSearch("bomba", k = 10)).isEmpty()
        assertThat(ftsRows()).isEqualTo(2)
        ftsIntegrityCheck()
        assertThat(quantDirty()).isEqualTo("1")
        // Out of the index, of every run and of the Ingest queue statuses (research R4).
        assertThat(index.documents(setOf(DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.EMBEDDING, DocumentStatus.ERROR)))
            .isEmpty()
    }

    @Test
    fun removeFromIndex_ofANotDoneDocument_changesNothing() = runBlocking<Unit> {
        val pending = doc("Pendiente.pdf", DocumentStatus.PENDING)
        val embedding = doc("A medias.pdf", DocumentStatus.EMBEDDING, chunks = 2)

        assertThat(index.removeFromIndex(pending)).isNull()
        assertThat(index.removeFromIndex(embedding)).isNull()
        assertThat(index.removeFromIndex(9_999)).isNull()

        assertThat(index.document(pending)!!.status).isEqualTo(DocumentStatus.PENDING)
        assertThat(chunkRows(embedding)).hasSize(2)
    }

    @Test
    fun deleteDocument_marksTheInt8IndexDirty() = runBlocking<Unit> {
        val target = doc("Borrar.pdf", chunks = 3)
        val shadow = index.addReindexShadow(target)
        index.insertChunks(shadow, listOf(chunk(0, content = "Sombra", vector = unitVector(42))))
        quantDirtyCleared()

        index.deleteDocument(target)

        assertThat(index.document(target)).isNull()
        assertThat(index.document(shadow)).isNull()
        assertThat(index.counts().chunks).isEqualTo(0)
        assertThat(ftsRows()).isEqualTo(0)
        assertThat(quantDirty()).isEqualTo("1")
    }
}

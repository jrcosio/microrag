package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DuplicateDocumentException
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexCounts
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.rag.text.SpanishText
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** F04 US1: schema, documents, atomic and idempotent batches, triggers and cascade (T018). */
@RunWith(AndroidJUnit4::class)
class RagIndexWriteTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private suspend fun addDoc(name: String = "Manual de motores", hash: String = name) =
        index.addDocument(NewDocument(displayName = name, contentHash = hash, mimeType = "application/pdf", sizeBytes = 1234))

    private fun ftsIds(term: String): List<Long> =
        temp.withRaw { db -> db.query("SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ?", "\"$term\"") { it.getLong(0) } }

    private fun integrityCheck() =
        temp.withRaw { it.exec("INSERT INTO chunks_fts(chunks_fts, rank) VALUES('integrity-check', 1)") }

    @Test
    fun newIndex_createsSchemaAndWritesMeta() = runBlocking<Unit> {
        assertThat(index.open()).isEqualTo(IndexCompatibility.Compatible)
        assertThat(index.compatibility.value).isEqualTo(IndexCompatibility.Compatible)

        val objects = temp.withRaw { db ->
            db.query(
                "SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT LIKE 'chunks_fts_%'",
            ) { "${it.getText(0)}:${it.getText(1)}" }
        }
        assertThat(objects).containsExactly(
            "table:rag_meta", "table:documents", "table:chunks", "index:idx_chunks_doc", "table:chunks_fts",
            "trigger:chunks_ai", "trigger:chunks_ad", "trigger:chunks_au",
            // Created by SQLite-Vector itself when it is loaded (research R3).
            "table:${RagConnection.VECTOR_EXTENSION_TABLE}",
        )
        assertThat(index.meta()).isEqualTo(RagIndexConfig.current().toMeta())
    }

    @Test
    fun documents_registerUpdateAndReject() = runBlocking<Unit> {
        val id = addDoc()

        val stored = index.document(id)!!
        assertThat(stored.status).isEqualTo(DocumentStatus.PENDING)
        assertThat(stored.included).isTrue()
        assertThat(stored.sizeBytes).isEqualTo(1234)
        assertThat(stored.createdAtMs).isEqualTo(1_000L)
        assertThat(stored.updatedAtMs).isEqualTo(1_000L)
        assertThat(temp.withRaw { db -> db.queryOne("SELECT source FROM documents") { it.getText(0) } }).isEqualTo("user")

        temp.now = 2_000L
        index.updateStatus(id, DocumentStatus.ERROR, error = "parse", chunkCount = 3)
        index.document(id)!!.let {
            assertThat(it.status).isEqualTo(DocumentStatus.ERROR)
            assertThat(it.error).isEqualTo("parse")
            assertThat(it.chunkCount).isEqualTo(3)
            assertThat(it.updatedAtMs).isEqualTo(2_000L)
            assertThat(it.createdAtMs).isEqualTo(1_000L)
        }
        index.updateStatus(id, DocumentStatus.DONE)
        index.document(id)!!.let {
            assertThat(it.error).isNull()
            assertThat(it.chunkCount).isEqualTo(3)
        }

        temp.now = 3_000L
        index.setIncluded(id, false)
        index.document(id)!!.let {
            assertThat(it.included).isFalse()
            assertThat(it.updatedAtMs).isEqualTo(3_000L)
        }
        assertThat(index.document(999)).isNull()

        val duplicate = runCatching { addDoc(name = "Otra copia", hash = "Manual de motores") }.exceptionOrNull()
        assertThat(duplicate).isInstanceOf(DuplicateDocumentException::class.java)
        assertThat((duplicate as DuplicateDocumentException).existingId).isEqualTo(id)
        assertThat(index.counts().documents).isEqualTo(1)
    }

    @Test
    fun batch_isStoredWithNormalizedColumnsAndIsIdempotent() = runBlocking<Unit> {
        val id = addDoc()
        val batch = (0..4).map { chunk(it, content = "Las bombas $it del circuito", headingPath = if (it == 0) null else "Capítulo 1 › Bombas") }

        assertThat(index.insertChunks(id, batch)).isEqualTo(5)
        assertThat(index.counts()).isEqualTo(IndexCounts(documents = 1, chunks = 5, ftsRows = 5))

        val norms = temp.withRaw { db ->
            db.query("SELECT ordinal, title_norm, body_norm FROM chunks ORDER BY ordinal") { Triple(it.getLong(0), it.getText(1), it.getText(2)) }
        }
        assertThat(norms[0]).isEqualTo(Triple(0L, SpanishText.normalize("Manual de motores"), SpanishText.normalize("Las bombas 0 del circuito")))
        assertThat(norms[1].second).isEqualTo(SpanishText.normalize("Manual de motores › Capítulo 1 › Bombas"))

        // SC-004: a retried batch changes nothing; an overlapping one adds only the new ordinals.
        assertThat(index.insertChunks(id, batch)).isEqualTo(0)
        assertThat(index.counts()).isEqualTo(IndexCounts(documents = 1, chunks = 5, ftsRows = 5))
        assertThat(index.insertChunks(id, (3..7).map { chunk(it) })).isEqualTo(3)
        assertThat(index.counts()).isEqualTo(IndexCounts(documents = 1, chunks = 8, ftsRows = 8))
        integrityCheck()
    }

    @Test
    fun invalidBatch_isRejectedWhole() = runBlocking<Unit> {
        val id = addDoc()
        val invalid = listOf(
            listOf(chunk(0), chunk(1, vector = FloatArray(255) { 0f }.also { it[0] = 1f })),
            listOf(chunk(0), chunk(1, vector = unitVector(1).map { it * 2f }.toFloatArray())),
            listOf(chunk(0), chunk(0)),
        )
        invalid.forEach { batch ->
            assertThat(runCatching { index.insertChunks(id, batch) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(runCatching { index.insertChunks(999, listOf(chunk(0))) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(index.counts().chunks).isEqualTo(0)
    }

    @Test
    fun failureInsideTheTransaction_rollsBackTheWholeBatch() = runBlocking<Unit> {
        val id = addDoc()
        temp.withRaw {
            it.exec("CREATE TRIGGER fail_on_3 BEFORE INSERT ON chunks WHEN NEW.ordinal = 3 BEGIN SELECT RAISE(ABORT, 'test'); END")
        }

        assertThat(runCatching { index.insertChunks(id, (0..5).map { chunk(it) }) }.isFailure).isTrue()
        assertThat(index.counts()).isEqualTo(IndexCounts(documents = 1, chunks = 0, ftsRows = 0))

        temp.withRaw { it.exec("DROP TRIGGER fail_on_3") }
        assertThat(index.insertChunks(id, (0..5).map { chunk(it) })).isEqualTo(6)
    }

    @Test
    fun everyBatch_marksTheQuantizationDirty() = runBlocking<Unit> {
        val id = addDoc()
        temp.withRaw { it.exec("UPDATE rag_meta SET value = '0' WHERE key = 'quant_dirty'") }
        assertThat(index.meta()["quant_dirty"]).isEqualTo("0")

        index.insertChunks(id, listOf(chunk(0)))

        assertThat(index.meta()["quant_dirty"]).isEqualTo("1")
    }

    @Test
    fun triggers_keepTheFtsIndexInSync() = runBlocking<Unit> {
        val id = addDoc()
        index.insertChunks(id, listOf(chunk(0, content = "Las bombas hidráulicas")))
        val chunkId = ftsIds("bomb").single()

        temp.withRaw { it.exec("UPDATE chunks SET body_norm = 'turbin' WHERE id = ?", chunkId) }

        assertThat(ftsIds("turbin")).containsExactly(chunkId)
        assertThat(ftsIds("bomb")).isEmpty()
        integrityCheck()
    }

    @Test
    fun deleteDocument_cascadesToChunksAndFts() = runBlocking<Unit> {
        val removed = addDoc(name = "Bombas", hash = "a")
        val kept = addDoc(name = "Turbinas", hash = "b")
        index.insertChunks(removed, (0..3).map { chunk(it, content = "bombas $it") })
        index.insertChunks(kept, (0..1).map { chunk(it, content = "turbinas $it") })

        index.deleteDocument(removed)

        assertThat(index.counts()).isEqualTo(IndexCounts(documents = 1, chunks = 2, ftsRows = 2))
        assertThat(index.document(removed)).isNull()
        assertThat(ftsIds("bomb")).isEmpty()
        assertThat(ftsIds("turbin")).hasSize(2)
        integrityCheck()
    }

    @Test
    fun reopen_keepsTheData() = runBlocking<Unit> {
        val id = addDoc()
        index.insertChunks(id, (0..2).map { chunk(it) })

        index.close()
        assertThat(index.compatibility.value).isEqualTo(IndexCompatibility.Closed)

        assertThat(index.counts()).isEqualTo(IndexCounts(documents = 1, chunks = 3, ftsRows = 3))
        assertThat(index.compatibility.value).isEqualTo(IndexCompatibility.Compatible)
    }

    @Test
    fun uncommittedTransaction_leavesNothingAfterReopen() = runBlocking<Unit> {
        addDoc()
        index.close()

        temp.raw().use { db ->
            db.exec("BEGIN IMMEDIATE")
            db.exec(
                "INSERT INTO documents(source, display_name, content_hash, created_at_ms, updated_at_ms) VALUES ('user', 'x', 'x', 1, 1)",
            )
        }

        assertThat(index.counts().documents).isEqualTo(1)
    }

    // F06 T008: queue operations (contracts/ingest-api.md).

    @Test
    fun documents_filterByStatusOldestFirst() = runBlocking<Unit> {
        val first = addDoc("Primero", "h1")
        temp.now = 2_000L
        val second = addDoc("Segundo", "h2")
        temp.now = 3_000L
        val third = addDoc("Tercero", "h3")
        index.updateStatus(second, DocumentStatus.DONE, chunkCount = 4)
        index.updateStatus(third, DocumentStatus.ERROR, error = "no_text")

        val queue = index.documents(setOf(DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.ERROR))
        assertThat(queue.map { it.id }).containsExactly(first, third).inOrder()
        assertThat(queue.last().error).isEqualTo("no_text")
        assertThat(index.documents(setOf(DocumentStatus.DONE)).map { it.id }).containsExactly(second)
        assertThat(index.documents(emptySet())).isEmpty()
    }

    @Test
    fun documentIdByHash_findsOnlyTheRegisteredHash() = runBlocking<Unit> {
        val id = addDoc("Manual", "abc123")

        assertThat(index.documentIdByHash("abc123")).isEqualTo(id)
        assertThat(index.documentIdByHash("otro")).isNull()
    }

    @Test
    fun documentChanges_increaseOnEveryCommittedWriteOnly() = runBlocking<Unit> {
        index.open()
        var last = index.documentChanges.value
        fun assertChanged(name: String) {
            assertWithMessage(name).that(index.documentChanges.value).isGreaterThan(last)
            last = index.documentChanges.value
        }

        val id = addDoc()
        assertChanged("addDocument")
        index.updateStatus(id, DocumentStatus.PARSED, chunkCount = 2)
        assertChanged("updateStatus")
        index.setIncluded(id, false)
        assertChanged("setIncluded")
        index.insertChunks(id, listOf(chunk(0)))
        assertChanged("insertChunks")

        index.document(id)
        index.documents(setOf(DocumentStatus.PARSED))
        index.counts()
        assertThat(index.documentChanges.value).isEqualTo(last)

        index.deleteDocument(id)
        assertChanged("deleteDocument")
        assertThat(runCatching { addDoc("Otro", "h-dup") }.isSuccess).isTrue()
        last = index.documentChanges.value
        assertThat(runCatching { addDoc("Duplicado", "h-dup") }.exceptionOrNull()).isInstanceOf(DuplicateDocumentException::class.java)
        assertThat(index.documentChanges.value).isEqualTo(last)
    }

    @Test
    fun updateStatus_keepsSkippedPagesUnlessGiven() = runBlocking<Unit> {
        val id = addDoc()

        index.updateStatus(id, DocumentStatus.PARSED, chunkCount = 12, skippedPages = 3)
        assertThat(index.document(id)!!.skippedPages).isEqualTo(3)

        index.updateStatus(id, DocumentStatus.PENDING)
        val stored = index.document(id)!!
        assertThat(stored.skippedPages).isEqualTo(3)
        assertThat(stored.chunkCount).isEqualTo(12)
    }
}

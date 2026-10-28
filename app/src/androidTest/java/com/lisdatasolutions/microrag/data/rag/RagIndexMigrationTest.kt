package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentNames
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DocumentTitle
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.rag.text.SpanishText
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real migrations on indexes with data: 1 → 2 (documents.skipped_pages, F06 T007, D-038) and
 * 2 → 3 (name_norm, replaces_id and the Library indexes, F08 T002, research R1).
 */
@RunWith(AndroidJUnit4::class)
class RagIndexMigrationTest {

    /** The released v2 schema (F06), a test asset: v2 files are built with it, never with the app's schema. */
    private val v2Schema: List<String> = RagSchema.split(
        InstrumentationRegistry.getInstrumentation().context.assets.open("schema/schema_v2.sql").bufferedReader().use { it.readText() },
    )

    /** One document per state, with names that fold differently; chunks for the done and embedding ones. */
    private val names = listOf(
        "Guía de instalación.pdf", "CATÁLOGO 2024.pdf", "Ñandú.txt", "Manual de motores.pdf", "Informe anual.md",
        "Notas_50%.txt", "Árbol.md",
    )

    /** Builds a version 2 file with raw SQL, as F06 left it. Returns the id of each state's document. */
    private fun versionTwoIndex(temp: TempRagIndex): Map<DocumentStatus, Long> = temp.withRaw { db ->
        db.enableWal()
        db.transaction {
            v2Schema.forEach { db.exec(it) }
            RagIndexConfig.current().copy(schemaVersion = 2).toMeta().forEach { (key, value) ->
                db.exec("INSERT INTO rag_meta(key, value) VALUES (?, ?)", key, value)
            }
            DocumentStatus.entries.zip(names).associate { (status, name) ->
                db.exec(
                    "INSERT INTO documents(source, display_name, content_hash, status, page_count, created_at_ms, updated_at_ms) " +
                        "VALUES ('user', ?, ?, ?, 3, ?, ?)",
                    name, "hash-${status.sqlValue}", status.sqlValue, status.ordinal.toLong(), status.ordinal.toLong(),
                )
                val id = checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
                when (status) {
                    DocumentStatus.DONE -> {
                        insertChunk(db, id, name, 0, "Cambio de aceite cada 500 horas.")
                        insertChunk(db, id, name, 1, "Revisión de los filtros del motor.")
                        db.exec("UPDATE documents SET chunk_count = 2 WHERE id = ?", id)
                    }
                    DocumentStatus.EMBEDDING -> insertChunk(db, id, name, 0, "Un fragmento a medias.", seed = 7)
                    else -> Unit
                }
                status to id
            }
        }
    }

    private fun insertChunk(db: RagConnection, documentId: Long, name: String, ordinal: Int, content: String, seed: Int = ordinal) {
        db.exec(
            "INSERT INTO chunks(document_id, ordinal, content, title_norm, body_norm, token_estimate, embedding) " +
                "VALUES (?, ?, ?, ?, ?, ?, vector_as_f32(?))",
            documentId, ordinal, content, SpanishText.normalize(DocumentTitle.of(name)), SpanishText.normalize(content),
            content.length / 4 + 1, unitVector(seed).toLeBlob(),
        )
    }

    /** A version 1 file: the v2 one without its column and with version 1. */
    private fun versionOneIndex(temp: TempRagIndex): Map<DocumentStatus, Long> {
        val ids = versionTwoIndex(temp)
        temp.withRaw { db ->
            db.exec("ALTER TABLE documents DROP COLUMN skipped_pages")
            db.exec("UPDATE rag_meta SET value = '1' WHERE key = ?", RagMetaKeys.SCHEMA_VERSION)
        }
        return ids
    }

    private fun columns(temp: TempRagIndex): List<String> =
        temp.withRaw { db -> db.query("SELECT name FROM pragma_table_info('documents')") { it.getText(0) } }

    private fun indexes(temp: TempRagIndex): List<String> =
        temp.withRaw { db -> db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'documents'") { it.getText(0) } }

    private fun nameNorms(temp: TempRagIndex): Map<String, String?> = temp.withRaw { db ->
        db.query("SELECT display_name, name_norm FROM documents") { it.getText(0) to if (it.isNull(1)) null else it.getText(1) }.toMap()
    }

    /** What every migrated index must still hold, whatever version it came from. */
    private suspend fun assertDataKept(temp: TempRagIndex, ids: Map<DocumentStatus, Long>) {
        ids.forEach { (status, id) ->
            val doc = temp.index.document(id)!!
            assertThat(doc.status).isEqualTo(status)
            assertThat(doc.pageCount).isEqualTo(3)
            assertThat(doc.replacesId).isNull()
        }
        assertThat(temp.index.document(ids.getValue(DocumentStatus.DONE))!!.chunkCount).isEqualTo(2)
        val counts = temp.index.counts()
        assertThat(counts.chunks).isEqualTo(3)
        assertThat(counts.ftsRows).isEqualTo(3)
        // Searches still find the done document only (F07 rule N7).
        val hit = temp.index.vectorSearch(unitVector(0), k = 1).single()
        assertThat(hit.distance).isWithin(1e-4).of(0.0)
        assertThat(temp.index.loadChunks(listOf(hit.id)).getValue(hit.id).documentId).isEqualTo(ids.getValue(DocumentStatus.DONE))
        assertThat(temp.index.vectorSearch(unitVector(7), k = 3).map { it.id }).hasSize(2)
        assertThat(temp.index.textSearch("aceite")).hasSize(1)
        assertThat(temp.index.textSearch("medias")).isEmpty()
    }

    @Test
    fun versionTwo_isMigratedKeepingItsDataAndFoldingItsNames() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            val ids = versionTwoIndex(temp)
            assertThat(columns(temp)).containsNoneOf("name_norm", "replaces_id")

            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Compatible)

            assertThat(temp.index.meta()[RagMetaKeys.SCHEMA_VERSION]).isEqualTo(RagIndexConfig.SCHEMA_VERSION.toString())
            assertThat(RagIndexConfig.SCHEMA_VERSION).isEqualTo(3)
            assertThat(columns(temp)).containsAtLeast("skipped_pages", "name_norm", "replaces_id")
            assertThat(indexes(temp)).containsAtLeast("idx_documents_created", "idx_documents_replaces")
            assertThat(nameNorms(temp)).isEqualTo(names.associateWith { DocumentNames.fold(it) })
            assertDataKept(temp, ids)

            // Reopening is a no-op: same version and names.
            temp.index.close()
            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Compatible)
            assertThat(nameNorms(temp)).isEqualTo(names.associateWith { DocumentNames.fold(it) })
        }
    }

    @Test
    fun versionOne_isMigratedThroughBothSteps() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            val ids = versionOneIndex(temp)
            assertThat(columns(temp)).doesNotContain("skipped_pages")

            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Compatible)

            assertThat(temp.index.meta()[RagMetaKeys.SCHEMA_VERSION]).isEqualTo(RagIndexConfig.SCHEMA_VERSION.toString())
            assertThat(columns(temp)).containsAtLeast("skipped_pages", "name_norm", "replaces_id")
            assertThat(nameNorms(temp).values).doesNotContain(null)
            assertDataKept(temp, ids)

            val done = ids.getValue(DocumentStatus.DONE)
            temp.index.updateStatus(done, DocumentStatus.DONE, chunkCount = 2, skippedPages = 1)
            assertThat(temp.index.document(done)!!.skippedPages).isEqualTo(1)
        }
    }

    @Test
    fun migratedIndex_deletesAShadowWithItsOriginal() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            val ids = versionTwoIndex(temp)
            val done = ids.getValue(DocumentStatus.DONE)
            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Compatible)
            temp.index.close()
            temp.withRaw { db ->
                db.exec(
                    "INSERT INTO documents(source, display_name, content_hash, status, replaces_id, created_at_ms, updated_at_ms) " +
                        "VALUES ('user', ?, 'hash-done#reindex', 'embedding', ?, 9, 9)",
                    names[DocumentStatus.DONE.ordinal], done,
                )
                val shadow = checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
                insertChunk(db, shadow, names[DocumentStatus.DONE.ordinal], 0, "Cambio de aceite cada 250 horas.", seed = 11)
            }
            assertThat(temp.index.counts().chunks).isEqualTo(4)

            temp.index.deleteDocument(done)

            val counts = temp.index.counts()
            assertThat(counts.documents).isEqualTo(names.size - 1)
            assertThat(counts.chunks).isEqualTo(1)
            assertThat(counts.ftsRows).isEqualTo(1)
            assertThat(temp.withRaw { db -> db.queryOne("SELECT count(*) FROM documents WHERE replaces_id IS NOT NULL") { it.getLong(0) } })
                .isEqualTo(0L)
        }
    }

    @Test
    fun failingMigration_leavesTheVersionOneFileUntouched() = runBlocking<Unit> {
        // The second step fails: the first one, in the same transaction, must not stay applied either.
        val migrations = listOf(RagMigrations.all.first { it.from == 1 }, RagMigration(from = 2, statements = listOf("NOT SQL")))
        TempRagIndex(migrations = migrations).use { temp ->
            versionOneIndex(temp)
            val before = temp.fileDigest()

            assertThat(temp.index.open()).isEqualTo(
                IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, RagIndexConfig.SCHEMA_VERSION.toString(), "1"),
            )
            temp.index.close()

            assertThat(temp.fileDigest()).isEqualTo(before)
            assertThat(columns(temp)).containsNoneOf("skipped_pages", "name_norm", "replaces_id")
        }
    }
}

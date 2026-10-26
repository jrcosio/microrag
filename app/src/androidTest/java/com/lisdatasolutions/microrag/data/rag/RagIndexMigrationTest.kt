package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** F06 T007: the real 1 → 2 migration (documents.skipped_pages, D-038) on an index with data. */
@RunWith(AndroidJUnit4::class)
class RagIndexMigrationTest {

    /** Builds a version 1 file: a current index with data, without the v2 column and with version 1. */
    private fun versionOneIndex(temp: TempRagIndex): Long = runBlocking {
        val id = temp.index.addDocument(NewDocument(displayName = "Manual de motores", contentHash = "hash-1", pageCount = 3))
        temp.index.insertChunks(id, listOf(chunk(0, content = "Cambio de aceite cada 500 horas."), chunk(1)))
        temp.index.updateStatus(id, DocumentStatus.DONE, chunkCount = 2)
        temp.index.close()
        temp.withRaw { db ->
            db.exec("ALTER TABLE documents DROP COLUMN skipped_pages")
            db.exec("UPDATE rag_meta SET value = '1' WHERE key = ?", RagMetaKeys.SCHEMA_VERSION)
        }
        id
    }

    private fun columns(temp: TempRagIndex): List<String> =
        temp.withRaw { db -> db.query("SELECT name FROM pragma_table_info('documents')") { it.getText(0) } }

    @Test
    fun versionOne_isMigratedKeepingItsData() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            val id = versionOneIndex(temp)
            assertThat(columns(temp)).doesNotContain("skipped_pages")

            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Compatible)

            assertThat(temp.index.meta()[RagMetaKeys.SCHEMA_VERSION]).isEqualTo(RagIndexConfig.SCHEMA_VERSION.toString())
            assertThat(columns(temp)).contains("skipped_pages")
            val doc = temp.index.document(id)!!
            assertThat(doc.status).isEqualTo(DocumentStatus.DONE)
            assertThat(doc.pageCount).isEqualTo(3)
            assertThat(doc.skippedPages).isNull()
            assertThat(temp.index.counts().chunks).isEqualTo(2)
            assertThat(temp.index.vectorSearch(unitVector(0), k = 1).single().distance).isWithin(1e-4).of(0.0)
            assertThat(temp.index.textSearch("aceite")).hasSize(1)

            temp.index.updateStatus(id, DocumentStatus.PARSED, chunkCount = 2, skippedPages = 1)
            assertThat(temp.index.document(id)!!.skippedPages).isEqualTo(1)
        }
    }

    @Test
    fun failingMigration_leavesTheVersionOneFileUntouched() = runBlocking<Unit> {
        TempRagIndex(migrations = listOf(RagMigration(from = 1, statements = listOf("NOT SQL")))).use { temp ->
            versionOneIndex(temp)
            val before = temp.fileDigest()

            assertThat(temp.index.open()).isEqualTo(
                IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, RagIndexConfig.SCHEMA_VERSION.toString(), "1"),
            )
            temp.index.close()

            assertThat(temp.fileDigest()).isEqualTo(before)
            assertThat(columns(temp)).doesNotContain("skipped_pages")
        }
    }
}

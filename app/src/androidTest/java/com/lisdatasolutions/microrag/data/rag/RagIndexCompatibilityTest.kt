package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** F04 US5: rag_meta validation, blocking, migrations and unreadable files (T040). */
@RunWith(AndroidJUnit4::class)
class RagIndexCompatibilityTest {

    /** A closed index with one document and two chunks, built with the app configuration. */
    private fun populated(temp: TempRagIndex) = runBlocking {
        val doc = temp.index.addDocument(NewDocument(displayName = "Manual", contentHash = "h"))
        temp.index.insertChunks(doc, listOf(chunk(0), chunk(1)))
        temp.index.close()
    }

    /** Every operation but compatibility, open and close is refused with the current state (rule A2). */
    private suspend fun assertEveryOperationIsRefused(index: RagIndex) {
        val operations: Map<String, suspend () -> Unit> = mapOf(
            "meta" to { index.meta() },
            "addDocument" to { index.addDocument(NewDocument(displayName = "x", contentHash = "x")) },
            "document" to { index.document(1) },
            "documents" to { index.documents(DocumentStatus.entries.toSet()) },
            "documentIdByHash" to { index.documentIdByHash("h") },
            "updateStatus" to { index.updateStatus(1, DocumentStatus.DONE) },
            "setIncluded" to { index.setIncluded(1, false) },
            "deleteDocument" to { index.deleteDocument(1) },
            "storedChunkCounts" to { index.storedChunkCounts(listOf(1L)) },
            "storedChunkContents" to { index.storedChunkContents(1) },
            "deleteChunks" to { index.deleteChunks(1) },
            "insertChunks" to { index.insertChunks(1, listOf(chunk(5))) },
            "vectorSearch" to { index.vectorSearch(unitVector(0)) },
            "textSearch" to { index.textSearch("manual") },
            "loadChunks" to { index.loadChunks(listOf(1L)) },
            "neighbor" to { index.neighbor(1, 0) },
            "counts" to { index.counts() },
        )
        operations.forEach { (name, operation) ->
            val error = runCatching { operation() }.exceptionOrNull()
            assertWithMessage(name).that(error).isInstanceOf(IndexUnavailableException::class.java)
            assertWithMessage(name).that((error as IndexUnavailableException).state).isEqualTo(index.compatibility.value)
        }
    }

    @Test
    fun freshIndex_isCompatibleAndSearchable() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Compatible)
            assertThat(temp.index.vectorSearch(unitVector(0))).isEmpty()
        }
    }

    @Test
    fun anotherVectorSpace_blocksEveryOperationWithoutTouchingTheFile() = runBlocking<Unit> {
        val cases = listOf(
            Triple(RagMetaKeys.EMBEDDING_MODEL_SHA256, "0".repeat(64), RagMetaKeys.EMBEDDING_MODEL_SHA256),
            Triple(RagMetaKeys.EMBEDDING_DIMS, "768", RagMetaKeys.EMBEDDING_DIMS),
            Triple(RagMetaKeys.EMBEDDING_DOC_PREFIX, "task: search result | text:", RagMetaKeys.EMBEDDING_DOC_PREFIX),
            Triple(RagMetaKeys.EMBEDDING_QUERY_PREFIX, "query: ", RagMetaKeys.EMBEDDING_QUERY_PREFIX),
            Triple(RagMetaKeys.NORMALIZER_VERSION, "2", RagMetaKeys.NORMALIZER_VERSION),
            Triple(RagMetaKeys.EMBEDDING_DIMS, null, "missing:${RagMetaKeys.EMBEDDING_DIMS}"),
        )
        cases.forEach { (key, value, field) ->
            TempRagIndex().use { temp ->
                populated(temp)
                val expected = runBlocking { temp.index.open(); temp.index.meta().getValue(key).also { temp.index.close() } }
                temp.withRaw { db ->
                    if (value == null) {
                        db.exec("DELETE FROM rag_meta WHERE key = ?", key)
                    } else {
                        db.exec("UPDATE rag_meta SET value = ? WHERE key = ?", value, key)
                    }
                }
                val before = temp.fileDigest()

                assertWithMessage(field).that(temp.index.open()).isEqualTo(IndexCompatibility.Incompatible(field, expected, value))
                assertEveryOperationIsRefused(temp.index)
                assertThat(temp.index.open()).isEqualTo(temp.index.compatibility.value)
                temp.index.close()

                assertWithMessage(field).that(temp.fileDigest()).isEqualTo(before)
            }
        }
    }

    @Test
    fun newerSchema_isIncompatibleAndUntouched() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            populated(temp)
            temp.withRaw { it.exec("UPDATE rag_meta SET value = ? WHERE key = ?", "$NEWER", RagMetaKeys.SCHEMA_VERSION) }
            val before = temp.fileDigest()

            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, "$CURRENT", "$NEWER"))
            temp.index.close()

            assertThat(temp.fileDigest()).isEqualTo(before)
        }
    }

    @Test
    fun olderSchema_withMigration_isMigratedAndOpened() = runBlocking<Unit> {
        // The generic mechanism, from a made-up version 0: every step up to the current one must exist.
        val migrations = listOf(RagMigration(from = 0, statements = listOf("CREATE TABLE migrated_marker (x INTEGER)"))) +
            (1 until CURRENT).map { RagMigration(from = it, statements = emptyList()) }
        TempRagIndex(migrations = migrations).use { temp ->
            populated(temp)
            temp.withRaw { it.exec("UPDATE rag_meta SET value = '0' WHERE key = ?", RagMetaKeys.SCHEMA_VERSION) }

            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Compatible)
            assertThat(temp.index.meta()[RagMetaKeys.SCHEMA_VERSION]).isEqualTo("$CURRENT")
            assertThat(temp.index.counts().chunks).isEqualTo(2)
            assertThat(temp.withRaw { it.tableNames() }).contains("migrated_marker")
        }
    }

    @Test
    fun olderSchema_withoutMigration_isIncompatibleAndUntouched() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            populated(temp)
            temp.withRaw { it.exec("UPDATE rag_meta SET value = '0' WHERE key = ?", RagMetaKeys.SCHEMA_VERSION) }
            val before = temp.fileDigest()

            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, "$CURRENT", "0"))
            temp.index.close()

            assertThat(temp.fileDigest()).isEqualTo(before)
        }
    }

    @Test
    fun failingMigration_rollsBackAndLeavesTheFileUntouched() = runBlocking<Unit> {
        val migrations = listOf(RagMigration(from = 0, statements = listOf("CREATE TABLE partial_marker (x INTEGER)", "NOT SQL"))) +
            (1 until CURRENT).map { RagMigration(from = it, statements = emptyList()) }
        TempRagIndex(migrations = migrations).use { temp ->
            populated(temp)
            temp.withRaw { it.exec("UPDATE rag_meta SET value = '0' WHERE key = ?", RagMetaKeys.SCHEMA_VERSION) }
            val before = temp.fileDigest()

            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, "$CURRENT", "0"))
            temp.index.close()

            assertThat(temp.fileDigest()).isEqualTo(before)
            assertThat(temp.withRaw { it.tableNames() }).doesNotContain("partial_marker")
        }
    }

    @Test
    fun notADatabase_isUnreadableAndKept() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            val garbage = Random(7).nextBytes(8192)
            temp.dbFile.writeBytes(garbage)

            val state = temp.index.open()

            assertThat(state).isInstanceOf(IndexCompatibility.Unreadable::class.java)
            assertEveryOperationIsRefused(temp.index)
            temp.index.close()
            assertThat(temp.dbFile.readBytes()).isEqualTo(garbage)
        }
    }

    @Test
    fun missingExtension_isUnreadableWithTheReason() = runBlocking<Unit> {
        TempRagIndex(extensionPath = "/nonexistent/vector.so").use { temp ->
            assertThat(temp.index.open()).isEqualTo(IndexCompatibility.Unreadable("extension: vector.so not found"))
            assertEveryOperationIsRefused(temp.index)
        }
    }

    private companion object {
        const val CURRENT = RagIndexConfig.SCHEMA_VERSION
        const val NEWER = CURRENT + 1
    }
}

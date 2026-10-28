package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F09 T001 on real SQLite (research R2, rule H5): the Home summary counts every document but reindex
 * shadows, as the Library's «Todos», and exactly the stored chunks of the done documents.
 */
@RunWith(AndroidJUnit4::class)
class RagIndexSummaryTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private val all = DocumentStatus.entries.toSet()

    /** [stored] chunks are inserted; [chunkCount] is what the runner records at that stage (planned, or produced when done). */
    private suspend fun doc(name: String, status: DocumentStatus, stored: Int = 0, chunkCount: Int? = stored.takeIf { it > 0 }, seed: Int = 0): Long {
        temp.now += 10
        val id = index.addDocument(
            NewDocument(displayName = name, contentHash = name, mimeType = "application/pdf", sizeBytes = 2048, pageCount = 3, localPath = "/copies/$name"),
        )
        if (stored > 0) index.insertChunks(id, (0 until stored).map { chunk(it, content = "$name $it", vector = unitVector(seed * 1_000 + it)) })
        index.updateStatus(id, status, error = if (status == DocumentStatus.ERROR) "no_text" else null, chunkCount = chunkCount)
        return id
    }

    /** Stored chunks of done documents that are not shadows, counted on the chunks themselves. */
    private fun storedChunksOfDoneDocuments(): Int = temp.withRaw { db ->
        db.queryOne(
            "SELECT count(*) FROM chunks c JOIN documents d ON d.id = c.document_id WHERE d.status = 'done' AND d.replaces_id IS NULL",
        ) { it.getLong(0).toInt() } ?: 0
    }

    private suspend fun assertRuleH5(): LibrarySummary {
        val summary = index.librarySummary()
        assertThat(summary.documents).isEqualTo(index.libraryCount("", all))
        assertThat(summary.indexedChunks).isEqualTo(storedChunksOfDoneDocuments())
        return summary
    }

    @Test
    fun anEmptyIndex_countsNothing() = runBlocking<Unit> {
        assertThat(assertRuleH5()).isEqualTo(LibrarySummary(documents = 0, indexedChunks = 0))
    }

    @Test
    fun everyState_countsAsADocument_andOnlyDoneChunksCount() = runBlocking<Unit> {
        doc("Pendiente.pdf", DocumentStatus.PENDING)
        doc("Preparado.pdf", DocumentStatus.PARSED, chunkCount = 40)
        // Half-way through embedding: chunks already stored, and the planned total recorded.
        doc("A medias.pdf", DocumentStatus.EMBEDDING, stored = 3, chunkCount = 8, seed = 1)
        doc("Incluido.pdf", DocumentStatus.DONE, stored = 5, seed = 2)
        val excluded = doc("Excluido.pdf", DocumentStatus.DONE, stored = 4, seed = 3)
        index.setIncluded(excluded, false)
        doc("Con error.pdf", DocumentStatus.ERROR)
        doc("Fuera.pdf", DocumentStatus.OUT_OF_INDEX)

        assertThat(assertRuleH5()).isEqualTo(LibrarySummary(documents = 7, indexedChunks = 9))
    }

    @Test
    fun aShadowHalfWay_countsNeither_untilItReplacesItsOriginal() = runBlocking<Unit> {
        val original = doc("Manual.pdf", DocumentStatus.DONE, stored = 4)
        doc("Otro.pdf", DocumentStatus.DONE, stored = 2, seed = 5)
        val shadow = index.addReindexShadow(original)
        index.updateStatus(shadow, DocumentStatus.PARSED, chunkCount = 6)
        index.insertChunks(shadow, (0 until 6).map { chunk(it, content = "Nuevo $it", vector = unitVector(700 + it)) })
        index.updateStatus(shadow, DocumentStatus.EMBEDDING)

        assertThat(assertRuleH5()).isEqualTo(LibrarySummary(documents = 2, indexedChunks = 6))

        index.publishReindex(shadow)

        assertThat(assertRuleH5()).isEqualTo(LibrarySummary(documents = 2, indexedChunks = 8))
    }

    @Test
    fun removingFromTheIndexAndDeleting_followTheChunks() = runBlocking<Unit> {
        val removed = doc("Quitado.pdf", DocumentStatus.DONE, stored = 3)
        val deleted = doc("Borrado.pdf", DocumentStatus.DONE, stored = 2, seed = 1)
        doc("Queda.pdf", DocumentStatus.DONE, stored = 5, seed = 2)
        assertThat(assertRuleH5()).isEqualTo(LibrarySummary(documents = 3, indexedChunks = 10))

        index.removeFromIndex(removed)
        assertThat(assertRuleH5()).isEqualTo(LibrarySummary(documents = 3, indexedChunks = 7))

        index.deleteDocument(deleted)
        assertThat(assertRuleH5()).isEqualTo(LibrarySummary(documents = 2, indexedChunks = 5))
    }
}

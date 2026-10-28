package com.lisdatasolutions.microrag.data.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeTextEmbedder
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.testing.TempLibrary
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F08 T039 on SQLite-Vector with the real runner and a deterministic embedder (SC-005, SC-006):
 * reindexing keeps the previous version answering until the shadow is published in one transaction,
 * survives an interruption, and «Quitar del índice» then «Reindexar» gives back the same chunks.
 */
@RunWith(AndroidJUnit4::class)
class ReindexFlowTest {

    private val library = TempLibrary()
    private val index = library.temp.index
    private val pdf = PlatformPdfOpener()

    @After
    fun tearDown() = library.close()

    private fun runner(sessions: FakeEmbeddingSessions) = IngestRunner(
        index, DocumentExtractor(pdf), sessions, library.runStore, FakeThermalStatusSource(), { Long.MAX_VALUE }, Dispatchers.IO, library.logger,
    )

    /** About 430 chunks of Markdown, one per section, so every chunk has its heading. */
    private val manual: String = (1..SECTIONS).joinToString("\n\n") { s ->
        "## Sección $s\n\n" + (1..4).joinToString("\n\n") { p ->
            "Párrafo $p de la sección $s: la bomba número $s se revisa cada $p semanas y se anota en el registro de mantenimiento de la planta."
        }
    }

    private suspend fun indexedDocument(name: String, file: File, mime: String): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = mime, sizeBytes = file.length(), localPath = file.path))
        runner(FakeEmbeddingSessions()).process(listOf(id)) { }
        assertThat(index.document(id)!!.status).isEqualTo(DocumentStatus.DONE)
        return id
    }

    private data class Row(val id: Long, val ordinal: Int, val content: String, val page: Int?, val heading: String?)

    private fun rows(documentId: Long): List<Row> = library.temp.withRaw { db ->
        db.query("SELECT id, ordinal, content, page, heading_path FROM chunks WHERE document_id = ? ORDER BY ordinal", documentId) {
            Row(it.getLong(0), it.getLong(1).toInt(), it.getText(2), if (it.isNull(3)) null else it.getLong(3).toInt(), if (it.isNull(4)) null else it.getText(4))
        }
    }

    /** One vector and one text search; the documents and chunk ids they found. */
    private suspend fun search(seed: Int): Pair<Set<Long>, Set<Long>> {
        val vector = index.vectorSearch(FakeTextEmbedder.vectorOf("consulta $seed"), k = 20).map { it.id }
        val text = index.textSearch("bomba revisa", k = 20)
        val ids = (vector + text).toSet()
        return index.loadChunks(ids).values.mapTo(HashSet()) { it.documentId } to ids
    }

    @Test
    fun whileReindexing_searchesFindThePreviousVersion_untilItIsReplacedInOneStep() = runBlocking<Unit> {
        val id = indexedDocument("Manual.md", library.copy("manual.md", manual), "text/markdown")
        val before = rows(id)
        assertThat(before.size).isGreaterThan(400)
        val oldIds = before.map { it.id }.toSet()

        library.repository.reindex(id)
        val shadow = index.shadowOf(id)!!.id
        assertThat(library.runStore.run.first().documentIds).containsExactly(shadow)

        val sessions = FakeEmbeddingSessions()
        val seen = Collections.synchronizedList(mutableListOf<Pair<Set<Long>, Set<Long>>>())
        var calls = 0
        sessions.embedder.beforeEmbedding = { if (++calls % 20 == 0) runBlocking { seen += search(calls) } }
        runner(sessions).process(listOf(shadow)) { }

        // Never empty, never the shadow, never a mix of versions (SC-006).
        assertThat(seen.size).isAtLeast(before.size / 20)
        seen.forEach { (documents, ids) ->
            assertThat(documents).containsExactly(id)
            assertThat(oldIds).containsAtLeastElementsIn(ids)
        }
        // Published: the shadow's chunks under the original, same texts by ordinal.
        assertThat(index.document(shadow)).isNull()
        val after = rows(id)
        assertThat(after.map { it.ordinal to it.content }).isEqualTo(before.map { it.ordinal to it.content })
        assertThat(after.map { it.id }.toSet().intersect(oldIds)).isEmpty()
        val (documents, ids) = search(-1)
        assertThat(documents).containsExactly(id)
        assertThat(after.map { it.id }.toSet()).containsAtLeastElementsIn(ids)
        assertThat(index.document(id)!!.chunkCount).isEqualTo(before.size)
        assertThat(library.runStore.run.first().documentIds).isEmpty()
        library.temp.withRaw { it.exec("INSERT INTO chunks_fts(chunks_fts, rank) VALUES('integrity-check', 1)") }
    }

    @Test
    fun anInterruptedReindex_leavesTheOriginalIntact_andResumes() = runBlocking<Unit> {
        val id = indexedDocument("Manual.md", library.copy("manual.md", manual), "text/markdown")
        val before = rows(id)
        library.repository.reindex(id)
        val shadow = index.shadowOf(id)!!.id

        // Stopped halfway, as when the process dies or the work is cancelled.
        val sessions = FakeEmbeddingSessions()
        lateinit var job: Job
        var calls = 0
        sessions.embedder.beforeEmbedding = { if (++calls == STOP_AT) job.cancel() }
        job = launch(Dispatchers.Default) { runner(sessions).process(listOf(shadow)) { } }
        job.join()

        assertThat(rows(id)).isEqualTo(before)
        assertThat(index.document(id)!!.status).isEqualTo(DocumentStatus.DONE)
        val partial = index.storedChunkCounts(listOf(shadow)).getValue(shadow)
        assertThat(partial).isIn(STOP_AT - IngestRunner.BATCH..STOP_AT)
        assertThat(index.document(shadow)!!.status).isEqualTo(DocumentStatus.EMBEDDING)

        val resumed = FakeEmbeddingSessions()
        runner(resumed).process(listOf(shadow)) { }

        assertThat(resumed.embedder.embedded.size).isEqualTo(before.size - partial)
        assertThat(index.document(shadow)).isNull()
        assertThat(rows(id).map { it.ordinal to it.content }).isEqualTo(before.map { it.ordinal to it.content })
    }

    @Test
    fun removedFromTheIndex_thenReindexed_givesTheSameChunks_SC005() = runBlocking<Unit> {
        val markdown = indexedDocument("Manual.md", library.copy("manual.md", manual.take(40_000)), "text/markdown")
        val documents = mutableListOf(markdown)
        if (pdf.isAvailable) {
            val asset = InstrumentationRegistry.getInstrumentation().context.assets.open("pdf/text_3_pages.pdf")
            val file = File(library.dir, "pages.pdf").apply { outputStream().use { asset.copyTo(it) } }
            documents += indexedDocument("Páginas.pdf", file, "application/pdf")
        }
        documents.forEach { id ->
            val before = rows(id)

            library.repository.removeFromIndex(id)
            assertThat(rows(id)).isEmpty()
            assertThat(index.document(id)!!.status).isEqualTo(DocumentStatus.OUT_OF_INDEX)
            library.repository.reindex(id)
            assertThat(index.document(id)!!.status).isEqualTo(DocumentStatus.PENDING)
            assertThat(library.runStore.run.first().documentIds).contains(id)
            runner(FakeEmbeddingSessions()).process(listOf(id)) { }

            val after = rows(id)
            assertWithMessage("document $id").that(after.map { Triple(it.ordinal, it.content, it.page) to it.heading })
                .isEqualTo(before.map { Triple(it.ordinal, it.content, it.page) to it.heading })
            assertThat(index.document(id)!!.status).isEqualTo(DocumentStatus.DONE)
        }
        if (pdf.isAvailable) assertThat(rows(documents.last()).mapNotNull { it.page }).isNotEmpty()
        assertThat(rows(markdown).all { it.heading != null }).isTrue()
    }

    @Test
    fun aReindexCannotStartWithoutThePrivateCopy() = runBlocking<Unit> {
        val copy = library.copy("manual.md", manual.take(20_000))
        val id = indexedDocument("Manual.md", copy, "text/markdown")
        assumeTrue(copy.delete())

        library.repository.reindex(id)

        assertThat(index.shadowOf(id)).isNull()
        assertThat(library.runStore.run.first().documentIds).isEmpty()
    }

    private companion object {
        const val SECTIONS = 430
        const val STOP_AT = 200
    }
}

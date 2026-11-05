package com.lisdatasolutions.microrag.domain.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F11 T049 (research R12, FR-027): whether a cited source can still be opened. */
class ResolveSourcesUseCaseTest {

    private val index = FakeRagIndex()
    private val resolve = ResolveSourcesUseCase(index)

    private suspend fun document(name: String): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        index.insertChunks(id, (0..1).map { ChunkInput(it, null, null, "texto $it", 1, FloatArray(256)) })
        index.updateStatus(id, DocumentStatus.DONE, null, 2, null)
        return id
    }

    /** Cited after every document of these tests was created, unless [citedAt] says otherwise (Q15). */
    private fun source(documentId: Long, chunkId: Long, citedAt: Long = 9_999L) =
        CitedSource(AnswerSource(1, 1, documentId, chunkId, 0, 0, "Doc.pdf", 1), citedAt)

    private fun chunkIdOf(documentId: Long, ordinal: Int): Long = index.chunkId(documentId, ordinal)

    @Test
    fun available_deleted_andFragmentGone() = runTest {
        val kept = document("Kept.txt")
        val deleted = document("Deleted.txt")
        val reindexed = document("Reindexed.txt")
        val sources = listOf(source(kept, chunkIdOf(kept, 0)), source(deleted, chunkIdOf(deleted, 0)), source(reindexed, 999L))
        index.deleteDocument(deleted)

        assertThat(resolve(sources).first()).containsExactly(
            sources[0], SourceAvailability.AVAILABLE,
            sources[1], SourceAvailability.DOCUMENT_GONE,
            sources[2], SourceAvailability.FRAGMENT_GONE,
        )
    }

    @Test
    fun aDocumentExcludedFromSearch_stillOpens() = runTest {
        val id = document("Excluded.txt")
        index.setIncluded(id, false)
        assertThat(resolve(listOf(source(id, chunkIdOf(id, 1)))).first().values.single()).isEqualTo(SourceAvailability.AVAILABLE)
    }

    @Test
    fun withTheIndexUnavailable_everySourceReadsAvailable() = runTest {
        val id = document("Doc.txt")
        index.setCompatibility(IndexCompatibility.Unreadable("x"))
        assertThat(resolve(listOf(source(id, 12345L))).first().values.single()).isEqualTo(SourceAvailability.AVAILABLE)
    }

    @Test
    fun itFollowsTheChangesOfTheIndex() = runTest(UnconfinedTestDispatcher()) {
        val id = document("Doc.txt")
        val seen = mutableListOf<Map<CitedSource, SourceAvailability>>()
        val job = launch { resolve(listOf(source(id, chunkIdOf(id, 0)))).toList(seen) }
        runCurrent()
        index.deleteDocument(id)
        runCurrent()
        assertThat(seen.map { it.values.single() }).containsExactly(SourceAvailability.AVAILABLE, SourceAvailability.DOCUMENT_GONE).inOrder()
        job.cancel()
    }

    @Test
    fun noSources_noLookups() = runTest {
        assertThat(resolve(emptyList()).first()).isEmpty()
        assertThat(index.calls).doesNotContain("open")
    }

    /**
     * F13 Q15 (analyze AN-01): `rag.db` reuses the id of a deleted last document, so a document created
     * after the answer is another one, even with the cited id and chunk.
     */
    @Test
    fun aDocumentCreatedAfterTheAnswer_isNotItsSource() = runTest {
        index.now = 5_000L
        val id = document("Nuevo.txt")
        val chunk = chunkIdOf(id, 0)

        assertThat(resolve(listOf(source(id, chunk, citedAt = 4_000L))).first().values.single()).isEqualTo(SourceAvailability.DOCUMENT_GONE)
        assertThat(resolve(listOf(source(id, chunk, citedAt = 6_000L))).first().values.single()).isEqualTo(SourceAvailability.AVAILABLE)
    }

    // F20 T042 (contract H3, H4; research R10).

    private suspend fun withPages(name: String, vararg pages: Int, mime: String = "application/pdf"): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = mime))
        pages.forEach { index.insertPage(id, PageInput(it, "$id-$it.jpg", 10, 10, 1, FloatArray(256))) }
        index.updateStatus(id, DocumentStatus.DONE, null, 0, null)
        return id
    }

    private fun pageSource(documentId: Long, page: Int, kind: SourceKind = SourceKind.PAGE, citedAt: Long = 9_999L) =
        CitedSource(AnswerSource(1, 2, documentId, 0, 0, 0, "Informe.pdf", page, kind), citedAt)

    @Test
    fun aCitedPage_isAvailable_gone_orItsDocumentGone() = runTest {
        val kept = withPages("Informe.pdf", 1, 4)
        val deleted = withPages("Viejo.pdf", 2)
        val poster = withPages("cartel.jpg", 1, mime = "image/jpeg")
        val sources = listOf(
            pageSource(kept, 4), pageSource(kept, 9), pageSource(deleted, 2), pageSource(poster, 1, SourceKind.IMAGE),
            source(kept, 12345L),
        )
        index.deleteDocument(deleted)

        assertThat(resolve(sources).first()).containsExactly(
            sources[0], SourceAvailability.AVAILABLE,
            sources[1], SourceAvailability.PAGE_GONE,
            sources[2], SourceAvailability.DOCUMENT_GONE,
            sources[3], SourceAvailability.AVAILABLE,
            sources[4], SourceAvailability.FRAGMENT_GONE,
        )
    }

    @Test
    fun aPageOfADocumentCreatedAfterTheAnswer_isNotItsSource() = runTest {
        index.now = 5_000L
        val id = withPages("Informe.pdf", 4)

        assertThat(resolve(listOf(pageSource(id, 4, citedAt = 4_000L))).first().values.single()).isEqualTo(SourceAvailability.DOCUMENT_GONE)
    }
}

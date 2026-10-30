package com.lisdatasolutions.microrag.domain.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
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

    private fun source(documentId: Long, chunkId: Long) = AnswerSource(1, 1, documentId, chunkId, 0, 0, "Doc.pdf", 1)

    private fun chunkIdOf(documentId: Long, ordinal: Int): Long = index.chunkId(documentId, ordinal)

    @Test
    fun available_deleted_andFragmentGone() = runTest {
        val kept = document("Kept.txt")
        val deleted = document("Deleted.txt")
        val reindexed = document("Reindexed.txt")
        val sources = listOf(source(kept, chunkIdOf(kept, 0)), source(deleted, chunkIdOf(deleted, 0)), source(reindexed, 999L))
        index.deleteDocument(deleted)

        assertThat(resolve(sources).first()).containsExactly(
            chunkIdOf(kept, 0), SourceAvailability.AVAILABLE,
            sources[1].chunkId, SourceAvailability.DOCUMENT_GONE,
            999L, SourceAvailability.FRAGMENT_GONE,
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
        val seen = mutableListOf<Map<Long, SourceAvailability>>()
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
}

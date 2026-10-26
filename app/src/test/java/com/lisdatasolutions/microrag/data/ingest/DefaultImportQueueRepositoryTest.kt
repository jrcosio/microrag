package com.lisdatasolutions.microrag.data.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.testing.FakeDocumentSource
import com.lisdatasolutions.microrag.testing.FakeIngestScheduler
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** F06 US1 (T020): the queue combines attempts, documents and the work (research R6). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultImportQueueRepositoryTest {

    private val dir: File = Files.createTempDirectory("documents").toFile()
    private val source = FakeDocumentSource()
    private val pdf = FakePdfOpener()
    private val index = FakeRagIndex()
    private val scheduler = FakeIngestScheduler()
    private val files = DocumentFileStore(dir)
    private val logger = RecordingLogger()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun TestScope.repository(): DefaultImportQueueRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val importer = DocumentImporter(source, files, pdf, index, { Long.MAX_VALUE / 2 }, logger)
        return DefaultImportQueueRepository(importer, source, index, files, scheduler, pdf, backgroundScope, dispatcher, logger) { 0L }
    }

    private fun TestScope.collect(repository: DefaultImportQueueRepository): MutableList<ImportQueue> {
        val values = mutableListOf<ImportQueue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.queue.toList(values) }
        return values
    }

    @Test
    fun anImportShowsAnAttemptThatBecomesAReadyDocument() = runTest {
        source.add("content://1", "Manual.pdf", FakePdfOpener.pdf("uno"), "application/pdf")
        val repository = repository()
        val values = collect(repository)

        repository.import(listOf("content://1"))
        advanceUntilIdle()

        val states = values.flatMap { queue -> queue.items.map { it.key to it.state } }
        assertThat(states.any { (key, state) -> key is QueueKey.Attempt && state is QueueItemState.Importing }).isTrue()
        val last = values.last()
        assertThat(last.items).hasSize(1)
        assertThat(last.items.single().key).isEqualTo(QueueKey.Document(1))
        assertThat(last.items.single().state).isEqualTo(QueueItemState.Ready)
        assertThat(last.items.single().displayName).isEqualTo("Manual.pdf")
        assertThat(last.readyCount).isEqualTo(1)
    }

    @Test
    fun importsRunInSelectionOrderAndAttemptsComeFirst() = runTest {
        source.add("content://a", "a.txt", "uno".toByteArray(), "text/plain")
        source.add("content://b", "b.txt", "dos".toByteArray(), "text/plain")
        source.add("content://c", "c.txt", ByteArray(0), "text/plain")
        val repository = repository()
        val values = collect(repository)

        repository.import(listOf("content://a", "content://b", "content://c"))
        advanceUntilIdle()

        assertThat(source.opened).containsExactly("content://a", "content://b", "content://c").inOrder()
        val items = values.last().items
        assertThat(items.map { it.displayName }).containsExactly("c.txt", "a.txt", "b.txt").inOrder()
        assertThat(items.first().state).isEqualTo(QueueItemState.ImportFailed(IngestError.NO_TEXT))
        assertThat(values.last().readyCount).isEqualTo(2)
    }

    @Test
    fun orphansAreCleanedOnceOnTheFirstCollection() = runTest {
        val orphan = File(dir, "a".repeat(64) + ".pdf").apply { writeText("orphan") }
        val repository = repository()

        collect(repository)
        advanceUntilIdle()
        assertThat(orphan.exists()).isFalse()

        val later = File(dir, "b".repeat(64) + ".pdf").apply { writeText("later") }
        collect(repository)
        advanceUntilIdle()
        assertThat(later.exists()).isTrue()
    }

    @Test
    fun pdfSupportAndIndexAvailabilityAreReported() = runTest {
        pdf.isAvailableValue = false
        index.setCompatibility(IndexCompatibility.Incompatible("embedding_dims", "256", "768"))
        source.add("content://1", "notas.txt", "texto".toByteArray(), "text/plain")
        val repository = repository()
        val values = collect(repository)

        repository.import(listOf("content://1"))
        advanceUntilIdle()

        val last = values.last()
        assertThat(last.pdfSupported).isFalse()
        assertThat(last.indexAvailable).isFalse()
        assertThat(last.items.single().state).isEqualTo(QueueItemState.ImportFailed(IngestError.INDEX_UNAVAILABLE))
    }

    @Test
    fun workStateMapsToQueuedAndProgress() = runTest {
        source.add("content://1", "a.txt", "uno".toByteArray(), "text/plain")
        source.add("content://2", "b.txt", "dos".toByteArray(), "text/plain")
        val repository = repository()
        val values = collect(repository)
        repository.import(listOf("content://1", "content://2"))
        advanceUntilIdle()

        scheduler.activeDocumentIds.value = setOf(1L, 2L)
        scheduler.progress.value = com.lisdatasolutions.microrag.domain.ingest.IngestProgress(
            1L, com.lisdatasolutions.microrag.domain.ingest.IngestProgress.Stage.EXTRACTING, 3, 10,
        )
        advanceUntilIdle()

        val states = values.last().items.associate { it.key to it.state }
        assertThat(states[QueueKey.Document(1)]).isEqualTo(QueueItemState.Extracting(3, 10))
        assertThat(states[QueueKey.Document(2)]).isEqualTo(QueueItemState.Queued)
        assertThat(values.last().readyCount).isEqualTo(0)
    }
}

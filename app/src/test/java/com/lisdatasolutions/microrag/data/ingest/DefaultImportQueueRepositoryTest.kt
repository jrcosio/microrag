package com.lisdatasolutions.microrag.data.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.DefaultIndexingRepository
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.testing.FakeDocumentSource
import com.lisdatasolutions.microrag.testing.FakeFile
import com.lisdatasolutions.microrag.testing.FakeIngestScheduler
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
    private val runStore = InMemoryIngestRunStore()
    private val files = DocumentFileStore(dir)
    private val logger = RecordingLogger()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun TestScope.repository(): DefaultImportQueueRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val importer = DocumentImporter(source, files, pdf, index, { Long.MAX_VALUE / 2 }, logger)
        val indexing = DefaultIndexingRepository(index, scheduler, runStore, FakeThermalStatusSource(), dispatcher, logger)
        return DefaultImportQueueRepository(importer, source, index, files, scheduler, pdf, indexing, runStore, backgroundScope, dispatcher, logger) { 0L }
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

    // F06 US2 (T032): errors never stop the others; retry and remove (contracts I3, I7, I8).

    @Test
    fun anErrorDoesNotStopTheNextImports() = runTest {
        source.add("content://bad", "falso.pdf", "no es pdf".toByteArray(), "application/pdf")
        source.add("content://good", "bueno.txt", "texto".toByteArray(), "text/plain")
        val repository = repository()
        val values = collect(repository)

        repository.import(listOf("content://bad", "content://good"))
        advanceUntilIdle()

        val states = values.last().items.map { it.displayName to it.state }
        assertThat(states).containsExactly(
            "falso.pdf" to QueueItemState.ImportFailed(IngestError.DAMAGED),
            "bueno.txt" to QueueItemState.Ready,
        ).inOrder()
    }

    @Test
    fun retryingAnAttemptImportsItAgainOrReportsLostAccess() = runTest {
        source.files["content://1"] = FakeFile(SourceInfo("a.txt", 5, "text/plain"), "texto".toByteArray(), readError = java.io.IOException("I/O"))
        val repository = repository()
        val values = collect(repository)
        repository.import(listOf("content://1"))
        advanceUntilIdle()
        val key = values.last().items.single().key
        assertThat(values.last().items.single().canRetry).isTrue()

        source.add("content://1", "a.txt", "texto".toByteArray(), "text/plain")
        repository.retry(key)
        advanceUntilIdle()
        assertThat(values.last().items.single().state).isEqualTo(QueueItemState.Ready)

        source.files["content://2"] = FakeFile(SourceInfo("b.txt", 5, "text/plain"), "otro".toByteArray(), readError = java.io.IOException("I/O"))
        repository.import(listOf("content://2"))
        advanceUntilIdle()
        val failed = values.last().items.first { it.displayName == "b.txt" }
        source.files.remove("content://2")
        repository.retry(failed.key)
        advanceUntilIdle()
        assertThat(values.last().items.first { it.displayName == "b.txt" }.state)
            .isEqualTo(QueueItemState.ImportFailed(IngestError.ACCESS_LOST))
    }

    @Test
    fun aNonRetryableErrorIsNotRetried() = runTest {
        source.add("content://1", "hoja.xlsx", byteArrayOf(1), "application/vnd.ms-excel")
        val repository = repository()
        val values = collect(repository)
        repository.import(listOf("content://1"))
        advanceUntilIdle()
        val item = values.last().items.single()

        repository.retry(item.key)
        advanceUntilIdle()

        assertThat(item.canRetry).isFalse()
        assertThat(source.opened).isEmpty()
        assertThat(values.last().items.single().state).isEqualTo(QueueItemState.ImportFailed(IngestError.UNSUPPORTED_FORMAT))
    }

    @Test
    fun retryingAFailedDocumentPutsItBackAsPending() = runTest {
        source.add("content://1", "a.txt", "texto".toByteArray(), "text/plain")
        val repository = repository()
        val values = collect(repository)
        repository.import(listOf("content://1"))
        advanceUntilIdle()
        index.updateStatus(1, com.lisdatasolutions.microrag.domain.rag.DocumentStatus.ERROR, error = IngestError.EXTRACTION_FAILED.code)
        advanceUntilIdle()
        assertThat(values.last().items.single().state).isEqualTo(QueueItemState.Failed(IngestError.EXTRACTION_FAILED))

        repository.retry(QueueKey.Document(1))
        advanceUntilIdle()

        assertThat(index.docs.getValue(1).status).isEqualTo(com.lisdatasolutions.microrag.domain.rag.DocumentStatus.PENDING)
        assertThat(index.docs.getValue(1).error).isNull()
    }

    @Test
    fun removingADocumentDeletesItsRecordAndCopy() = runTest {
        source.add("content://1", "a.txt", "texto".toByteArray(), "text/plain")
        val repository = repository()
        val values = collect(repository)
        repository.import(listOf("content://1"))
        advanceUntilIdle()
        val copy = File(index.docs.getValue(1).localPath!!)
        assertThat(copy.exists()).isTrue()

        repository.remove(QueueKey.Document(1))
        advanceUntilIdle()

        assertThat(index.docs).isEmpty()
        assertThat(copy.exists()).isFalse()
        assertThat(values.last().items).isEmpty()
    }

    @Test
    fun removingAFailedAttemptHidesIt() = runTest {
        source.add("content://1", "hoja.xlsx", byteArrayOf(1), "application/vnd.ms-excel")
        val repository = repository()
        val values = collect(repository)
        repository.import(listOf("content://1"))
        advanceUntilIdle()

        repository.remove(values.last().items.single().key)
        advanceUntilIdle()

        assertThat(values.last().items).isEmpty()
        assertThat(values.last().items.none { it.removalNeedsConfirmation }).isTrue()
    }

    // F06 US3 (T046), changed by F07: starting adds every non-indexed document to the run (contracts/indexing-api.md).

    @Test
    fun startProcessingAddsEveryUnindexedDocumentToTheRun() = runTest {
        listOf("a", "b", "c").forEach { source.add("content://$it", "$it.txt", it.toByteArray(), "text/plain") }
        val repository = repository()
        val values = collect(repository)
        repository.import(listOf("content://a", "content://b", "content://c"))
        advanceUntilIdle()
        index.updateStatus(2, com.lisdatasolutions.microrag.domain.rag.DocumentStatus.PARSED, chunkCount = 1)
        scheduler.activeDocumentIds.value = setOf(3L)

        val inRun = repository.startProcessing()
        advanceUntilIdle()

        assertThat(inRun).isEqualTo(3)
        assertThat(runStore.run.value.documentIds).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(scheduler.enqueued).containsExactly(listOf(1L, 2L, 3L))
        val states = values.last().items.associate { it.key to it.state }
        assertThat(states[QueueKey.Document(1)]).isEqualTo(QueueItemState.Queued)
        assertThat(values.last().readyCount).isEqualTo(0)
    }

    @Test
    fun withNothingReadyNothingIsEnqueued() = runTest {
        val repository = repository()

        assertThat(repository.startProcessing()).isEqualTo(0)
        assertThat(scheduler.enqueued).isEmpty()
    }

    @Test
    fun retryingAFailedDocumentEnqueuesOnlyIt() = runTest {
        source.add("content://1", "a.txt", "texto".toByteArray(), "text/plain")
        val repository = repository()
        collect(repository)
        repository.import(listOf("content://1"))
        advanceUntilIdle()
        index.updateStatus(1, com.lisdatasolutions.microrag.domain.rag.DocumentStatus.ERROR, error = IngestError.EXTRACTION_FAILED.code)

        repository.retry(QueueKey.Document(1))

        assertThat(scheduler.enqueued).containsExactly(listOf(1L))
    }

    /** Regression (found by IngestStrictModeTest): the first cleanup must not take a copy being registered for an orphan. */
    @Test
    fun orphanCleanupNeverDeletesACopyBeingImported() = kotlinx.coroutines.runBlocking {
        val gate = java.util.concurrent.CountDownLatch(1)
        source.files["content://slow"] = FakeFile(SourceInfo("lento.txt", 5, "text/plain"), "texto".toByteArray(), gate = gate)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val importer = DocumentImporter(source, files, pdf, index, { Long.MAX_VALUE / 2 }, logger)
        val indexing = DefaultIndexingRepository(index, scheduler, runStore, FakeThermalStatusSource(), kotlinx.coroutines.Dispatchers.IO, logger)
        val repository = DefaultImportQueueRepository(
            importer, source, index, files, scheduler, pdf, indexing, runStore, scope, kotlinx.coroutines.Dispatchers.IO, logger,
        ) { 0L }
        try {
            repository.import(listOf("content://slow"))
            kotlinx.coroutines.withTimeout(5_000) { while ("content://slow" !in source.opened) kotlinx.coroutines.delay(10) }
            val queue = scope.async { repository.queue.first { it.items.any { item -> item.key is QueueKey.Document } } }
            kotlinx.coroutines.delay(200) // the cleanup is now waiting for the copy in progress
            gate.countDown()

            kotlinx.coroutines.withTimeout(5_000) { queue.await() }
            val copy = File(index.docs.values.single().localPath!!)
            assertThat(copy.exists()).isTrue()
        } finally {
            scope.cancel()
        }
    }
}

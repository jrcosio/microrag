package com.lisdatasolutions.microrag.data.indexing

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.device.ThermalLevel
import com.lisdatasolutions.microrag.data.ingest.IngestWork
import com.lisdatasolutions.microrag.domain.indexing.CurrentDocument
import com.lisdatasolutions.microrag.domain.indexing.FailedDocument
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.FakeIngestScheduler
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F07 T028/T029 (research R8, data-model §3 and §7): the Index screen state and «Iniciar indexación». */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultIndexingRepositoryTest {

    private val index = FakeRagIndex()
    private val scheduler = FakeIngestScheduler()
    private val runStore = InMemoryIngestRunStore()
    private val thermal = FakeThermalStatusSource()
    private val logger = RecordingLogger()

    private fun TestScope.repository() =
        DefaultIndexingRepository(index, scheduler, runStore, thermal, StandardTestDispatcher(testScheduler), logger)

    private suspend fun doc(name: String, status: DocumentStatus = DocumentStatus.PENDING, chunks: Int? = null, error: IngestError? = null): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        if (status != DocumentStatus.PENDING || chunks != null) index.updateStatus(id, status, error = error?.code, chunkCount = chunks)
        return id
    }

    private suspend fun store(id: Long, count: Int) {
        index.insertChunks(id, (0 until count).map { ChunkInput(it, null, null, "c$it", 1, FloatArray(256)) })
    }

    private suspend fun TestScope.state(repository: DefaultIndexingRepository = repository()): IndexingState =
        repository.state.first { it != IndexingState.Loading }

    @Test
    fun withoutARun_itIsIdleAndListsTheFailedDocuments() = runTest {
        val failed = doc("Roto.pdf", DocumentStatus.ERROR, error = IngestError.EMBEDDING_FAILED)
        doc("Hecho.txt", DocumentStatus.DONE, chunks = 3)

        assertThat(state()).isEqualTo(IndexingState.Idle(listOf(FailedDocument(failed, "Roto.pdf", IngestError.EMBEDDING_FAILED))))
    }

    @Test
    fun aPendingDocument_leavesTheTotalUnknown() = runTest {
        val parsed = doc("A.txt", DocumentStatus.PARSED, chunks = 10)
        val pending = doc("B.txt")
        runStore.edit { it.adding(listOf(parsed, pending)) }

        val state = state() as IndexingState.Active

        assertThat(state.progress).isEqualTo(RunProgress(done = 0, total = null, remainingSeconds = null))
        assertThat(state.queuedDocuments).isEqualTo(2)
    }

    @Test
    fun progressIsWhatIsStoredOverTheChunking_withoutFailedDocuments() = runTest {
        val done = doc("A.txt", DocumentStatus.DONE, chunks = 100).also { store(it, 100) }
        val embedding = doc("B.txt", DocumentStatus.EMBEDDING, chunks = 200).also { store(it, 64) }
        val parsed = doc("C.txt", DocumentStatus.PARSED, chunks = 50)
        val failed = doc("D.txt", DocumentStatus.ERROR, chunks = 30, error = IngestError.INCOMPLETE).also { store(it, 10) }
        runStore.edit { it.adding(listOf(done, embedding, parsed, failed)) }

        val state = state() as IndexingState.Active

        assertThat(state.progress.done).isEqualTo(164)
        assertThat(state.progress.total).isEqualTo(350)
        assertThat(state.failed.map { it.id }).containsExactly(failed)
    }

    @Test
    fun theCurrentDocumentAndItsStage_comeFromTheRunningWork() = runTest {
        val first = doc("Manual técnico.pdf", DocumentStatus.EMBEDDING, chunks = 430).also { store(it, 128) }
        val second = doc("Notas.txt", DocumentStatus.PARSED, chunks = 2)
        runStore.edit { it.adding(listOf(first, second)) }
        scheduler.work.value = IngestWork(unfinished = true, running = true, waitingForSystem = false)
        scheduler.progress.value = IngestProgress(
            first, IngestProgress.Stage.EMBEDDING, chunksDone = 130, chunksTotal = 430, chunksPerSecond = 3.0, backend = ComputeBackend.GPU,
        )

        val state = state() as IndexingState.Active

        assertThat(state.current).isEqualTo(CurrentDocument(first, "Manual técnico.pdf", CurrentDocument.Stage.EMBEDDING))
        assertThat(state.queuedDocuments).isEqualTo(1)
        assertThat(state.pause).isNull()
        // (432 - 128) chunks at 3 per second.
        assertThat(state.progress.remainingSeconds).isEqualTo(102)
    }

    @Test
    fun withoutASteadyRate_noRemainingTimeIsShown() = runTest {
        val id = doc("A.txt", DocumentStatus.EMBEDDING, chunks = 100)
        runStore.edit { it.adding(listOf(id)) }
        scheduler.work.value = IngestWork(unfinished = true, running = true, waitingForSystem = false)
        scheduler.progress.value = IngestProgress(id, IngestProgress.Stage.EMBEDDING, chunksDone = 10, chunksTotal = 100)

        assertThat((state() as IndexingState.Active).progress.remainingSeconds).isNull()
    }

    @Test
    fun pauseReasons_followDataModelSection7() = runTest {
        val id = doc("A.txt", DocumentStatus.EMBEDDING, chunks = 100)
        runStore.edit { it.adding(listOf(id)) }
        val repository = repository()

        scheduler.work.value = IngestWork(unfinished = true, running = false, waitingForSystem = true)
        assertThat((state(repository) as IndexingState.Active).pause).isEqualTo(PauseReason.SYSTEM_LIMIT)

        thermal.level.value = ThermalLevel.SEVERE_OR_WORSE
        assertThat((state(repository) as IndexingState.Active).pause).isEqualTo(PauseReason.THERMAL)

        runStore.edit { it.pausedBy(RunPauseCause.NO_SPACE) }
        assertThat((state(repository) as IndexingState.Active).pause).isEqualTo(PauseReason.NO_SPACE)
    }

    @Test
    fun aRunWithEverythingFinished_isIdle() = runTest {
        val done = doc("A.txt", DocumentStatus.DONE, chunks = 1)
        runStore.edit { it.adding(listOf(done)) }

        assertThat(state()).isEqualTo(IndexingState.Idle(emptyList()))
    }

    @Test
    fun anUnavailableIndex_isReported() = runTest {
        index.setCompatibility(IndexCompatibility.Incompatible("schema_version", "2", "9"))

        assertThat(state()).isEqualTo(IndexingState.IndexUnavailable)
    }

    // T029: «Iniciar indexación».

    @Test
    fun start_dropsFinishedDocumentsAddsTheUnindexedOnesResumesAndEnqueues() = runTest {
        val done = doc("A.txt", DocumentStatus.DONE, chunks = 1)
        val pending = doc("B.txt")
        val parsed = doc("C.txt", DocumentStatus.PARSED, chunks = 4)
        val embedding = doc("D.txt", DocumentStatus.EMBEDDING, chunks = 9)
        doc("E.txt", DocumentStatus.ERROR, error = IngestError.NO_TEXT)
        runStore.edit { it.adding(listOf(done, embedding)).pausedBy(RunPauseCause.USER) }

        val inRun = repository().start()

        assertThat(inRun).isEqualTo(3)
        assertThat(runStore.run.value).isEqualTo(IngestRun(listOf(embedding, pending, parsed)))
        assertThat(scheduler.enqueued).containsExactly(listOf(pending, parsed, embedding))
    }

    @Test
    fun start_appendsToRunningWorkOnlyWhatItLacks() = runTest {
        val first = doc("A.txt", DocumentStatus.EMBEDDING, chunks = 9)
        val added = doc("B.txt")
        scheduler.enqueue(listOf(first))
        scheduler.enqueued.clear()

        repository().start()

        assertThat(scheduler.enqueued).containsExactly(listOf(added))
    }

    @Test
    fun start_withNothingToIndex_enqueuesNothing() = runTest {
        doc("A.txt", DocumentStatus.DONE, chunks = 1)

        assertThat(repository().start()).isEqualTo(0)
        assertThat(scheduler.enqueued).isEmpty()
    }

    // T039: reconciliation when the app becomes visible (research R12, rule N14).

    @Test
    fun reconcile_enqueuesARunLeftWithoutWork() = runTest {
        val id = doc("A.txt", DocumentStatus.EMBEDDING, chunks = 9)
        runStore.edit { it.adding(listOf(id)) }

        repository().reconcile()

        assertThat(scheduler.enqueued).containsExactly(listOf(id))
    }

    @Test
    fun reconcile_replacesWorkWaitingForTheSystem() = runTest {
        val id = doc("A.txt", DocumentStatus.EMBEDDING, chunks = 9)
        runStore.edit { it.adding(listOf(id)) }
        scheduler.work.value = IngestWork(unfinished = true, running = false, waitingForSystem = true)

        repository().reconcile()

        assertThat(scheduler.replaced).containsExactly(listOf(id))
        assertThat(scheduler.enqueued).isEmpty()
    }

    @Test
    fun reconcile_leavesRunningWorkPausedRunsAndAnUnavailableIndexAlone() = runTest {
        val id = doc("A.txt", DocumentStatus.EMBEDDING, chunks = 9)
        runStore.edit { it.adding(listOf(id)) }
        val repository = repository()

        scheduler.work.value = IngestWork(unfinished = true, running = true, waitingForSystem = false)
        repository.reconcile()

        scheduler.work.value = IngestWork.NONE
        runStore.edit { it.pausedBy(RunPauseCause.USER) }
        repository.reconcile()

        runStore.edit { it.resumed() }
        index.setCompatibility(IndexCompatibility.Unreadable("broken"))
        repository.reconcile()

        assertThat(scheduler.enqueued).isEmpty()
        assertThat(scheduler.replaced).isEmpty()
    }

    @Test
    fun heatOverridesAWaitForTheSystem() = runTest {
        val id = doc("A.txt", DocumentStatus.EMBEDDING, chunks = 9)
        runStore.edit { it.adding(listOf(id)) }
        scheduler.work.value = IngestWork(unfinished = true, running = false, waitingForSystem = true)
        thermal.level.value = ThermalLevel.SEVERE_OR_WORSE

        assertThat((state() as IndexingState.Active).pause).isEqualTo(PauseReason.THERMAL)
    }
}

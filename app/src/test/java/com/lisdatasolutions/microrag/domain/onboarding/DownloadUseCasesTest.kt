package com.lisdatasolutions.microrag.domain.onboarding

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.LocalModelWork
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.NetworkKind
import com.lisdatasolutions.microrag.domain.model.SetupProblem
import com.lisdatasolutions.microrag.testing.FakeDeviceResources
import com.lisdatasolutions.microrag.testing.FakeModelDownloadController
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Confirmation, start, pause, resume, observation and reconciliation (T029, T056). */
class DownloadUseCasesTest {

    private val eg = ModelCatalog.byId(ModelId.EG2_270M)
    private val gemma = ModelCatalog.byId(ModelId.GEMMA4_E2B)
    private val repository = InMemoryModelFileRepository()
    private val controller = FakeModelDownloadController(mutableMapOf(ModelId.GEMMA4_E2B to 1_000L))
    private val resources = FakeDeviceResources()
    private val store = FakeOnboardingStore()
    private val tracker = LocalModelWorkTracker()

    @Test
    fun confirmation_listsPendingModelsSpaceNetworkAndMemory() {
        resources.free = 10_000_000_000L
        resources.network = NetworkKind.MOBILE
        resources.ram = 7_499_999_999L

        val details = BuildDownloadConfirmationUseCase(repository, controller, resources)()

        assertThat(details.models).containsExactly(ModelId.EG2_270M to eg.sizeBytes, ModelId.GEMMA4_E2B to gemma.sizeBytes).inOrder()
        assertThat(details.totalBytes).isEqualTo(eg.sizeBytes + gemma.sizeBytes - 1_000L)
        assertThat(details.freeAfterBytes).isEqualTo(10_000_000_000L - details.totalBytes)
        assertThat(details.network).isEqualTo(NetworkKind.MOBILE)
        assertThat(details.lowRam).isTrue()
        assertThat(details.missingBytes).isNull()

        resources.ram = 7_500_000_000L
        resources.free = details.totalBytes
        val tight = BuildDownloadConfirmationUseCase(repository, controller, resources)()
        assertThat(tight.lowRam).isFalse()
        assertThat(tight.missingBytes).isEqualTo(ModelSetupPolicy.SPACE_MARGIN_BYTES)
    }

    @Test
    fun start_withoutSpace_schedulesNothing() = runTest {
        resources.free = 1_000L

        val result = StartModelDownloadUseCase(repository, controller, resources, store, tracker)()

        assertThat(result).isInstanceOf(StartDownloadResult.NoSpace::class.java)
        assertThat(controller.scheduled).isEmpty()
        assertThat(store.downloadRequested.value).isEmpty()
    }

    @Test
    fun start_requestsThePendingModelsAndClearsRejectedImports() = runTest {
        tracker.set(ModelId.EG2_270M, LocalModelWork.Rejected(SetupProblem.WrongFile))

        val result = StartModelDownloadUseCase(repository, controller, resources, store, tracker)()

        assertThat(result).isEqualTo(StartDownloadResult.Started)
        assertThat(store.downloadRequested.value).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B)
        assertThat(store.downloadConfirmed.value).isTrue()
        assertThat(controller.scheduled).containsExactly(eg.sizeBytes + gemma.sizeBytes - 1_000L)
        assertThat(tracker.work.value).isEmpty()
    }

    @Test
    fun pauseAndResume() = runTest {
        PauseModelDownloadUseCase(store, controller)()
        assertThat(store.downloadPaused.value).isTrue()
        assertThat(controller.cancels).isEqualTo(1)

        ResumeModelDownloadUseCase(repository, store, controller)()
        assertThat(store.downloadPaused.value).isFalse()
        assertThat(controller.scheduled).hasSize(1)
    }

    @Test
    fun observe_combinesFilesProgressIntentAndNetwork() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))
        controller.progress.value = DownloadProgress(active = ModelId.EG2_270M, bytesDone = 7, totalBytes = eg.sizeBytes)
        val observe = ObserveModelSetupUseCase(repository, controller, store, resources, tracker)

        val rows = observe().first()
        assertThat(rows[ModelId.EG2_270M]).isEqualTo(ModelSetupStatus.Downloading(7, eg.sizeBytes))
        assertThat(rows[ModelId.GEMMA4_E2B]).isEqualTo(ModelSetupStatus.Queued(ModelId.EG2_270M, 1_000L, gemma.sizeBytes))

        resources.hasInternet.value = false
        assertThat(observe().first()[ModelId.GEMMA4_E2B]).isEqualTo(ModelSetupStatus.WaitingForNetwork(1_000L, gemma.sizeBytes))
    }

    private fun reconcile() = ReconcileDownloadsUseCase(repository, controller, store, resources, RecordingLogger())

    @Test
    fun reconcile_reschedulesARequestThatLostItsJob() = runTest {
        store.requestDownload(setOf(ModelId.GEMMA4_E2B))

        reconcile()()

        assertThat(repository.refreshes).hasSize(1)
        assertThat(controller.orphanCleanups).isEqualTo(1)
        assertThat(controller.scheduled).containsExactly(gemma.sizeBytes - 1_000L)
    }

    @Test
    fun reconcile_leavesPausedRunningFailedAndStillFullQueuesAlone() = runTest {
        store.requestDownload(setOf(ModelId.GEMMA4_E2B))
        store.setPaused(true)
        reconcile()()
        assertThat(controller.scheduled).isEmpty()

        store.setPaused(false)
        controller.running = true
        reconcile()()
        assertThat(controller.scheduled).isEmpty()

        controller.running = false
        controller.progress.value = DownloadProgress(outcomes = mapOf(ModelId.GEMMA4_E2B to DownloadOutcome.Failed(ModelId.GEMMA4_E2B, com.lisdatasolutions.microrag.domain.model.DownloadError.Http(500))))
        reconcile()()
        assertThat(controller.scheduled).isEmpty()

        controller.progress.value = DownloadProgress(outcomes = mapOf(ModelId.GEMMA4_E2B to DownloadOutcome.NoSpace(ModelId.GEMMA4_E2B, 1)))
        resources.free = 1_000L
        reconcile()()
        assertThat(controller.scheduled).isEmpty()

        // Space was freed: the queue resumes on its own.
        resources.free = Long.MAX_VALUE
        reconcile()()
        assertThat(controller.scheduled).hasSize(1)
    }

    @Test
    fun reconcile_verifiesCompleteFilesWithoutARecord() = runTest {
        repository.unverifiedOnRefresh = setOf(ModelId.GEMMA4_E2B)

        reconcile()()

        assertThat(repository.verifyCalls).containsExactly(ModelId.GEMMA4_E2B)
    }
}

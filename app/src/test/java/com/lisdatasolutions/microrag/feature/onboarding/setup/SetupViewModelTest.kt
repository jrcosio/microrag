package com.lisdatasolutions.microrag.feature.onboarding.setup

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ContinueBlocker
import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.model.StorageStatus
import com.lisdatasolutions.microrag.domain.onboarding.BuildDownloadConfirmationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.CompleteOnboardingUseCase
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.ImportModelFileUseCase
import com.lisdatasolutions.microrag.domain.onboarding.LocalModelWorkTracker
import com.lisdatasolutions.microrag.domain.onboarding.ModelFileSource
import com.lisdatasolutions.microrag.domain.onboarding.ObserveModelSetupUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PauseModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PrepareModelsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PrepareStorageUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ReconcileDownloadsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RetryModelUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartModelDownloadUseCase
import com.lisdatasolutions.microrag.rag.runtime.CompatibilityResult
import com.lisdatasolutions.microrag.testing.FakeDeviceResources
import com.lisdatasolutions.microrag.testing.FakeModelDownloadController
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** P02 behavior with real use cases over fakes (T032, T050, T061, T064). */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = FakeOnboardingStore(OnboardingStage.STARTED)
    private val resources = FakeDeviceResources()
    private val controller = FakeModelDownloadController()
    private val tracker = LocalModelWorkTracker()
    private val tested = mutableListOf<ModelId>()

    /**
     * Stands in for the process scope. Not `backgroundScope`: since coroutines-test 1.7,
     * `advanceUntilIdle()` does not run background work on its own.
     */
    private val appJob = SupervisorJob()
    private val appScope = CoroutineScope(dispatcher + appJob)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        appJob.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: InMemoryModelFileRepository = InMemoryModelFileRepository()): SetupViewModel {
        val applicationScope = ApplicationScope(appScope)
        val prepare = PrepareModelsUseCase(
            repository,
            { file, _ -> tested += file.id; CompatibilityResult.Compatible(if (file.id == ModelId.GEMMA4_E2B) ComputeBackend.GPU else ComputeBackend.CPU) },
            tracker,
            applicationScope,
        )
        val source = object : ModelFileSource {
            override fun sizeOf(uri: String) = ModelCatalog.byId(ModelId.EG2_270M).sizeBytes
            override fun open(uri: String) = ByteArrayInputStream(ByteArray(16))
        }
        return SetupViewModel(
            observeSetup = ObserveModelSetupUseCase(repository, controller, store, resources, tracker),
            buildConfirmation = BuildDownloadConfirmationUseCase(repository, controller, resources),
            startDownload = StartModelDownloadUseCase(repository, controller, resources, store, tracker),
            pauseDownload = PauseModelDownloadUseCase(store, controller),
            resumeDownload = ResumeModelDownloadUseCase(repository, store, controller),
            retryModel = RetryModelUseCase(repository, controller, store, tracker),
            reconcile = ReconcileDownloadsUseCase(repository, controller, store, resources, RecordingLogger()),
            prepareModels = prepare,
            importModel = ImportModelFileUseCase(repository, source, tracker, prepare, applicationScope),
            prepareStorage = PrepareStorageUseCase(repository, controller, resources),
            completeOnboarding = CompleteOnboardingUseCase(repository, store),
            backgroundDispatcher = dispatcher,
        )
    }

    private fun events(vm: SetupViewModel): MutableList<SetupEvent> =
        mutableListOf<SetupEvent>().also { list -> appScope.launch { vm.setupEvents.collect { list += it } } }

    @Test
    fun firstStep_rowsInPngOrderAndContinueExplained() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.step).isEqualTo(1)
        assertThat(state.rows.map { it.id }).containsExactly(ModelId.GEMMA4_E2B, ModelId.EG2_270M).inOrder()
        assertThat(state.storage).isEqualTo(StorageStatus.Offline)
        assertThat(state.blocker).isEqualTo(ContinueBlocker.MODELS_NOT_CONFIGURED)
        assertThat(state.continueEnabled).isFalse()
    }

    @Test
    fun rowTap_opensTheConfirmation_andDownloadAsksForNotificationsFirst() = runTest(dispatcher) {
        val vm = viewModel()
        val events = events(vm)
        advanceUntilIdle()

        vm.onRowClick(ModelId.GEMMA4_E2B)
        advanceUntilIdle()
        assertThat(vm.state.value.confirmation?.target).isEqualTo(ModelId.GEMMA4_E2B)

        vm.onConfirmDownload(needsNotificationPermission = true)
        advanceUntilIdle()
        assertThat(vm.state.value.confirmation).isNull()
        assertThat(events).containsExactly(SetupEvent.RequestNotificationPermission)
        assertThat(controller.scheduled).isEmpty()

        // Granted or denied, the download starts (FR-010).
        vm.onNotificationPermissionResult()
        advanceUntilIdle()
        assertThat(controller.scheduled).hasSize(1)
        assertThat(store.downloadRequested.value).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B)
        assertThat(vm.state.value.step).isEqualTo(2)
    }

    @Test
    fun downloadingRow_pausesAndResumes_andLeavingTheScreenCancelsNothing() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onConfirmDownload(needsNotificationPermission = false)
        advanceUntilIdle()
        controller.progress.value = DownloadProgress(active = ModelId.EG2_270M, bytesDone = 1, totalBytes = 10)
        advanceUntilIdle()

        vm.onRowClick(ModelId.EG2_270M)
        advanceUntilIdle()
        assertThat(store.downloadPaused.value).isTrue()
        assertThat(controller.cancels).isEqualTo(1)
        assertThat(vm.state.value.rows.last().status).isInstanceOf(ModelSetupStatus.Paused::class.java)

        vm.onRowClick(ModelId.EG2_270M)
        advanceUntilIdle()
        assertThat(store.downloadPaused.value).isFalse()
        assertThat(controller.scheduled).hasSize(2)

        // FR-003: Back from setup clears the ViewModel; the download keeps going.
        ViewModelStore().apply { put("setup", vm) }.clear()
        assertThat(controller.cancels).isEqualTo(1)
    }

    @Test
    fun failedRow_retries_andNoSpaceRowOpensStorageSettings() = runTest(dispatcher) {
        val vm = viewModel()
        val events = events(vm)
        store.requestDownload(setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))
        controller.progress.value = DownloadProgress(
            outcomes = mapOf(
                ModelId.EG2_270M to DownloadOutcome.Failed(ModelId.EG2_270M, DownloadError.Http(503)),
                ModelId.GEMMA4_E2B to DownloadOutcome.NoSpace(ModelId.GEMMA4_E2B, 42),
            ),
        )
        advanceUntilIdle()

        vm.onRowClick(ModelId.GEMMA4_E2B)
        vm.onRowClick(ModelId.EG2_270M)
        advanceUntilIdle()

        assertThat(events).containsExactly(SetupEvent.OpenStorageSettings)
        assertThat(controller.scheduled).hasSize(1)
    }

    @Test
    fun verifiedFiles_areTestedThenReady_andContinueGoesHome() = runTest(dispatcher) {
        val repository = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))
        val vm = viewModel(repository)
        val events = events(vm)
        advanceUntilIdle()

        assertThat(tested).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
        val state = vm.state.value
        assertThat(state.step).isEqualTo(3)
        assertThat(state.rows.map { it.status }).containsExactly(
            ModelSetupStatus.Ready(ComputeBackend.GPU, ModelCatalog.byId(ModelId.GEMMA4_E2B).sizeBytes),
            ModelSetupStatus.Ready(ComputeBackend.CPU, ModelCatalog.byId(ModelId.EG2_270M).sizeBytes),
        ).inOrder()
        assertThat(state.continueEnabled).isTrue()

        vm.onContinue()
        advanceUntilIdle()
        assertThat(store.stage.value).isEqualTo(OnboardingStage.COMPLETED)
        assertThat(events).containsExactly(SetupEvent.NavigateHome)
    }

    @Test
    fun continueWithoutBothModels_doesNothing() = runTest(dispatcher) {
        val vm = viewModel(InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M)))
        val events = events(vm)
        advanceUntilIdle()

        vm.onContinue()
        advanceUntilIdle()

        assertThat(events).isEmpty()
        assertThat(store.stage.value).isEqualTo(OnboardingStage.STARTED)
    }

    @Test
    fun pickFile_importsTheModelOfTheRowThatOpenedTheDialog() = runTest(dispatcher) {
        val repository = InMemoryModelFileRepository()
        val vm = viewModel(repository)
        val events = events(vm)
        advanceUntilIdle()

        vm.onRowClick(ModelId.EG2_270M)
        advanceUntilIdle()
        vm.onPickFile()
        advanceUntilIdle()
        assertThat(events).containsExactly(SetupEvent.PickFile(ModelId.EG2_270M))

        vm.onFilePicked(ModelId.EG2_270M, "content://picked")
        advanceUntilIdle()
        assertThat(repository.streamed.map { it.first }).containsExactly(ModelId.EG2_270M)
        assertThat(tested).contains(ModelId.EG2_270M)
    }

    @Test
    fun resumeVisible_reconcilesALostJob() = runTest(dispatcher) {
        store.requestDownload(setOf(ModelId.GEMMA4_E2B))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onResumeVisible()
        advanceUntilIdle()

        assertThat(controller.scheduled).hasSize(1)
    }
}

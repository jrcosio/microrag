package com.lisdatasolutions.microrag.data.multimodal

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.IngestRun
import com.lisdatasolutions.microrag.data.indexing.IngestRunStore
import com.lisdatasolutions.microrag.data.indexing.RunPauseCause
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.DefaultModelRequirements
import com.lisdatasolutions.microrag.domain.multimodal.EnableMultimodalResult
import com.lisdatasolutions.microrag.domain.multimodal.IdentityCheck
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.multimodal.ValidateIdentityUseCase
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.LocalModelWorkTracker
import com.lisdatasolutions.microrag.domain.onboarding.PrepareModelsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartModelDownloadUseCase
import com.lisdatasolutions.microrag.rag.runtime.CompatibilityResult
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.testing.FakeDeviceResources
import com.lisdatasolutions.microrag.testing.FakeIngestScheduler
import com.lisdatasolutions.microrag.testing.FakeModelDownloadController
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F19 T019 (contract M2–M4, research R1 and R4): the activation, its failures and its restarts. */
class DefaultMultimodalControllerTest {

    private val logger = RecordingLogger()
    private val store = FakeMultimodalStore()
    private val index = FakeRagIndex()
    private val models = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B)).apply {
        preparedBackends.value = mapOf(ModelId.EG2_270M to ComputeBackend.GPU, ModelId.GEMMA4_E2B to ComputeBackend.GPU)
    }
    private val downloads = FakeModelDownloadController()
    private val onboarding = FakeOnboardingStore()
    private val resources = FakeDeviceResources()
    private val tracker = LocalModelWorkTracker()
    private val requirements = DefaultModelRequirements(index, store)
    private val runStore = InMemoryIngestRunStore()
    private val scheduler = FakeIngestScheduler()
    private val indexing = RecordingIndexing(runStore)
    private var cosine = 1.0
    private var compatibility: CompatibilityResult = CompatibilityResult.Compatible(ComputeBackend.CPU)

    /** Resumes the run like the real repository, and records every call. */
    private class RecordingIndexing(private val runStore: IngestRunStore) : IndexingRepository {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow<IndexingState>(IndexingState.Idle(emptyList()))
        override suspend fun start(): Int = 0.also { calls += "start" }
        override suspend fun pause() { calls += "pause" }
        override suspend fun resume() {
            calls += "resume"
            runStore.edit { it.resumed() }
        }
        override suspend fun retry(documentId: Long) = Unit
        override suspend fun add(documentId: Long) = Unit
        override suspend fun reconcile() { calls += "reconcile" }
    }

    private fun TestScope.controller(): DefaultMultimodalController {
        // advanceUntilIdle() does not wait for background work: the app's loop runs eagerly, and ends with the test.
        val scope = ApplicationScope(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        return DefaultMultimodalController(
            store = store,
            index = index,
            models = models,
            downloads = downloads,
            onboarding = onboarding,
            startDownload = StartModelDownloadUseCase(models, downloads, resources, onboarding, tracker, requirements),
            prepare = PrepareModelsUseCase(models, { _: ModelFile, _: File -> compatibility }, tracker, scope, requirements),
            tracker = tracker,
            requirements = requirements,
            validateIdentity = ValidateIdentityUseCase { IdentityCheck(20, cosine, cosine >= IdentityValidator.MIN_COSINE) },
            coordinator = EngineCoordinator(logger),
            runStore = runStore,
            scheduler = scheduler,
            indexing = indexing,
            applicationScope = scope,
            logger = logger,
        ).also { it.start() }
    }

    /** What the F03 runner does once the 440M's download ends well. */
    private fun downloaded() = models.states.update { it + (ModelId.EG2_440M to ModelFileState.Verified(File("/models/440m"))) }

    private suspend fun stage() = store.state.first().stage

    @Test
    fun aFullActivation_switchesTheIndexAndDeletesThe270M() = runTest {
        val controller = controller()

        assertThat(controller.enable()).isEqualTo(EnableMultimodalResult.Started)
        assertThat(stage()).isEqualTo(MultimodalStage.DOWNLOADING)
        assertThat(onboarding.downloadRequested.value).containsExactly(ModelId.EG2_440M)
        assertThat(downloads.scheduled.single()).isEqualTo(ModelCatalog.byId(ModelId.EG2_440M).sizeBytes)
        advanceUntilIdle()
        assertThat(stage()).isEqualTo(MultimodalStage.DOWNLOADING)

        downloaded()
        advanceUntilIdle()

        assertThat(models.preparedBackends.value[ModelId.EG2_440M]).isEqualTo(ComputeBackend.CPU)
        assertThat(index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
        assertThat(store.state.first()).isEqualTo(StoredMultimodal(MultimodalStage.ON, null, textEmbedder = ModelId.EG2_440M))
        assertThat(models.deleted).containsExactly(ModelId.EG2_270M)
        assertThat(models.states.value[ModelId.EG2_270M]).isEqualTo(ModelFileState.Missing)
        assertThat(indexing.calls).contains("reconcile")
        // The stages went one after the other, and the mirror was written before the stage turned ON.
        assertThat(store.writes.map { it.stage }.distinct())
            .containsExactly(MultimodalStage.DOWNLOADING, MultimodalStage.VALIDATING, MultimodalStage.SWITCHING, MultimodalStage.ON).inOrder()
        assertThat(store.writes.first { it.stage == MultimodalStage.ON }.textEmbedder).isEqualTo(ModelId.EG2_440M)
    }

    @Test
    fun aFailedIdentity_deletesThe440M_andKeepsThe270M() = runTest {
        cosine = 0.9998
        val controller = controller()
        controller.enable()
        downloaded()
        advanceUntilIdle()

        assertThat(store.state.first()).isEqualTo(StoredMultimodal(MultimodalStage.OFF, MultimodalError.IDENTITY_MISMATCH))
        assertThat(models.deleted).containsExactly(ModelId.EG2_440M)
        assertThat(models.states.value[ModelId.EG2_270M]).isInstanceOf(ModelFileState.Verified::class.java)
        assertThat(index.textEmbedder.value).isEqualTo(ModelId.EG2_270M)
        assertThat(onboarding.downloadRequested.value).doesNotContain(ModelId.EG2_440M)
        assertThat(downloads.keptPartials).doesNotContain(ModelId.EG2_440M)
    }

    @Test
    fun aModelThisDeviceCannotRun_goesBackToOffWithItsReason() = runTest {
        compatibility = CompatibilityResult.Incompatible
        val controller = controller()
        controller.enable()
        downloaded()
        advanceUntilIdle()

        assertThat(store.state.first()).isEqualTo(StoredMultimodal(MultimodalStage.OFF, MultimodalError.PREPARE_FAILED))
        assertThat(models.deleted).containsExactly(ModelId.EG2_440M)
    }

    @Test
    fun withoutSpace_itStaysDownloadingWithNoSpace_andRetryStartsIt() = runTest {
        resources.free = 100L * 1024 * 1024
        val controller = controller()

        val result = controller.enable()

        assertThat(result).isInstanceOf(EnableMultimodalResult.NoSpace::class.java)
        assertThat(store.state.first()).isEqualTo(StoredMultimodal(MultimodalStage.DOWNLOADING, MultimodalError.NO_SPACE))
        assertThat(downloads.scheduled).isEmpty()

        resources.free = 100_000_000_000L
        assertThat(controller.retry()).isEqualTo(EnableMultimodalResult.Started)
        assertThat(store.state.first().error).isNull()
        assertThat(downloads.scheduled).hasSize(1)
    }

    @Test
    fun aFailedDownload_staysDownloadingWithItsError() = runTest {
        val controller = controller()
        controller.enable()
        downloads.progress.update {
            it.copy(outcomes = mapOf(ModelId.EG2_440M to DownloadOutcome.Failed(ModelId.EG2_440M, DownloadError.HashMismatch)))
        }
        advanceUntilIdle()

        assertThat(store.state.first()).isEqualTo(StoredMultimodal(MultimodalStage.DOWNLOADING, MultimodalError.DOWNLOAD_FAILED))
        assertThat(models.deleted).isEmpty()
    }

    @Test
    fun cancel_deletesThePartialDownload_andGoesBackToOff() = runTest {
        val controller = controller()
        controller.enable()

        controller.cancelDownload()

        assertThat(store.state.first()).isEqualTo(StoredMultimodal())
        assertThat(downloads.cancels).isEqualTo(1)
        assertThat(downloads.keptPartials).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
        assertThat(onboarding.downloadRequested.value).doesNotContain(ModelId.EG2_440M)
        // Disabling while downloading is the same «Cancelar».
        controller.enable()
        controller.disable()
        assertThat(stage()).isEqualTo(MultimodalStage.OFF)
        assertThat(downloads.cancels).isEqualTo(2)
    }

    @Test
    fun aStopInSwitching_beforeTheTransaction_isFinishedAtStart() = runTest {
        store.state.value = StoredMultimodal(MultimodalStage.SWITCHING)
        models.states.update { it + (ModelId.EG2_440M to ModelFileState.Verified(File("/models/440m"))) }

        controller()
        advanceUntilIdle()

        assertThat(index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
        assertThat(store.state.first()).isEqualTo(StoredMultimodal(MultimodalStage.ON, textEmbedder = ModelId.EG2_440M))
        assertThat(models.deleted).containsExactly(ModelId.EG2_270M)
    }

    @Test
    fun aStopInSwitching_afterTheTransaction_onlyDeletesThe270M() = runTest {
        store.state.value = StoredMultimodal(MultimodalStage.SWITCHING)
        index.textEmbedder.value = ModelId.EG2_440M

        controller()
        advanceUntilIdle()

        assertThat(stage()).isEqualTo(MultimodalStage.ON)
        assertThat(store.state.first().textEmbedder).isEqualTo(ModelId.EG2_440M)
        assertThat(models.deleted).containsExactly(ModelId.EG2_270M)
    }

    @Test
    fun turningItOnAgain_isImmediate_whenTheIndexUsesThe440M() = runTest {
        index.textEmbedder.value = ModelId.EG2_440M
        store.state.value = StoredMultimodal(textEmbedder = ModelId.EG2_440M)
        val controller = controller()

        assertThat(controller.enable()).isEqualTo(EnableMultimodalResult.On)

        assertThat(stage()).isEqualTo(MultimodalStage.ON)
        assertThat(downloads.scheduled).isEmpty()
        assertThat(indexing.calls).contains("reconcile")

        // Turning it off keeps everything (D-079).
        controller.disable()
        assertThat(stage()).isEqualTo(MultimodalStage.OFF)
        assertThat(models.deleted).isEmpty()
        assertThat(index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
    }

    @Test
    fun theSwitch_pausesTheIngestWithModelSwitch_andResumesIt() = runTest {
        runStore.edit { IngestRun(documentIds = listOf(7L)) }
        var pausedDuringCancel: RunPauseCause? = null
        scheduler.onCancel = { pausedDuringCancel = runStore.run.value.pauseCause }
        val controller = controller()
        controller.enable()
        downloaded()
        advanceUntilIdle()

        assertThat(pausedDuringCancel).isEqualTo(RunPauseCause.MODEL_SWITCH)
        assertThat(scheduler.cancels).isEqualTo(1)
        assertThat(indexing.calls).contains("resume")
        assertThat(runStore.run.first()).isEqualTo(IngestRun(documentIds = listOf(7L)))
    }

    @Test
    fun aPauseOfTheUser_staysTheirs_throughTheSwitch() = runTest {
        runStore.edit { IngestRun(documentIds = listOf(7L), pauseCause = RunPauseCause.USER) }
        val controller = controller()
        controller.enable()
        downloaded()
        advanceUntilIdle()

        assertThat(stage()).isEqualTo(MultimodalStage.ON)
        assertThat(runStore.run.first().pauseCause).isEqualTo(RunPauseCause.USER)
        assertThat(indexing.calls).doesNotContain("resume")
        assertThat(scheduler.cancels).isEqualTo(0)
    }

    @Test
    fun aDownloadLeftWithoutItsJob_showsRetry() = runTest {
        store.state.value = StoredMultimodal(MultimodalStage.DOWNLOADING)
        downloads.running = false

        controller()
        advanceUntilIdle()

        assertThat(store.state.first().error).isEqualTo(MultimodalError.DOWNLOAD_FAILED)
    }
}

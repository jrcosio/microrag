package com.lisdatasolutions.microrag.domain.onboarding

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.LocalModelWork
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.model.SetupProblem
import com.lisdatasolutions.microrag.domain.model.StorageStatus
import com.lisdatasolutions.microrag.rag.runtime.CompatibilityResult
import com.lisdatasolutions.microrag.rag.runtime.ModelCompatibility
import com.lisdatasolutions.microrag.testing.FakeDeviceResources
import com.lisdatasolutions.microrag.testing.FakeModelDownloadController
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** On-device test, retry, import, storage and completion (T049, T050, T061, T064). */
class LocalWorkUseCasesTest {

    private val tracker = LocalModelWorkTracker()
    private val controller = FakeModelDownloadController()
    private val store = FakeOnboardingStore()

    private class RecordingCompatibility(private val results: Map<ModelId, CompatibilityResult>) : ModelCompatibility {
        val checked = mutableListOf<ModelId>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun check(file: com.lisdatasolutions.microrag.domain.model.ModelFile, path: java.io.File): CompatibilityResult {
            checked += file.id
            gate?.await()
            return results.getValue(file.id)
        }
    }

    private fun CoroutineScope.prepare(repository: InMemoryModelFileRepository, compatibility: ModelCompatibility) =
        PrepareModelsUseCase(repository, compatibility, tracker, ApplicationScope(this))

    @Test
    fun prepare_testsVerifiedModelsInOrderAndRecordsTheBackend() = runTest {
        val repository = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))
        val compatibility = RecordingCompatibility(
            mapOf(ModelId.EG2_270M to CompatibilityResult.Compatible(ComputeBackend.CPU), ModelId.GEMMA4_E2B to CompatibilityResult.Compatible(ComputeBackend.GPU)),
        )

        prepare(repository, compatibility).prepareAll()

        assertThat(compatibility.checked).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
        assertThat(repository.preparedBackends.value).containsExactly(ModelId.EG2_270M, ComputeBackend.CPU, ModelId.GEMMA4_E2B, ComputeBackend.GPU)
        assertThat(tracker.work.value).isEmpty()
    }

    @Test
    fun prepare_incompatibleIsNeverReadyAndIsNotRetestedForever() = runTest {
        val repository = InMemoryModelFileRepository(verified = setOf(ModelId.GEMMA4_E2B))
        val compatibility = RecordingCompatibility(mapOf(ModelId.GEMMA4_E2B to CompatibilityResult.Incompatible))
        val useCase = prepare(repository, compatibility)

        useCase.prepareAll()
        useCase.prepareAll()

        assertThat(repository.preparedBackends.value).isEmpty()
        assertThat(tracker.work.value[ModelId.GEMMA4_E2B]).isEqualTo(LocalModelWork.Rejected(SetupProblem.DeviceIncompatible))
        assertThat(compatibility.checked).containsExactly(ModelId.GEMMA4_E2B)
    }

    @Test
    fun prepare_concurrentCallsRunOneTestAndCancellationRecordsNothing() = runTest {
        val repository = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M))
        val compatibility = RecordingCompatibility(mapOf(ModelId.EG2_270M to CompatibilityResult.Compatible(ComputeBackend.CPU)))
        compatibility.gate = CompletableDeferred()
        val useCase = prepare(repository, compatibility)

        val first = launch { useCase.prepareAll() }
        testScheduler.runCurrent()
        useCase.prepareAll()
        assertThat(tracker.work.value[ModelId.EG2_270M]).isEqualTo(LocalModelWork.Preparing)
        first.cancel()
        testScheduler.advanceUntilIdle()

        assertThat(compatibility.checked).containsExactly(ModelId.EG2_270M)
        assertThat(repository.preparedBackends.value).isEmpty()
        assertThat(tracker.work.value).isEmpty()
    }

    @Test
    fun retry_forgetsTheFailureAndSchedulesAgain() = runTest {
        val repository = InMemoryModelFileRepository(corrupt = setOf(ModelId.EG2_270M))
        repository.adopt(ModelId.EG2_270M, java.io.File("/nope"))
        tracker.set(ModelId.EG2_270M, LocalModelWork.Rejected(SetupProblem.Storage))

        RetryModelUseCase(repository, controller, store, tracker)(ModelId.EG2_270M)

        assertThat(repository.states.value[ModelId.EG2_270M]).isEqualTo(ModelFileState.Missing)
        assertThat(tracker.work.value).isEmpty()
        assertThat(store.downloadRequested.value).contains(ModelId.EG2_270M)
        assertThat(controller.scheduled).hasSize(1)
    }

    private class FakeSource(private val size: Long?, private val bytes: Int) : ModelFileSource {
        var opened = 0

        override fun sizeOf(uri: String) = size

        override fun open(uri: String): InputStream {
            opened++
            return ByteArrayInputStream(ByteArray(bytes))
        }
    }

    @Test
    fun import_ofTheRightFile_isVerifiedAndThenTested() = runTest {
        val eg = ModelCatalog.byId(ModelId.EG2_270M)
        val repository = InMemoryModelFileRepository()
        val compatibility = RecordingCompatibility(mapOf(ModelId.EG2_270M to CompatibilityResult.Compatible(ComputeBackend.CPU)))
        val source = FakeSource(size = eg.sizeBytes, bytes = 4096)
        val scope = ApplicationScope(this)

        ImportModelFileUseCase(repository, source, tracker, prepare(repository, compatibility), scope).import(ModelId.EG2_270M, "content://doc")

        assertThat(repository.streamed).containsExactly(ModelId.EG2_270M to 4096L)
        assertThat(repository.preparedBackends.value).containsExactly(ModelId.EG2_270M, ComputeBackend.CPU)
        assertThat(tracker.work.value).isEmpty()
    }

    @Test
    fun import_ofAnotherFile_isIncompatible() = runTest {
        val repository = InMemoryModelFileRepository(corrupt = setOf(ModelId.EG2_270M))
        val compatibility = RecordingCompatibility(emptyMap())
        val scope = ApplicationScope(this)

        // Wrong size: rejected without copying anything.
        val wrongSize = FakeSource(size = 10, bytes = 10)
        ImportModelFileUseCase(repository, wrongSize, tracker, prepare(repository, compatibility), scope).import(ModelId.EG2_270M, "content://a")
        assertThat(wrongSize.opened).isEqualTo(0)
        assertThat(tracker.work.value[ModelId.EG2_270M]).isEqualTo(LocalModelWork.Rejected(SetupProblem.WrongFile))

        // Right size, wrong content: the hash rejects it.
        val eg = ModelCatalog.byId(ModelId.EG2_270M)
        val wrongHash = FakeSource(size = eg.sizeBytes, bytes = 10)
        ImportModelFileUseCase(repository, wrongHash, tracker, prepare(repository, compatibility), scope).import(ModelId.EG2_270M, "content://b")
        assertThat(tracker.work.value[ModelId.EG2_270M]).isEqualTo(LocalModelWork.Rejected(SetupProblem.WrongFile))
        assertThat(compatibility.checked).isEmpty()
    }

    @Test
    fun storage_isOfflineWithFoldersAndSpace_otherwiseNoSpace() {
        val repository = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))
        val resources = FakeDeviceResources(free = 1_000_000_000L)
        val prepareStorage = PrepareStorageUseCase(repository, controller, resources)

        assertThat(prepareStorage()).isEqualTo(StorageStatus.Offline)

        resources.free = 100L
        assertThat(prepareStorage()).isEqualTo(StorageStatus.NoSpace(ModelSetupPolicy.SPACE_MARGIN_BYTES - 100L))

        resources.storageOk = false
        assertThat(prepareStorage()).isInstanceOf(StorageStatus.NoSpace::class.java)
    }

    @Test
    fun complete_onlyWithBothModelsReady() = runTest {
        val repository = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))
        val complete = CompleteOnboardingUseCase(repository, store)
        repository.markPrepared(ModelId.EG2_270M, ComputeBackend.CPU)

        assertThat(complete()).isFalse()
        assertThat(store.stage.value).isEqualTo(OnboardingStage.NOT_STARTED)

        repository.markPrepared(ModelId.GEMMA4_E2B, ComputeBackend.GPU)
        assertThat(complete()).isTrue()
        assertThat(store.stage.value).isEqualTo(OnboardingStage.COMPLETED)
    }
}

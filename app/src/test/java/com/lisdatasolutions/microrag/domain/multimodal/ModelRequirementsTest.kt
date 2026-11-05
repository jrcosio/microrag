package com.lisdatasolutions.microrag.domain.multimodal

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ReconcileDownloadsUseCase
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.testing.FakeDeviceResources
import com.lisdatasolutions.microrag.testing.FakeModelDownloadController
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F19 T008 (contract M6, research R5, analyze H3): the models required now. */
class ModelRequirementsTest {

    private val index = FakeRagIndex()
    private val store = FakeMultimodalStore()
    private val requirements = DefaultModelRequirements(index, store)

    private fun ids(files: List<ModelFile>) = files.map { it.id }

    @Test
    fun theTextEmbedderOfTheIndex_andGemma_always() = runTest {
        val first = requirements.current()
        assertThat(ids(first.core)).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
        assertThat(ids(first.files)).isEqualTo(ids(first.core))
        assertThat(first.shown).containsExactly(ModelId.GEMMA4_E2B, ModelId.EG2_270M).inOrder()
        assertThat(first).isEqualTo(RequiredModels.FIRST_RUN)
        assertThat(ids(RequiredModels.FIRST_RUN.core)).isEqualTo(ids(ModelCatalog.required))

        index.textEmbedder.value = ModelId.EG2_440M
        val switched = requirements.current()
        assertThat(ids(switched.core)).containsExactly(ModelId.EG2_440M, ModelId.GEMMA4_E2B).inOrder()
        assertThat(switched.shown).containsExactly(ModelId.GEMMA4_E2B, ModelId.EG2_440M).inOrder()
    }

    @Test
    fun the440M_isAddedWhileTheModeIsBeingTurnedOn() = runTest {
        for (stage in listOf(MultimodalStage.DOWNLOADING, MultimodalStage.VALIDATING, MultimodalStage.SWITCHING)) {
            store.state.value = StoredMultimodal(stage = stage)
            val required = requirements.current()
            assertThat(ids(required.files)).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B, ModelId.EG2_440M).inOrder()
            // The Home chip and the rows only follow what answers.
            assertThat(ids(required.core)).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
        }

        // Switched while still SWITCHING: the 440M is listed once.
        index.textEmbedder.value = ModelId.EG2_440M
        assertThat(ids(requirements.current().files)).containsExactly(ModelId.EG2_440M, ModelId.GEMMA4_E2B).inOrder()

        store.state.value = StoredMultimodal(stage = MultimodalStage.OFF)
        index.textEmbedder.value = ModelId.EG2_270M
        assertThat(ids(requirements.current().files)).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
    }

    @Test
    fun beforeTheIndexOpens_theMirrorSays() = runTest {
        val closed = FakeRagIndex(initial = IndexCompatibility.Closed)
        val mirror = FakeMultimodalStore(StoredMultimodal(stage = MultimodalStage.ON, textEmbedder = ModelId.EG2_440M))
        val beforeOpening = DefaultModelRequirements(closed, mirror)

        assertThat(closed.textEmbedder.value).isEqualTo(ModelId.EG2_270M)
        assertThat(beforeOpening.current().textEmbedder).isEqualTo(ModelId.EG2_440M)

        // Opened: the file is what counts.
        closed.textEmbedder.value = ModelId.EG2_440M
        closed.setCompatibility(IndexCompatibility.Compatible)
        assertThat(beforeOpening.current().textEmbedder).isEqualTo(ModelId.EG2_440M)
        closed.textEmbedder.value = ModelId.EG2_270M
        assertThat(beforeOpening.current().textEmbedder).isEqualTo(ModelId.EG2_270M)
    }

    @Test
    fun afterTheSwitch_the270MIsNeitherPendingNorFetchedAgain() = runTest {
        index.textEmbedder.value = ModelId.EG2_440M
        store.state.value = StoredMultimodal(stage = MultimodalStage.ON, textEmbedder = ModelId.EG2_440M)
        val repository = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_440M, ModelId.GEMMA4_E2B)).apply {
            preparedBackends.value = mapOf(ModelId.EG2_440M to ComputeBackend.CPU, ModelId.GEMMA4_E2B to ComputeBackend.GPU)
        }
        assertThat(repository.states.value[ModelId.EG2_270M]).isEqualTo(ModelFileState.Missing)

        assertThat(ObserveLocalAiUseCase(repository, requirements)().first()).isEqualTo(LocalAiStatus.READY)

        // Onboarding asked for the 270M once; the next reconcile does not bring it back.
        val onboarding = FakeOnboardingStore().apply { requestDownload(setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B)) }
        val controller = FakeModelDownloadController()
        ReconcileDownloadsUseCase(repository, controller, onboarding, FakeDeviceResources(), RecordingLogger(), requirements)()

        assertThat(controller.scheduled).isEmpty()
        assertThat(controller.keptPartials).containsExactly(ModelId.EG2_440M, ModelId.GEMMA4_E2B).inOrder()
    }

    @Test
    fun whileDownloading_the440MPartialIsKept_andTheChipStaysReady() = runTest {
        store.state.value = StoredMultimodal(stage = MultimodalStage.DOWNLOADING)
        val repository = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B)).apply {
            preparedBackends.value = mapOf(ModelId.EG2_270M to ComputeBackend.CPU, ModelId.GEMMA4_E2B to ComputeBackend.GPU)
        }
        val controller = FakeModelDownloadController()

        ReconcileDownloadsUseCase(repository, controller, FakeOnboardingStore(), FakeDeviceResources(), RecordingLogger(), requirements)()

        assertThat(controller.keptPartials).contains(ModelId.EG2_440M)
        assertThat(ObserveLocalAiUseCase(repository, requirements)().first()).isEqualTo(LocalAiStatus.READY)
    }
}

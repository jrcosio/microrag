package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.domain.settings.ModelOverview
import com.lisdatasolutions.microrag.domain.settings.ObserveModelOverviewUseCase
import com.lisdatasolutions.microrag.testing.FakeModelSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F12 T025: the model rows of Ajustes (P13–P15, S1, S2). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val setup = FakeModelSetup()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = SettingsViewModel(
        observeModels = ObserveModelOverviewUseCase(setup.useCase, RefreshLocalAiUseCase(setup.repository)),
        refreshLocalAi = RefreshLocalAiUseCase(setup.repository),
        savedState = saved,
    )

    private fun SettingsViewModel.model(id: ModelId) = state.value.models.single { it.id == id }

    @Test
    fun noRowStateBeforeTheFirstRescan() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.models).isEmpty()
        advanceUntilIdle()
        assertThat(setup.repository.refreshes).hasSize(1)
        assertThat(viewModel.state.value.models.map { it.id }).containsExactly(ModelId.GEMMA4_E2B, ModelId.EG2_270M).inOrder()
    }

    @Test
    fun comingBackRescansTheFiles() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onResume()
        advanceUntilIdle()

        assertThat(setup.repository.refreshes).hasSize(2)
    }

    @Test
    fun rowsFollowTheModelState_withoutLeavingTheScreen() = runTest(dispatcher) {
        setup.preparing(ModelId.GEMMA4_E2B)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.model(ModelId.GEMMA4_E2B).chip()).isEqualTo(ModelChip.Setup(ModelSetupStatus.Preparing))

        setup.work.set(ModelId.GEMMA4_E2B, null)
        setup.ready(ModelId.GEMMA4_E2B, ComputeBackend.GPU)
        advanceUntilIdle()

        assertThat(viewModel.model(ModelId.GEMMA4_E2B).chip()).isEqualTo(ModelChip.Local)
    }

    @Test
    fun aFileGoneOutsideTheApp_stopsSayingLocalOnTheNextRescan() = runTest(dispatcher) {
        setup.ready(ModelId.EG2_270M, ComputeBackend.CPU)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.model(ModelId.EG2_270M).chip()).isEqualTo(ModelChip.Local)

        setup.missing(ModelId.EG2_270M)
        viewModel.onResume()
        advanceUntilIdle()

        assertThat(viewModel.model(ModelId.EG2_270M).status).isNotInstanceOf(ModelSetupStatus.Ready::class.java)
        assertThat(viewModel.model(ModelId.EG2_270M).chip()).isNotEqualTo(ModelChip.Local)
    }

    @Test
    fun chipRule_P14() {
        fun overview(id: ModelId, status: ModelSetupStatus) = ModelCatalog.byId(id).let {
            ModelOverview(id, it.displayName, it.fileName, it.sizeBytes, it.sha256, null, status)
        }
        val gemmaSize = ModelCatalog.byId(ModelId.GEMMA4_E2B).sizeBytes
        val embeddingSize = ModelCatalog.byId(ModelId.EG2_270M).sizeBytes

        assertThat(overview(ModelId.GEMMA4_E2B, ModelSetupStatus.Ready(ComputeBackend.GPU, gemmaSize)).chip()).isEqualTo(ModelChip.Local)
        assertThat(overview(ModelId.GEMMA4_E2B, ModelSetupStatus.Ready(ComputeBackend.CPU, gemmaSize)).chip()).isEqualTo(ModelChip.CpuSlow)
        assertThat(overview(ModelId.EG2_270M, ModelSetupStatus.Ready(ComputeBackend.CPU, embeddingSize)).chip()).isEqualTo(ModelChip.Local)
        val unconfigured = ModelSetupStatus.Unconfigured()
        assertThat(overview(ModelId.GEMMA4_E2B, unconfigured).chip()).isEqualTo(ModelChip.Setup(unconfigured))
    }

    @Test
    fun theOpenSheetSurvivesInTheSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val viewModel = viewModel(saved)
        advanceUntilIdle()

        viewModel.onOpenModel(ModelId.EG2_270M)
        advanceUntilIdle()
        assertThat(viewModel.state.value.openSheet).isEqualTo(ModelId.EG2_270M)

        val restored = viewModel(saved)
        advanceUntilIdle()
        assertThat(restored.state.value.openSheet).isEqualTo(ModelId.EG2_270M)

        restored.onCloseSheet()
        advanceUntilIdle()
        assertThat(restored.state.value.openSheet).isNull()
    }
}

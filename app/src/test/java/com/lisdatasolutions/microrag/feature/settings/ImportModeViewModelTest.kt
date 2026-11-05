package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.ObserveImportModeUseCase
import com.lisdatasolutions.microrag.domain.multimodal.SetDefaultImportModeUseCase
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F19 T047 (contract U6): «Modo al importar PDF», shaped as Tema. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportModeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeMultimodalStore(StoredMultimodal(MultimodalStage.ON))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        ImportModeViewModel(ObserveImportModeUseCase(store), SetDefaultImportModeUseCase(store), saved)

    @Test
    fun opensOnTheSavedMode_Both_byDefault() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.saved).isEqualTo(IndexMode.BOTH)
        assertThat(vm.state.value.selected).isEqualTo(IndexMode.BOTH)
    }

    @Test
    fun theMarkedMode_survivesARotation_andASaveStoresItAndCloses() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        advanceUntilIdle()
        vm.onSelect(IndexMode.VISUAL)

        val rotated = viewModel(saved)
        advanceUntilIdle()
        assertThat(rotated.state.value.selected).isEqualTo(IndexMode.VISUAL)

        val closed = async { rotated.events.first() }
        rotated.onSave()
        advanceUntilIdle()
        assertThat(closed.await()).isEqualTo(ImportModeEvent.Close)
        assertThat(store.state.value.defaultMode).isEqualTo(IndexMode.VISUAL)
    }

    @Test
    fun anUnchangedSave_closesWithoutWriting() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val closed = async { vm.events.first() }
        vm.onSave()
        advanceUntilIdle()

        assertThat(closed.await()).isEqualTo(ImportModeEvent.Close)
        assertThat(store.writes).isEmpty()
    }

    @Test
    fun aFailedSave_keepsThePreviousMode_andSaysSo() = runTest(dispatcher) {
        store.failWrites = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelect(IndexMode.TEXT)

        vm.onSave()
        advanceUntilIdle()

        assertThat(vm.state.value.saveFailed).isTrue()
        assertThat(vm.state.value.saved).isEqualTo(IndexMode.BOTH)
        vm.onSaveFailureShown()
        advanceUntilIdle()
        assertThat(vm.state.value.saveFailed).isFalse()
    }
}

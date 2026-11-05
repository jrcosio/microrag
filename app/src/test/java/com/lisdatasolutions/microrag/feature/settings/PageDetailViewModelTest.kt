package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.ObservePageDetailUseCase
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import com.lisdatasolutions.microrag.domain.multimodal.SetPageDetailUseCase
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import java.util.Locale
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

/** F20 T032 (contract U2, M2): «Detalle de las páginas», shaped as «Modo al importar PDF». */
@OptIn(ExperimentalCoroutinesApi::class)
class PageDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeMultimodalStore(StoredMultimodal(MultimodalStage.ON))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        PageDetailViewModel(ObservePageDetailUseCase(store), SetPageDetailUseCase(store), saved)

    @Test
    fun opensOnTheSavedDetail_Balanced_byDefault() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.saved).isEqualTo(PageDetail.BALANCED)
        assertThat(vm.state.value.selected).isEqualTo(PageDetail.BALANCED)
    }

    @Test
    fun theMarkedDetail_survivesARotation_andASaveStoresItAndCloses() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        advanceUntilIdle()
        vm.onSelect(PageDetail.FAST)

        val rotated = viewModel(saved)
        advanceUntilIdle()
        assertThat(rotated.state.value.selected).isEqualTo(PageDetail.FAST)

        val closed = async { rotated.events.first() }
        rotated.onSave()
        advanceUntilIdle()
        assertThat(closed.await()).isEqualTo(PageDetailEvent.Close)
        assertThat(store.state.value.pageDetail).isEqualTo(PageDetail.FAST)
    }

    @Test
    fun aFailedSave_keepsThePreviousDetail_andSaysSo() = runTest(dispatcher) {
        store.failWrites = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSelect(PageDetail.DETAILED)

        vm.onSave()
        advanceUntilIdle()

        assertThat(vm.state.value.saveFailed).isTrue()
        assertThat(vm.state.value.saved).isEqualTo(PageDetail.BALANCED)
        assertThat(store.state.value.pageDetail).isEqualTo(PageDetail.BALANCED)
    }

    @Test
    fun withTheModeOff_itCloses() = runTest(dispatcher) {
        store.state.value = StoredMultimodal(MultimodalStage.OFF)
        val vm = viewModel()

        val closed = async { vm.events.first() }
        advanceUntilIdle()

        assertThat(closed.await()).isEqualTo(PageDetailEvent.Close)
        assertThat(vm.state.value.saved).isNull()
    }

    @Test
    fun theWaitsAreWrittenAsTheLocaleWritesNumbers() {
        assertThat(waitSeconds(115, Locale.forLanguageTag("es"))).isEqualTo("11,5")
        assertThat(waitSeconds(115, Locale.ENGLISH)).isEqualTo("11.5")
        assertThat(waitSeconds(100, Locale.forLanguageTag("es"))).isEqualTo("10")
        assertThat(waitTenthsFor(280)).isEqualTo(145)
        assertThat(waitTenthsFor(99)).isNull()
    }
}

package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.CancelMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.DisableMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.DismissMultimodalErrorUseCase
import com.lisdatasolutions.microrag.domain.multimodal.EnableMultimodalResult
import com.lisdatasolutions.microrag.domain.multimodal.EnableMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalController
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.ObserveMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.PauseMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.ResumeMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.RetryMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.ModelSetupPolicy
import com.lisdatasolutions.microrag.domain.onboarding.PauseModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.testing.FakeDeviceResources
import com.lisdatasolutions.microrag.testing.FakeModelDownloadController
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
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

/** F19 T021 (contracts/multimodal-ui.md U2–U4, U7; analyze M1 and M7): the «Modo multimodal» block of Ajustes. */
@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeMultimodalStore()
    private val downloads = FakeModelDownloadController()
    private val onboarding = FakeOnboardingStore()
    private val resources = FakeDeviceResources()
    private val controller = FakeController()

    private class FakeController : MultimodalController {
        val calls = mutableListOf<String>()
        var enableResult: EnableMultimodalResult = EnableMultimodalResult.Started

        override fun start() = Unit
        override suspend fun enable(): EnableMultimodalResult = enableResult.also { calls += "enable" }
        override suspend fun disable() { calls += "disable" }
        override suspend fun retry(): EnableMultimodalResult = EnableMultimodalResult.Started.also { calls += "retry" }
        override suspend fun cancelDownload() { calls += "cancel" }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()): MultimodalSettingsViewModel {
        val repository = InMemoryModelFileRepository()
        return MultimodalSettingsViewModel(
            observe = ObserveMultimodalUseCase(store, downloads, onboarding, resources),
            enable = EnableMultimodalUseCase(controller),
            disable = DisableMultimodalUseCase(controller),
            retry = RetryMultimodalUseCase(controller),
            cancel = CancelMultimodalDownloadUseCase(controller),
            pause = PauseMultimodalDownloadUseCase(PauseModelDownloadUseCase(onboarding, downloads)),
            resume = ResumeMultimodalDownloadUseCase(ResumeModelDownloadUseCase(repository, onboarding, downloads)),
            dismissError = DismissMultimodalErrorUseCase(store),
            savedState = saved,
            backgroundDispatcher = dispatcher,
        )
    }

    @Test
    fun off_asksBeforeTurningOn_andTheDialogSurvivesARotation() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val viewModel = viewModel(saved)
        advanceUntilIdle()
        assertThat(viewModel.state.value.switchOn).isFalse()
        assertThat(viewModel.state.value.switchEnabled).isTrue()
        assertThat(viewModel.state.value.actions).isEmpty()

        viewModel.onToggle(true)
        advanceUntilIdle()
        assertThat(viewModel.state.value.dialog).isEqualTo(MultimodalDialog.ENABLE)
        val rotated = viewModel(saved)
        advanceUntilIdle()
        assertThat(rotated.state.value.dialog).isEqualTo(MultimodalDialog.ENABLE)

        viewModel.onDismissDialog()
        advanceUntilIdle()
        assertThat(viewModel.state.value.dialog).isNull()
        assertThat(controller.calls).isEmpty()

        viewModel.onToggle(true)
        viewModel.onConfirmEnable()
        advanceUntilIdle()
        assertThat(controller.calls).containsExactly("enable")
        assertThat(viewModel.state.value.dialog).isNull()
    }

    @Test
    fun theDownloadSize_isThe440MOfTheCatalog() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.downloadBytes).isEqualTo(ModelCatalog.byId(ModelId.EG2_440M).sizeBytes)
    }

    @Test
    fun whileDownloading_theSwitchIsOnAndLocked_withPauseAndCancel() = runTest(dispatcher) {
        store.state.value = StoredMultimodal(MultimodalStage.DOWNLOADING)
        downloads.progress.value = DownloadProgress(active = ModelId.EG2_440M, bytesDone = 450, totalBytes = 1_000)
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.switchOn).isTrue()
        assertThat(state.switchEnabled).isFalse()
        assertThat(state.percent).isEqualTo(45)
        assertThat(state.showsProgress).isTrue()
        assertThat(state.actions).containsExactly(MultimodalAction.PAUSE, MultimodalAction.CANCEL).inOrder()

        // Touching it during the activation does nothing.
        viewModel.onToggle(false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.dialog).isNull()

        viewModel.onAction(MultimodalAction.PAUSE)
        advanceUntilIdle()
        assertThat(onboarding.downloadPaused.value).isTrue()
        assertThat(viewModel.state.value.actions).containsExactly(MultimodalAction.RESUME, MultimodalAction.CANCEL).inOrder()
        assertThat(viewModel.state.value.showsProgress).isFalse()

        viewModel.onAction(MultimodalAction.RESUME)
        viewModel.onAction(MultimodalAction.CANCEL)
        advanceUntilIdle()
        assertThat(onboarding.downloadPaused.value).isFalse()
        assertThat(controller.calls).containsExactly("cancel")
    }

    @Test
    fun downloadFailures_stayDownloading_withRetryAndCancel() = runTest(dispatcher) {
        resources.free = 100L * 1024 * 1024
        store.state.value = StoredMultimodal(MultimodalStage.DOWNLOADING, MultimodalError.NO_SPACE)
        val viewModel = viewModel()
        advanceUntilIdle()

        val size = ModelCatalog.byId(ModelId.EG2_440M).sizeBytes
        assertThat(viewModel.state.value.missingBytes).isEqualTo(size + ModelSetupPolicy.SPACE_MARGIN_BYTES - resources.free)
        assertThat(viewModel.state.value.actions).containsExactly(MultimodalAction.RETRY, MultimodalAction.CANCEL).inOrder()

        store.state.value = StoredMultimodal(MultimodalStage.DOWNLOADING, MultimodalError.DOWNLOAD_FAILED)
        advanceUntilIdle()
        assertThat(viewModel.state.value.actions).containsExactly(MultimodalAction.RETRY, MultimodalAction.CANCEL).inOrder()
        viewModel.onAction(MultimodalAction.RETRY)
        advanceUntilIdle()
        assertThat(controller.calls).containsExactly("retry")

        // Offline: it goes on by itself, so only «Cancelar».
        store.state.value = StoredMultimodal(MultimodalStage.DOWNLOADING)
        resources.hasInternet.value = false
        advanceUntilIdle()
        assertThat(viewModel.state.value.offline).isTrue()
        assertThat(viewModel.state.value.actions).containsExactly(MultimodalAction.CANCEL)
    }

    @Test
    fun aFailedCheck_isOffWithItsReason_andAceptarClearsIt() = runTest(dispatcher) {
        store.state.value = StoredMultimodal(MultimodalStage.OFF, MultimodalError.IDENTITY_MISMATCH)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.switchOn).isFalse()
        assertThat(viewModel.state.value.actions).containsExactly(MultimodalAction.OK)

        viewModel.onAction(MultimodalAction.OK)
        advanceUntilIdle()
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.actions).isEmpty()
    }

    @Test
    fun theCheckAndTheSwitch_haveNoButtons() = runTest(dispatcher) {
        for (stage in listOf(MultimodalStage.VALIDATING, MultimodalStage.SWITCHING)) {
            store.state.value = StoredMultimodal(stage)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertThat(viewModel.state.value.actions).isEmpty()
            assertThat(viewModel.state.value.switchEnabled).isFalse()
            assertThat(viewModel.state.value.showsProgress).isFalse()
        }
    }

    @Test
    fun reachingOn_isAnnouncedOnce() = runTest(dispatcher) {
        store.state.value = StoredMultimodal(MultimodalStage.SWITCHING)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.value.enabledNotice).isFalse()

        store.state.value = StoredMultimodal(MultimodalStage.ON, textEmbedder = ModelId.EG2_440M)
        advanceUntilIdle()
        assertThat(viewModel.state.value.enabledNotice).isTrue()

        viewModel.onNoticeShown()
        store.state.value = StoredMultimodal(MultimodalStage.ON, defaultMode = IndexMode.VISUAL)
        advanceUntilIdle()
        assertThat(viewModel.state.value.enabledNotice).isFalse()
    }

    @Test
    fun openingOnOn_isNotAnnounced_butTurningItOnAgainAtOnceIs() = runTest(dispatcher) {
        store.state.value = StoredMultimodal(MultimodalStage.ON)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.value.enabledNotice).isFalse()

        viewModel.onToggle(false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.dialog).isEqualTo(MultimodalDialog.DISABLE)
        viewModel.onConfirmDisable()
        advanceUntilIdle()
        assertThat(controller.calls).containsExactly("disable")

        controller.enableResult = EnableMultimodalResult.On
        store.state.value = StoredMultimodal(MultimodalStage.OFF)
        advanceUntilIdle()
        viewModel.onToggle(true)
        viewModel.onConfirmEnable()
        advanceUntilIdle()
        assertThat(viewModel.state.value.enabledNotice).isTrue()
    }
}

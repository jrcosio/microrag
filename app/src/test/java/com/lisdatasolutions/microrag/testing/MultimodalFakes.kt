package com.lisdatasolutions.microrag.testing

import androidx.lifecycle.SavedStateHandle
import com.lisdatasolutions.microrag.domain.multimodal.CancelMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.DisableMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.DismissMultimodalErrorUseCase
import com.lisdatasolutions.microrag.domain.multimodal.EnableMultimodalResult
import com.lisdatasolutions.microrag.domain.multimodal.EnableMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalController
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStore
import com.lisdatasolutions.microrag.domain.multimodal.ObserveMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.PauseMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.ResumeMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.RetryMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.PauseModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
import com.lisdatasolutions.microrag.feature.settings.MultimodalSettingsViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/** The mode's copy in memory (F19 contract M1); [failWrites] makes every write fail and keep the value. */
class FakeMultimodalStore(initial: StoredMultimodal = StoredMultimodal()) : MultimodalStore {
    override val state = MutableStateFlow(initial)
    var failWrites = false
    val writes = mutableListOf<StoredMultimodal>()

    override suspend fun write(transform: (StoredMultimodal) -> StoredMultimodal): Boolean {
        if (failWrites) return false
        state.value = transform(state.value).also { writes += it }
        return true
    }
}

/** A controller that only records what it is asked (screens and navigation tests). */
class FakeMultimodalController : MultimodalController {
    val calls = mutableListOf<String>()
    var enableResult: EnableMultimodalResult = EnableMultimodalResult.Started

    override fun start() = Unit
    override suspend fun enable(): EnableMultimodalResult = enableResult.also { calls += "enable" }
    override suspend fun disable() { calls += "disable" }
    override suspend fun retry(): EnableMultimodalResult = EnableMultimodalResult.Started.also { calls += "retry" }
    override suspend fun cancelDownload() { calls += "cancel" }
}

/** The «Modo multimodal» block's ViewModel over fakes, for the screens that host Ajustes. */
fun fakeMultimodalSettingsViewModel(
    savedState: SavedStateHandle = SavedStateHandle(),
    store: FakeMultimodalStore = FakeMultimodalStore(),
    controller: MultimodalController = FakeMultimodalController(),
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
): MultimodalSettingsViewModel {
    val downloads = FakeModelDownloadController()
    val onboarding = FakeOnboardingStore()
    return MultimodalSettingsViewModel(
        observe = ObserveMultimodalUseCase(store, downloads, onboarding, FakeDeviceResources()),
        enable = EnableMultimodalUseCase(controller),
        disable = DisableMultimodalUseCase(controller),
        retry = RetryMultimodalUseCase(controller),
        cancel = CancelMultimodalDownloadUseCase(controller),
        pause = PauseMultimodalDownloadUseCase(PauseModelDownloadUseCase(onboarding, downloads)),
        resume = ResumeMultimodalDownloadUseCase(ResumeModelDownloadUseCase(InMemoryModelFileRepository(), onboarding, downloads)),
        dismissError = DismissMultimodalErrorUseCase(store),
        savedState = savedState,
        backgroundDispatcher = dispatcher,
    )
}

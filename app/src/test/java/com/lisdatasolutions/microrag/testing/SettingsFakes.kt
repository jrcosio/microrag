package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.LocalModelWork
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.LocalModelWorkTracker
import com.lisdatasolutions.microrag.domain.onboarding.ObserveModelSetupUseCase
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.AppLanguageController
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.update

/**
 * [AppLanguageController] with every outcome programmable (F12 T020): the Android 12 path when
 * [recreatesItself] is false, a failed write with [failApply] and an unreadable file with [readFailed].
 */
class FakeAppLanguageController(
    var language: AppLanguage = AppLanguage.SPANISH,
    override var recreatesItself: Boolean = true,
    override var readFailed: Boolean = false,
) : AppLanguageController {
    var failApply = false
    val applied = mutableListOf<AppLanguage>()

    /** While set and not completed, apply() waits: what Idiomas shows while it saves. */
    var gate: CompletableDeferred<Unit>? = null

    override fun current() = language

    override suspend fun apply(language: AppLanguage): Boolean {
        applied += language
        gate?.await()
        if (failApply) return false
        this.language = language
        return true
    }
}

/**
 * The Preparation's own [ObserveModelSetupUseCase] over fakes, so Ajustes sees exactly what Setup sees
 * (F12 P13), with each required model's state set by hand (F12 T020).
 */
class FakeModelSetup {
    val repository = InMemoryModelFileRepository()
    val controller = FakeModelDownloadController()
    val store = FakeOnboardingStore(OnboardingStage.COMPLETED)
    val resources = FakeDeviceResources()
    val work = LocalModelWorkTracker()
    val useCase = ObserveModelSetupUseCase(repository, controller, store, resources, work)

    /** Verified and prepared on [backend]: the Preparation says Ready. */
    fun ready(id: ModelId, backend: ComputeBackend) {
        repository.states.update { it + (id to ModelFileState.Verified(File("/models/${ModelCatalog.byId(id).fileName}"))) }
        repository.preparedBackends.update { it + (id to backend) }
    }

    /** No file: the Preparation says Unconfigured. */
    fun missing(id: ModelId) {
        repository.states.update { it + (id to ModelFileState.Missing) }
        repository.preparedBackends.update { it - id }
    }

    /** The file is verified but the on-device test is running: the Preparation says Preparing. */
    fun preparing(id: ModelId) {
        repository.states.update { it + (id to ModelFileState.Verified(File("/models/${ModelCatalog.byId(id).fileName}"))) }
        repository.preparedBackends.update { it - id }
        work.set(id, LocalModelWork.Preparing)
    }
}

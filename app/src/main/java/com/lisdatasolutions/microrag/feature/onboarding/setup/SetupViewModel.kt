package com.lisdatasolutions.microrag.feature.onboarding.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.SetupRowAction
import com.lisdatasolutions.microrag.domain.model.SetupStepResolver
import com.lisdatasolutions.microrag.domain.onboarding.BuildDownloadConfirmationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.CompleteOnboardingUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ImportModelFileUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ObserveModelSetupUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PauseModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PrepareModelsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PrepareStorageUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ReconcileDownloadsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RetryModelUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartDownloadResult
import com.lisdatasolutions.microrag.domain.onboarding.StartModelDownloadUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * P02 Preparación inicial (FR-005 – FR-010, D-027). Downloads, tests and imports run outside this
 * ViewModel (job, application scope), so leaving the screen never cancels them (FR-003).
 */
class SetupViewModel(
    observeSetup: ObserveModelSetupUseCase,
    private val buildConfirmation: BuildDownloadConfirmationUseCase,
    private val startDownload: StartModelDownloadUseCase,
    private val pauseDownload: PauseModelDownloadUseCase,
    private val resumeDownload: ResumeModelDownloadUseCase,
    private val retryModel: RetryModelUseCase,
    private val reconcile: ReconcileDownloadsUseCase,
    private val prepareModels: PrepareModelsUseCase,
    private val importModel: ImportModelFileUseCase,
    private val prepareStorage: PrepareStorageUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val confirmation = MutableStateFlow<ConfirmationUi?>(null)
    private val events = Channel<SetupEvent>(Channel.BUFFERED)

    /** Activity-bound requests: permission prompt, system settings, file picker, navigation. */
    val setupEvents: Flow<SetupEvent> = events.receiveAsFlow()

    private val statuses = observeSetup()

    val state: StateFlow<SetupUiState> = combine(statuses, confirmation) { statuses, confirm ->
        val storage = prepareStorage()
        SetupUiState(
            step = SetupStepResolver.step(statuses.values),
            rows = DISPLAY_ORDER.map { ModelRowUi(it, statuses.getValue(it)) },
            storage = storage,
            blocker = SetupStepResolver.blocker(statuses.values, storage),
            confirmation = confirm,
        )
    }.flowOn(backgroundDispatcher).stateIn(viewModelScope, SharingStarted.Eagerly, SetupUiState())

    init {
        // A verified file without a tested backend starts its on-device test while setup is open.
        statuses.map { all -> all.values.any { it == ModelSetupStatus.Preparing } }
            .distinctUntilChanged()
            .filter { it }
            .onEach { prepareModels.launch() }
            .launchIn(viewModelScope)
    }

    /** The screen is in front again: resume what lost its job and verify what was left unchecked. */
    fun onResumeVisible() {
        viewModelScope.launch {
            reconcile()
            prepareModels.launch()
        }
    }

    fun onRowClick(id: ModelId) {
        val row = state.value.rows.firstOrNull { it.id == id } ?: return
        when (row.action) {
            SetupRowAction.OPEN_CONFIRMATION -> openConfirmation(id)
            SetupRowAction.PAUSE -> viewModelScope.launch { pauseDownload() }
            SetupRowAction.RESUME -> viewModelScope.launch { resumeDownload() }
            SetupRowAction.RETRY -> viewModelScope.launch { retryModel(id) }
            SetupRowAction.OPEN_STORAGE_SETTINGS -> events.trySend(SetupEvent.OpenStorageSettings)
            SetupRowAction.NONE -> Unit
        }
    }

    fun onDismissConfirmation() {
        confirmation.value = null
    }

    /**
     * «Descargar». On Android 13+ without the notification permission the system prompt comes first;
     * the download starts whatever the answer is (FR-010).
     */
    fun onConfirmDownload(needsNotificationPermission: Boolean) {
        confirmation.value = null
        if (needsNotificationPermission) events.trySend(SetupEvent.RequestNotificationPermission) else start()
    }

    fun onNotificationPermissionResult() = start()

    /** «Seleccionar archivo del dispositivo»: imports the model of the row that opened the dialog. */
    fun onPickFile() {
        val target = confirmation.value?.target ?: return
        confirmation.value = null
        events.trySend(SetupEvent.PickFile(target))
    }

    fun onFilePicked(target: ModelId, uri: String?) {
        if (uri != null) importModel.launch(target, uri)
    }

    fun onContinue() {
        viewModelScope.launch { if (completeOnboarding()) events.send(SetupEvent.NavigateHome) }
    }

    private fun openConfirmation(target: ModelId) {
        viewModelScope.launch(backgroundDispatcher) { confirmation.value = ConfirmationUi(target, buildConfirmation()) }
    }

    private fun start() {
        viewModelScope.launch {
            // Space may have changed since the dialog: show it again with the fresh numbers.
            if (startDownload() is StartDownloadResult.NoSpace) openConfirmation(state.value.rows.first().id)
        }
    }

    private companion object {
        /** PNG 02 order. */
        val DISPLAY_ORDER = listOf(ModelId.GEMMA4_E2B, ModelId.EG2_270M)
    }
}

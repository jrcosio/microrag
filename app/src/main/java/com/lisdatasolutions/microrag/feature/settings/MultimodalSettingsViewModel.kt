package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.CancelMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.DisableMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.DismissMultimodalErrorUseCase
import com.lisdatasolutions.microrag.domain.multimodal.EnableMultimodalResult
import com.lisdatasolutions.microrag.domain.multimodal.EnableMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalState
import com.lisdatasolutions.microrag.domain.multimodal.ObserveMultimodalUseCase
import com.lisdatasolutions.microrag.domain.multimodal.PauseMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.ResumeMultimodalDownloadUseCase
import com.lisdatasolutions.microrag.domain.multimodal.RetryMultimodalUseCase
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The confirmation open over Ajustes (wireframe §2 and §3), kept in the SavedStateHandle. */
enum class MultimodalDialog { ENABLE, DISABLE }

/** A button under the switch while the mode is being turned on or has failed (wireframe §2). */
enum class MultimodalAction { PAUSE, RESUME, RETRY, CANCEL, OK }

/** The «Modo multimodal» block of Ajustes (contracts/multimodal-ui.md U2–U4, U7). */
data class MultimodalUiState(
    val stage: MultimodalStage = MultimodalStage.OFF,
    val error: MultimodalError? = null,
    val defaultMode: IndexMode = IndexMode.BOTH,
    /** The 440M's download, 0–100, once its first bytes are known. */
    val percent: Int? = null,
    val paused: Boolean = false,
    val offline: Boolean = false,
    val missingBytes: Long? = null,
    val downloadBytes: Long = ModelCatalog.byId(ModelId.EG2_440M).sizeBytes,
    val dialog: MultimodalDialog? = null,
    /** U7: «Modo multimodal activado», shown once. */
    val enabledNotice: Boolean = false,
) {
    /** On from the moment it is confirmed; only OFF and ON can be touched (U2). */
    val switchOn: Boolean get() = stage != MultimodalStage.OFF
    val switchEnabled: Boolean get() = stage == MultimodalStage.OFF || stage == MultimodalStage.ON

    val actions: List<MultimodalAction> get() = when (stage) {
        MultimodalStage.DOWNLOADING -> when {
            error != null -> listOf(MultimodalAction.RETRY, MultimodalAction.CANCEL)
            paused -> listOf(MultimodalAction.RESUME, MultimodalAction.CANCEL)
            offline -> listOf(MultimodalAction.CANCEL)
            else -> listOf(MultimodalAction.PAUSE, MultimodalAction.CANCEL)
        }
        MultimodalStage.OFF -> if (error != null) listOf(MultimodalAction.OK) else emptyList()
        MultimodalStage.VALIDATING, MultimodalStage.SWITCHING, MultimodalStage.ON -> emptyList()
    }

    /** The real download's bar while it moves; the check and the switch are short and have none. */
    val showsProgress: Boolean get() = stage == MultimodalStage.DOWNLOADING && error == null && !paused && !offline
}

/**
 * The multimodal mode in Ajustes (F19 US1): its stage as the controller saves it, the two confirmations, and the
 * buttons of each stage. The stage reads the files and the download, so it is gathered on [backgroundDispatcher].
 */
class MultimodalSettingsViewModel(
    observe: ObserveMultimodalUseCase,
    private val enable: EnableMultimodalUseCase,
    private val disable: DisableMultimodalUseCase,
    private val retry: RetryMultimodalUseCase,
    private val cancel: CancelMultimodalDownloadUseCase,
    private val pause: PauseMultimodalDownloadUseCase,
    private val resume: ResumeMultimodalDownloadUseCase,
    private val dismissError: DismissMultimodalErrorUseCase,
    private val savedState: SavedStateHandle,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val notice = MutableStateFlow(false)
    private var lastStage: MultimodalStage? = null

    private val mode = observe().flowOn(backgroundDispatcher)
        // U7: an activation that ends while the screen watches it is announced once.
        .onEach { if (lastStage?.activating == true && it.stage == MultimodalStage.ON) notice.value = true }
        .onEach { lastStage = it.stage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<MultimodalUiState> =
        combine(mode, savedState.getStateFlow<String?>(DIALOG, null), notice) { mode, dialog, notice ->
            (mode?.toUi() ?: MultimodalUiState()).copy(dialog = dialog?.let(MultimodalDialog::valueOf), enabledNotice = notice)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MultimodalUiState())

    private fun MultimodalState.toUi() = MultimodalUiState(
        stage = stage,
        error = error,
        defaultMode = defaultMode,
        percent = download?.takeIf { it.totalBytes > 0 }?.let { (it.bytesDone * 100 / it.totalBytes).toInt().coerceIn(0, 100) },
        paused = downloadPaused,
        offline = offline,
        missingBytes = missingBytes,
        downloadBytes = downloadBytes,
    )

    fun onToggle(checked: Boolean) {
        val stage = state.value.stage
        when {
            checked && stage == MultimodalStage.OFF -> savedState[DIALOG] = MultimodalDialog.ENABLE.name
            !checked && stage == MultimodalStage.ON -> savedState[DIALOG] = MultimodalDialog.DISABLE.name
        }
    }

    fun onDismissDialog() {
        savedState[DIALOG] = null
    }

    fun onConfirmEnable() {
        savedState[DIALOG] = null
        viewModelScope.launch { if (enable() == EnableMultimodalResult.On) notice.value = true }
    }

    fun onConfirmDisable() {
        savedState[DIALOG] = null
        viewModelScope.launch { disable() }
    }

    fun onAction(action: MultimodalAction) {
        viewModelScope.launch {
            when (action) {
                MultimodalAction.PAUSE -> pause()
                MultimodalAction.RESUME -> resume()
                MultimodalAction.RETRY -> retry()
                MultimodalAction.CANCEL -> cancel()
                MultimodalAction.OK -> dismissError()
            }
        }
    }

    fun onNoticeShown() {
        notice.value = false
    }

    private companion object {
        const val DIALOG = "settings_multimodal_dialog"
    }
}

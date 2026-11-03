package com.lisdatasolutions.microrag.feature.settings

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.ModelOverview
import com.lisdatasolutions.microrag.domain.settings.ThemePreference

/** P09 (data-model §5). [models] is empty until the first rescan, so the rows show no state yet. */
data class SettingsUiState(
    val models: List<ModelOverview> = emptyList(),
    /** Subtitle of «Idiomas» (S3). */
    val appLanguage: AppLanguage = AppLanguage.SPANISH,
    /** Subtitle of «Tema» (F16 A1). */
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /** The model whose sheet is open; kept in the SavedStateHandle. */
    val openSheet: ModelId? = null,
    /** The confirmation of «Borrar historial de chat», kept in the SavedStateHandle (S6). */
    val confirmClear: ConfirmClear? = null,
    val clearing: Boolean = false,
    /** Shown once in the snackbar, which TalkBack reads (S6). */
    val message: SettingsMessage? = null,
)

/** S6: [stopsAnswer] adds «La respuesta en curso se detendrá.». */
data class ConfirmClear(val stopsAnswer: Boolean)

enum class SettingsMessage { HISTORY_CLEARED, NO_CONVERSATIONS, CLEAR_FAILED }

/** What the chip of a model row says (F12 P14). */
sealed interface ModelChip {
    /** «Local»: verified and prepared. */
    data object Local : ModelChip

    /** «Modo CPU, más lento»: Gemma prepared on CPU only (D-027). */
    data object CpuSlow : ModelChip

    /** Not ready: the Preparation's own chip. */
    data class Setup(val status: ModelSetupStatus) : ModelChip
}

fun ModelOverview.chip(): ModelChip = when {
    status !is ModelSetupStatus.Ready -> ModelChip.Setup(status)
    id == ModelId.GEMMA4_E2B && status.backend == ComputeBackend.CPU -> ModelChip.CpuSlow
    else -> ModelChip.Local
}

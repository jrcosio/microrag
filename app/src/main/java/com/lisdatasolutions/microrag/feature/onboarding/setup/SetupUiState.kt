package com.lisdatasolutions.microrag.feature.onboarding.setup

import android.net.Uri
import com.lisdatasolutions.microrag.domain.model.ContinueBlocker
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.SetupRowAction
import com.lisdatasolutions.microrag.domain.model.StorageStatus
import com.lisdatasolutions.microrag.domain.model.rowAction
import com.lisdatasolutions.microrag.domain.onboarding.DownloadConfirmation

/** P02 state (data-model §8). Rows follow PNG 02: Gemma 4 first, then EmbeddingGemma 2. */
data class SetupUiState(
    val step: Int = 1,
    val rows: List<ModelRowUi> = emptyList(),
    val storage: StorageStatus = StorageStatus.Preparing,
    val blocker: ContinueBlocker? = ContinueBlocker.MODELS_NOT_CONFIGURED,
    val confirmation: ConfirmationUi? = null,
) {
    val continueEnabled: Boolean get() = blocker == null

    /** Something was requested and is still on its way: used to warn when notifications are off (FR-010). */
    val downloadUnderway: Boolean
        get() = rows.any {
            it.status is ModelSetupStatus.Queued || it.status is ModelSetupStatus.Downloading ||
                it.status is ModelSetupStatus.Paused || it.status is ModelSetupStatus.WaitingForNetwork
        }
}

data class ModelRowUi(val id: ModelId, val status: ModelSetupStatus) {
    val action: SetupRowAction get() = status.rowAction()
}

/** Open download confirmation; [target] is the row that opened it and the model a picked file is for. */
data class ConfirmationUi(val target: ModelId, val details: DownloadConfirmation)

/** One-off requests from the ViewModel that need the Activity. */
sealed interface SetupEvent {
    data object RequestNotificationPermission : SetupEvent

    data object OpenStorageSettings : SetupEvent

    data class PickFile(val target: ModelId) : SetupEvent

    data object NavigateHome : SetupEvent
}

/** File chosen with the system picker for [target] (FR-024). */
data class PickedFile(val target: ModelId, val uri: Uri)

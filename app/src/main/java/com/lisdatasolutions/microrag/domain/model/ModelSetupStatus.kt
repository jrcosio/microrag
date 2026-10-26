package com.lisdatasolutions.microrag.domain.model

/**
 * What a model row shows during setup (F03 FR-007, data-model §2). Only [Ready] means the model can
 * be used, and it is only produced for a hash-verified file that passed the on-device test
 * (constitution V).
 */
sealed interface ModelSetupStatus {

    data class Unconfigured(val partialBytes: Long = 0) : ModelSetupStatus

    /** Requested and waiting for its turn; [after] is the model downloading ahead of it, if any. */
    data class Queued(val after: ModelId?, val bytesDone: Long, val totalBytes: Long) : ModelSetupStatus

    data class Downloading(val bytesDone: Long, val totalBytes: Long) : ModelSetupStatus

    data class Importing(val bytesDone: Long, val totalBytes: Long) : ModelSetupStatus

    data class Paused(val bytesDone: Long, val totalBytes: Long) : ModelSetupStatus

    data class WaitingForNetwork(val bytesDone: Long, val totalBytes: Long) : ModelSetupStatus

    data class Verifying(val bytesDone: Long, val totalBytes: Long) : ModelSetupStatus

    data object Preparing : ModelSetupStatus

    data class Ready(val backend: ComputeBackend, val sizeBytes: Long) : ModelSetupStatus

    data class NoSpace(val neededBytes: Long) : ModelSetupStatus

    data class Incompatible(val problem: SetupProblem) : ModelSetupStatus

    data class Failed(val problem: SetupProblem) : ModelSetupStatus
}

/** Reason shown under an [ModelSetupStatus.Incompatible] or [ModelSetupStatus.Failed] row. */
sealed interface SetupProblem {
    data object HashMismatch : SetupProblem

    /** Download error; [httpCode] is null for network failures. */
    data class DownloadFailed(val httpCode: Int?) : SetupProblem

    data object ForbiddenOrigin : SetupProblem

    data object Storage : SetupProblem

    /** An imported file that is not the expected model. */
    data object WrongFile : SetupProblem

    /** The runtime cannot run the model on this device. */
    data object DeviceIncompatible : SetupProblem
}

/** Action of a tap on a model row (data-model §2). */
enum class SetupRowAction { OPEN_CONFIRMATION, PAUSE, RESUME, RETRY, OPEN_STORAGE_SETTINGS, NONE }

/** In-app work on a model that is not a download: the on-device test or an import. */
sealed interface LocalModelWork {
    data object Preparing : LocalModelWork

    data class Importing(val bytesDone: Long, val totalBytes: Long) : LocalModelWork

    /** The last test or import ended without a usable model. */
    data class Rejected(val problem: SetupProblem) : LocalModelWork
}

fun ModelSetupStatus.rowAction(): SetupRowAction = when (this) {
    is ModelSetupStatus.Unconfigured, is ModelSetupStatus.Incompatible -> SetupRowAction.OPEN_CONFIRMATION
    is ModelSetupStatus.Downloading, is ModelSetupStatus.Queued, is ModelSetupStatus.WaitingForNetwork -> SetupRowAction.PAUSE
    is ModelSetupStatus.Paused -> SetupRowAction.RESUME
    is ModelSetupStatus.Failed -> SetupRowAction.RETRY
    is ModelSetupStatus.NoSpace -> SetupRowAction.OPEN_STORAGE_SETTINGS
    is ModelSetupStatus.Importing, is ModelSetupStatus.Verifying, ModelSetupStatus.Preparing, is ModelSetupStatus.Ready -> SetupRowAction.NONE
}

/** Whole percent of real bytes, never above 100 (FR-017). */
fun percentOf(bytesDone: Long, totalBytes: Long): Int =
    if (totalBytes <= 0) 0 else (bytesDone.coerceAtLeast(0) * 100 / totalBytes).coerceAtMost(100).toInt()

package com.lisdatasolutions.microrag.domain.model

/** Private storage card of the setup screen (FR-025). */
sealed interface StorageStatus {
    data object Preparing : StorageStatus

    /** Folders ready and enough space; shown as «Offline», as in PNG 02. */
    data object Offline : StorageStatus

    data class NoSpace(val neededBytes: Long) : StorageStatus
}

/** Why «Continuar» is disabled (FR-008). */
enum class ContinueBlocker { MODELS_NOT_CONFIGURED, IN_PROGRESS, NEEDS_ATTENTION, NO_SPACE }

/** Step chip and «Continuar» rules of the single setup screen (FR-006, D-027, data-model §8). */
object SetupStepResolver {

    const val TOTAL_STEPS = 3

    fun step(statuses: Collection<ModelSetupStatus>): Int = when {
        statuses.isNotEmpty() && statuses.all { it is ModelSetupStatus.Ready } -> 3
        statuses.any { it.isUnderway() || it.needsAttention() || it is ModelSetupStatus.NoSpace } -> 2
        else -> 1
    }

    /** Null when «Continuar» can be enabled: every model ready and storage prepared. */
    fun blocker(statuses: Collection<ModelSetupStatus>, storage: StorageStatus): ContinueBlocker? = when {
        storage is StorageStatus.NoSpace || statuses.any { it is ModelSetupStatus.NoSpace } -> ContinueBlocker.NO_SPACE
        statuses.any { it.needsAttention() } -> ContinueBlocker.NEEDS_ATTENTION
        statuses.any { it.isUnderway() } -> ContinueBlocker.IN_PROGRESS
        statuses.isEmpty() || statuses.any { it is ModelSetupStatus.Unconfigured } -> ContinueBlocker.MODELS_NOT_CONFIGURED
        storage is StorageStatus.Preparing -> ContinueBlocker.IN_PROGRESS
        else -> null
    }

    private fun ModelSetupStatus.isUnderway(): Boolean =
        this is ModelSetupStatus.Queued || this is ModelSetupStatus.Downloading ||
            this is ModelSetupStatus.Importing || this is ModelSetupStatus.WaitingForNetwork ||
            this is ModelSetupStatus.Verifying || this == ModelSetupStatus.Preparing

    private fun ModelSetupStatus.needsAttention(): Boolean =
        this is ModelSetupStatus.Failed || this is ModelSetupStatus.Incompatible || this is ModelSetupStatus.Paused
}

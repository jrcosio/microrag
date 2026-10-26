package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.LocalModelWork
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.SetupProblem

/** Everything the setup screen derives model rows from (data-model §2). */
data class SetupSnapshot(
    val files: Map<ModelId, ModelFileState>,
    val preparedBackends: Map<ModelId, ComputeBackend>,
    val partialBytes: Map<ModelId, Long>,
    val download: DownloadProgress,
    val localWork: Map<ModelId, LocalModelWork>,
    val requested: Set<ModelId>,
    val paused: Boolean,
    val hasInternet: Boolean,
)

/**
 * Pure mapping from [SetupSnapshot] to one [ModelSetupStatus] per required model. Rules are applied
 * in priority order; [ModelSetupStatus.Ready] requires a verified file *and* a prepared backend, and
 * no other input can produce it (SC-001).
 */
object ModelSetupStatusResolver {

    fun resolve(required: List<ModelFile>, snapshot: SetupSnapshot): Map<ModelId, ModelSetupStatus> =
        required.associate { file -> file.id to resolve(file, required, snapshot) }

    private fun resolve(file: ModelFile, required: List<ModelFile>, s: SetupSnapshot): ModelSetupStatus {
        val id = file.id
        val total = file.sizeBytes
        val fileState = s.files[id] ?: ModelFileState.Missing
        val work = s.localWork[id]
        val outcome = s.download.outcomes[id]
        val partial = s.partialBytes[id] ?: 0L
        val requested = id in s.requested
        val backend = s.preparedBackends[id]

        if (fileState is ModelFileState.Verified) {
            return when {
                backend != null -> ModelSetupStatus.Ready(backend, total)
                work is LocalModelWork.Rejected -> work.problem.asRejection()
                else -> ModelSetupStatus.Preparing
            }
        }
        if (work is LocalModelWork.Importing) return ModelSetupStatus.Importing(work.bytesDone, work.totalBytes)
        if (fileState is ModelFileState.Verifying) return ModelSetupStatus.Verifying(fileState.bytesDone, fileState.totalBytes)
        if (work is LocalModelWork.Rejected) return work.problem.asRejection()
        if (outcome is DownloadOutcome.NoSpace) return ModelSetupStatus.NoSpace(outcome.neededBytes)
        when {
            fileState is ModelFileState.Mismatch -> return ModelSetupStatus.Failed(SetupProblem.HashMismatch)
            fileState is ModelFileState.Failed -> return ModelSetupStatus.Failed(SetupProblem.Storage)
            outcome is DownloadOutcome.Failed -> return ModelSetupStatus.Failed(outcome.error.asProblem())
        }
        if (requested && s.paused) return ModelSetupStatus.Paused(partial, total)
        if (requested && !s.hasInternet) return ModelSetupStatus.WaitingForNetwork(partial, total)
        if (s.download.active == id) return ModelSetupStatus.Downloading(s.download.bytesDone, total)
        if (requested) {
            val ahead = required.takeWhile { it.id != id }
                .firstOrNull { it.id in s.requested && s.files[it.id] !is ModelFileState.Verified }
            return ModelSetupStatus.Queued(ahead?.id, partial, total)
        }
        return ModelSetupStatus.Unconfigured(partial)
    }

    private fun SetupProblem.asRejection(): ModelSetupStatus = when (this) {
        SetupProblem.WrongFile, SetupProblem.DeviceIncompatible -> ModelSetupStatus.Incompatible(this)
        else -> ModelSetupStatus.Failed(this)
    }

    private fun DownloadError.asProblem(): SetupProblem = when (this) {
        is DownloadError.Http -> SetupProblem.DownloadFailed(code)
        is DownloadError.Network, DownloadError.RangeMismatch -> SetupProblem.DownloadFailed(null)
        is DownloadError.ForbiddenHost, DownloadError.InsecureScheme, DownloadError.TooManyRedirects -> SetupProblem.ForbiddenOrigin
        DownloadError.HashMismatch -> SetupProblem.HashMismatch
        is DownloadError.Io -> SetupProblem.Storage
    }
}

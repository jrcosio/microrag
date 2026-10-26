package com.lisdatasolutions.microrag.domain.onboarding

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.LocalModelWork
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.SetupProblem
import com.lisdatasolutions.microrag.domain.model.SetupRowAction
import com.lisdatasolutions.microrag.domain.model.percentOf
import com.lisdatasolutions.microrag.domain.model.rowAction
import java.io.File
import org.junit.Test

class ModelSetupStatusResolverTest {

    private val required = ModelCatalog.required
    private val eg = ModelId.EG2_270M
    private val gemma = ModelId.GEMMA4_E2B
    private val egSize = ModelCatalog.byId(eg).sizeBytes
    private val gemmaSize = ModelCatalog.byId(gemma).sizeBytes

    private fun snapshot(
        files: Map<ModelId, ModelFileState> = emptyMap(),
        prepared: Map<ModelId, ComputeBackend> = emptyMap(),
        partial: Map<ModelId, Long> = emptyMap(),
        download: DownloadProgress = DownloadProgress(),
        work: Map<ModelId, LocalModelWork> = emptyMap(),
        requested: Set<ModelId> = emptySet(),
        paused: Boolean = false,
        hasInternet: Boolean = true,
    ) = SetupSnapshot(files, prepared, partial, download, work, requested, paused, hasInternet)

    private fun resolve(snapshot: SetupSnapshot) = ModelSetupStatusResolver.resolve(required, snapshot)

    private val verified = ModelFileState.Verified(File("/m"))

    @Test
    fun nothingRequested_bothUnconfigured() {
        assertThat(resolve(snapshot(partial = mapOf(gemma to 42L)))).containsExactly(
            eg, ModelSetupStatus.Unconfigured(0),
            gemma, ModelSetupStatus.Unconfigured(42),
        )
    }

    @Test
    fun requestedQueue_firstDownloadsAndSecondWaitsBehindIt() {
        val result = resolve(
            snapshot(
                requested = setOf(eg, gemma),
                download = DownloadProgress(active = eg, bytesDone = 10, totalBytes = egSize),
                partial = mapOf(gemma to 5L),
            ),
        )

        assertThat(result[eg]).isEqualTo(ModelSetupStatus.Downloading(10, egSize))
        assertThat(result[gemma]).isEqualTo(ModelSetupStatus.Queued(after = eg, bytesDone = 5, totalBytes = gemmaSize))
    }

    @Test
    fun headOfQueueWithoutRunningJob_isQueuedWithNothingAhead() {
        val result = resolve(snapshot(files = mapOf(eg to verified), prepared = mapOf(eg to ComputeBackend.CPU), requested = setOf(gemma), partial = mapOf(gemma to 7L)))

        assertThat(result[gemma]).isEqualTo(ModelSetupStatus.Queued(after = null, bytesDone = 7, totalBytes = gemmaSize))
    }

    @Test
    fun pauseAndMissingNetwork_winOverTheRunningDownload() {
        val running = DownloadProgress(active = gemma, bytesDone = 9, totalBytes = gemmaSize)
        assertThat(resolve(snapshot(requested = setOf(gemma), download = running, paused = true, partial = mapOf(gemma to 9L)))[gemma])
            .isEqualTo(ModelSetupStatus.Paused(9, gemmaSize))
        assertThat(resolve(snapshot(requested = setOf(gemma), download = running, hasInternet = false, partial = mapOf(gemma to 9L)))[gemma])
            .isEqualTo(ModelSetupStatus.WaitingForNetwork(9, gemmaSize))
    }

    @Test
    fun verifiedWithoutBackend_isPreparing_andRejectedTestIsIncompatible() {
        assertThat(resolve(snapshot(files = mapOf(gemma to verified)))[gemma]).isEqualTo(ModelSetupStatus.Preparing)
        assertThat(resolve(snapshot(files = mapOf(gemma to verified), work = mapOf(gemma to LocalModelWork.Rejected(SetupProblem.DeviceIncompatible))))[gemma])
            .isEqualTo(ModelSetupStatus.Incompatible(SetupProblem.DeviceIncompatible))
    }

    @Test
    fun failuresAndSpace_mapToTheirStates() {
        assertThat(resolve(snapshot(files = mapOf(eg to ModelFileState.Mismatch("a", "b")), requested = setOf(eg)))[eg])
            .isEqualTo(ModelSetupStatus.Failed(SetupProblem.HashMismatch))
        assertThat(resolve(snapshot(download = DownloadProgress(outcomes = mapOf(eg to DownloadOutcome.Failed(eg, DownloadError.Http(503)))), requested = setOf(eg)))[eg])
            .isEqualTo(ModelSetupStatus.Failed(SetupProblem.DownloadFailed(503)))
        assertThat(resolve(snapshot(download = DownloadProgress(outcomes = mapOf(eg to DownloadOutcome.Failed(eg, DownloadError.ForbiddenHost("x.com")))), requested = setOf(eg)))[eg])
            .isEqualTo(ModelSetupStatus.Failed(SetupProblem.ForbiddenOrigin))
        assertThat(resolve(snapshot(download = DownloadProgress(outcomes = mapOf(gemma to DownloadOutcome.NoSpace(gemma, 99))), requested = setOf(gemma)))[gemma])
            .isEqualTo(ModelSetupStatus.NoSpace(99))
        assertThat(resolve(snapshot(work = mapOf(eg to LocalModelWork.Rejected(SetupProblem.WrongFile))))[eg])
            .isEqualTo(ModelSetupStatus.Incompatible(SetupProblem.WrongFile))
    }

    @Test
    fun importAndVerification_showRealBytes() {
        assertThat(resolve(snapshot(work = mapOf(eg to LocalModelWork.Importing(3, egSize)), files = mapOf(eg to ModelFileState.Verifying(3, egSize))))[eg])
            .isEqualTo(ModelSetupStatus.Importing(3, egSize))
        assertThat(resolve(snapshot(files = mapOf(eg to ModelFileState.Verifying(4, egSize))))[eg])
            .isEqualTo(ModelSetupStatus.Verifying(4, egSize))
    }

    /**
     * SC-001, exhaustive: across every combination of inputs, a row is Ready if and only if the file
     * is hash-verified and the on-device test recorded a backend.
     */
    @Test
    fun ready_onlyWithVerifiedFileAndPreparedBackend() {
        val fileStates = listOf(
            ModelFileState.Missing, ModelFileState.Unverified(File("/m")), ModelFileState.Verifying(1, 2),
            verified, ModelFileState.Mismatch("a", "b"), ModelFileState.Failed("x"),
        )
        val backends = listOf(null, ComputeBackend.GPU, ComputeBackend.CPU)
        val outcomes = listOf(
            null, DownloadOutcome.Completed(gemma), DownloadOutcome.NoSpace(gemma, 1),
            DownloadOutcome.Failed(gemma, DownloadError.HashMismatch), DownloadOutcome.Cancelled(gemma),
        )
        val works = listOf(null, LocalModelWork.Preparing, LocalModelWork.Importing(1, 2), LocalModelWork.Rejected(SetupProblem.WrongFile))
        var combinations = 0
        for (fileState in fileStates) for (backend in backends) for (outcome in outcomes) for (work in works)
            for (active in listOf(false, true)) for (requested in listOf(false, true)) for (paused in listOf(false, true))
                for (internet in listOf(false, true)) {
                    val status = resolve(
                        snapshot(
                            files = mapOf(gemma to fileState),
                            prepared = backend?.let { mapOf(gemma to it) }.orEmpty(),
                            download = DownloadProgress(
                                active = gemma.takeIf { active },
                                outcomes = outcome?.let { mapOf(gemma to it) }.orEmpty(),
                            ),
                            work = work?.let { mapOf(gemma to it) }.orEmpty(),
                            requested = if (requested) setOf(gemma) else emptySet(),
                            paused = paused,
                            hasInternet = internet,
                        ),
                    ).getValue(gemma)
                    val shouldBeReady = fileState is ModelFileState.Verified && backend != null
                    assertWithMessage("$fileState $backend $outcome $work active=$active req=$requested")
                        .that(status is ModelSetupStatus.Ready).isEqualTo(shouldBeReady)
                    combinations++
                }
        assertThat(combinations).isEqualTo(6 * 3 * 5 * 4 * 2 * 2 * 2 * 2)
    }

    @Test
    fun rowActions_followTheContract() {
        assertThat(ModelSetupStatus.Unconfigured().rowAction()).isEqualTo(SetupRowAction.OPEN_CONFIRMATION)
        assertThat(ModelSetupStatus.Incompatible(SetupProblem.WrongFile).rowAction()).isEqualTo(SetupRowAction.OPEN_CONFIRMATION)
        assertThat(ModelSetupStatus.Downloading(1, 2).rowAction()).isEqualTo(SetupRowAction.PAUSE)
        assertThat(ModelSetupStatus.Queued(null, 0, 2).rowAction()).isEqualTo(SetupRowAction.PAUSE)
        assertThat(ModelSetupStatus.WaitingForNetwork(1, 2).rowAction()).isEqualTo(SetupRowAction.PAUSE)
        assertThat(ModelSetupStatus.Paused(1, 2).rowAction()).isEqualTo(SetupRowAction.RESUME)
        assertThat(ModelSetupStatus.Failed(SetupProblem.HashMismatch).rowAction()).isEqualTo(SetupRowAction.RETRY)
        assertThat(ModelSetupStatus.NoSpace(1).rowAction()).isEqualTo(SetupRowAction.OPEN_STORAGE_SETTINGS)
        listOf(ModelSetupStatus.Importing(1, 2), ModelSetupStatus.Verifying(1, 2), ModelSetupStatus.Preparing, ModelSetupStatus.Ready(ComputeBackend.GPU, 1))
            .forEach { assertThat(it.rowAction()).isEqualTo(SetupRowAction.NONE) }
    }

    @Test
    fun percent_isFlooredAndCapped() {
        assertThat(percentOf(0, 0)).isEqualTo(0)
        assertThat(percentOf(999, 1000)).isEqualTo(99)
        assertThat(percentOf(1000, 1000)).isEqualTo(100)
        assertThat(percentOf(2000, 1000)).isEqualTo(100)
        assertThat(percentOf(1_294_073_856, 2_588_147_712)).isEqualTo(50)
    }
}

package com.lisdatasolutions.microrag.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SetupStepResolverTest {

    private val ready = ModelSetupStatus.Ready(ComputeBackend.GPU, 1)
    private val unconfigured = ModelSetupStatus.Unconfigured()

    @Test
    fun step_followsTheRealState() {
        assertThat(SetupStepResolver.step(listOf(unconfigured, unconfigured))).isEqualTo(1)
        assertThat(SetupStepResolver.step(listOf(ready, unconfigured))).isEqualTo(1)
        assertThat(SetupStepResolver.step(listOf(ModelSetupStatus.Downloading(1, 2), unconfigured))).isEqualTo(2)
        assertThat(SetupStepResolver.step(listOf(ModelSetupStatus.Failed(SetupProblem.HashMismatch), ready))).isEqualTo(2)
        assertThat(SetupStepResolver.step(listOf(ModelSetupStatus.NoSpace(1), unconfigured))).isEqualTo(2)
        assertThat(SetupStepResolver.step(listOf(ModelSetupStatus.Preparing, ready))).isEqualTo(2)
        assertThat(SetupStepResolver.step(listOf(ready, ready))).isEqualTo(3)
    }

    @Test
    fun blocker_explainsWhyContinueIsDisabled() {
        val offline = StorageStatus.Offline
        assertThat(SetupStepResolver.blocker(listOf(unconfigured, unconfigured), offline)).isEqualTo(ContinueBlocker.MODELS_NOT_CONFIGURED)
        assertThat(SetupStepResolver.blocker(listOf(ModelSetupStatus.Downloading(1, 2), unconfigured), offline)).isEqualTo(ContinueBlocker.IN_PROGRESS)
        assertThat(SetupStepResolver.blocker(listOf(ModelSetupStatus.Paused(1, 2), ready), offline)).isEqualTo(ContinueBlocker.NEEDS_ATTENTION)
        assertThat(SetupStepResolver.blocker(listOf(ModelSetupStatus.Incompatible(SetupProblem.WrongFile), ready), offline))
            .isEqualTo(ContinueBlocker.NEEDS_ATTENTION)
        assertThat(SetupStepResolver.blocker(listOf(ModelSetupStatus.NoSpace(1), unconfigured), offline)).isEqualTo(ContinueBlocker.NO_SPACE)
        assertThat(SetupStepResolver.blocker(listOf(ready, ready), StorageStatus.NoSpace(1))).isEqualTo(ContinueBlocker.NO_SPACE)
        assertThat(SetupStepResolver.blocker(listOf(ready, ready), StorageStatus.Preparing)).isEqualTo(ContinueBlocker.IN_PROGRESS)
    }

    @Test
    fun continueIsEnabled_onlyWithBothReadyAndStorageOffline() {
        assertThat(SetupStepResolver.blocker(listOf(ready, ready), StorageStatus.Offline)).isNull()
        assertThat(SetupStepResolver.blocker(listOf(ready, ModelSetupStatus.Preparing), StorageStatus.Offline)).isNotNull()
        assertThat(SetupStepResolver.blocker(emptyList(), StorageStatus.Offline)).isNotNull()
    }
}

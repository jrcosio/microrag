package com.lisdatasolutions.microrag.feature.onboarding.setup

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ContinueBlocker
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.SetupProblem
import com.lisdatasolutions.microrag.domain.model.StorageStatus
import com.lisdatasolutions.microrag.domain.model.percentOf

// Model status → texts of contracts/onboarding-ui.md. Pure mapping: no state.

@StringRes
fun ModelId.nameRes(): Int = when (this) {
    ModelId.GEMMA4_E2B, ModelId.GEMMA4_E2B_GPU -> R.string.setup_model_gemma_name
    else -> R.string.setup_model_embedding_name
}

@StringRes
fun ModelId.purposeRes(): Int = when (this) {
    ModelId.GEMMA4_E2B, ModelId.GEMMA4_E2B_GPU -> R.string.setup_model_gemma_purpose
    else -> R.string.setup_model_embedding_purpose
}

@DrawableRes
fun ModelId.iconRes(): Int = when (this) {
    ModelId.GEMMA4_E2B, ModelId.GEMMA4_E2B_GPU -> LisdataIcons.Sparkle
    else -> LisdataIcons.Layers
}

@Composable
fun bytesText(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

fun ModelSetupStatus.statusKind(): StatusKind = when (this) {
    is ModelSetupStatus.Unconfigured -> StatusKind.Warning
    is ModelSetupStatus.Queued -> StatusKind.Queued
    is ModelSetupStatus.Downloading, is ModelSetupStatus.Importing, is ModelSetupStatus.Verifying,
    ModelSetupStatus.Preparing,
    -> StatusKind.Processing
    is ModelSetupStatus.Paused -> StatusKind.Paused
    is ModelSetupStatus.WaitingForNetwork, is ModelSetupStatus.NoSpace -> StatusKind.Warning
    is ModelSetupStatus.Ready -> StatusKind.Ready
    is ModelSetupStatus.Incompatible, is ModelSetupStatus.Failed -> StatusKind.Error
}

fun ModelSetupStatus.progress(): ProgressValue? = when (this) {
    is ModelSetupStatus.Downloading -> fraction(bytesDone, totalBytes)
    is ModelSetupStatus.Importing -> fraction(bytesDone, totalBytes)
    is ModelSetupStatus.Paused -> fraction(bytesDone, totalBytes)
    is ModelSetupStatus.WaitingForNetwork -> fraction(bytesDone, totalBytes)
    is ModelSetupStatus.Verifying -> fraction(bytesDone, totalBytes)
    ModelSetupStatus.Preparing -> ProgressValue.Indeterminate
    else -> null
}

private fun fraction(done: Long, total: Long) = ProgressValue.Determinate(percentOf(done, total) / 100f)

@Composable
fun ModelSetupStatus.chipText(): String = when (this) {
    is ModelSetupStatus.Unconfigured -> stringResource(R.string.model_status_unconfigured)
    is ModelSetupStatus.Queued -> stringResource(R.string.model_status_queued)
    is ModelSetupStatus.Downloading -> stringResource(R.string.model_status_downloading, percentOf(bytesDone, totalBytes))
    is ModelSetupStatus.Importing -> stringResource(R.string.model_status_importing, percentOf(bytesDone, totalBytes))
    is ModelSetupStatus.Paused -> stringResource(R.string.model_status_paused)
    is ModelSetupStatus.WaitingForNetwork -> stringResource(R.string.model_status_waiting_network)
    is ModelSetupStatus.Verifying -> stringResource(R.string.model_status_verifying)
    ModelSetupStatus.Preparing -> stringResource(R.string.model_status_preparing)
    is ModelSetupStatus.Ready -> stringResource(R.string.model_status_ready)
    is ModelSetupStatus.NoSpace -> stringResource(R.string.model_status_no_space)
    is ModelSetupStatus.Incompatible -> stringResource(R.string.model_status_incompatible)
    is ModelSetupStatus.Failed -> stringResource(R.string.model_status_failed)
}

@Composable
fun ModelSetupStatus.detailText(id: ModelId): String? = when (this) {
    is ModelSetupStatus.Unconfigured -> null
    is ModelSetupStatus.Queued -> when {
        after != null -> stringResource(R.string.model_detail_queued_after, stringResource(after.nameRes()))
        bytesDone > 0 -> stringResource(R.string.model_detail_bytes, bytesText(bytesDone), bytesText(totalBytes))
        else -> null
    }
    is ModelSetupStatus.Downloading -> stringResource(R.string.model_detail_bytes, bytesText(bytesDone), bytesText(totalBytes))
    is ModelSetupStatus.Importing -> stringResource(R.string.model_detail_bytes, bytesText(bytesDone), bytesText(totalBytes))
    is ModelSetupStatus.Paused -> stringResource(R.string.model_detail_paused, bytesText(bytesDone), bytesText(totalBytes))
    is ModelSetupStatus.WaitingForNetwork -> stringResource(R.string.model_detail_bytes, bytesText(bytesDone), bytesText(totalBytes))
    is ModelSetupStatus.Verifying -> stringResource(R.string.model_detail_verifying, percentOf(bytesDone, totalBytes))
    ModelSetupStatus.Preparing -> stringResource(R.string.model_detail_preparing)
    is ModelSetupStatus.Ready ->
        if (backend == ComputeBackend.CPU) stringResource(R.string.model_detail_ready_cpu, bytesText(sizeBytes)) else bytesText(sizeBytes)
    is ModelSetupStatus.NoSpace -> stringResource(R.string.model_detail_no_space, bytesText(neededBytes))
    is ModelSetupStatus.Incompatible -> stringResource(R.string.model_detail_incompatible, problem.text(id))
    is ModelSetupStatus.Failed -> stringResource(R.string.model_detail_failed, problem.text(id))
}

@Composable
fun SetupProblem.text(id: ModelId): String = when (this) {
    SetupProblem.HashMismatch -> stringResource(R.string.model_reason_hash_mismatch)
    is SetupProblem.DownloadFailed -> stringResource(
        R.string.model_reason_download_failed,
        httpCode?.let { "HTTP $it" } ?: stringResource(R.string.model_reason_network),
    )
    SetupProblem.ForbiddenOrigin -> stringResource(R.string.model_reason_forbidden_origin)
    SetupProblem.Storage -> stringResource(R.string.model_reason_storage)
    SetupProblem.WrongFile -> stringResource(R.string.model_reason_wrong_file, stringResource(id.nameRes()))
    SetupProblem.DeviceIncompatible -> stringResource(R.string.model_reason_device_incompatible)
}

/**
 * Verbal status for TalkBack. Percentages move in steps of 10 so the live region does not flood
 * announcements (contracts/onboarding-ui.md § Accesibilidad).
 */
@Composable
fun ModelSetupStatus.accessibilityText(id: ModelId): String {
    val verbal = when (this) {
        is ModelSetupStatus.Unconfigured -> stringResource(R.string.a11y_status_unconfigured)
        is ModelSetupStatus.Queued -> stringResource(R.string.a11y_status_queued)
        is ModelSetupStatus.Downloading -> tens(percentOf(bytesDone, totalBytes)).let {
            pluralStringResource(R.plurals.a11y_status_downloading, it, it)
        }
        is ModelSetupStatus.Importing -> tens(percentOf(bytesDone, totalBytes)).let {
            pluralStringResource(R.plurals.a11y_status_importing, it, it)
        }
        is ModelSetupStatus.Paused -> stringResource(R.string.a11y_status_paused, bytesText(bytesDone), bytesText(totalBytes))
        is ModelSetupStatus.WaitingForNetwork ->
            stringResource(R.string.a11y_status_waiting_network, bytesText(bytesDone), bytesText(totalBytes))
        is ModelSetupStatus.Verifying -> tens(percentOf(bytesDone, totalBytes)).let {
            pluralStringResource(R.plurals.a11y_status_verifying, it, it)
        }
        ModelSetupStatus.Preparing -> stringResource(R.string.a11y_status_preparing)
        is ModelSetupStatus.Ready ->
            stringResource(if (backend == ComputeBackend.CPU) R.string.a11y_status_ready_cpu else R.string.a11y_status_ready)
        is ModelSetupStatus.NoSpace -> stringResource(R.string.a11y_status_no_space, bytesText(neededBytes))
        is ModelSetupStatus.Incompatible -> stringResource(R.string.a11y_status_incompatible, problem.text(id))
        is ModelSetupStatus.Failed -> stringResource(R.string.a11y_status_failed, problem.text(id))
    }
    return stringResource(R.string.a11y_model_row, stringResource(id.nameRes()), stringResource(id.purposeRes()), verbal)
}

private fun tens(percent: Int) = percent / 10 * 10

@Composable
fun ContinueBlocker.text(): String = stringResource(
    when (this) {
        ContinueBlocker.MODELS_NOT_CONFIGURED -> R.string.setup_blocker_models
        ContinueBlocker.IN_PROGRESS -> R.string.setup_blocker_in_progress
        ContinueBlocker.NEEDS_ATTENTION -> R.string.setup_blocker_attention
        ContinueBlocker.NO_SPACE -> R.string.setup_blocker_no_space
    },
)

@Composable
fun StorageStatus.chipText(): String = stringResource(
    when (this) {
        StorageStatus.Offline -> R.string.setup_storage_offline
        is StorageStatus.NoSpace -> R.string.setup_storage_no_space
        StorageStatus.Preparing -> R.string.setup_storage_preparing
    },
)

fun StorageStatus.statusKind(): StatusKind = when (this) {
    StorageStatus.Offline -> StatusKind.Ready
    is StorageStatus.NoSpace -> StatusKind.Warning
    StorageStatus.Preparing -> StatusKind.Processing
}

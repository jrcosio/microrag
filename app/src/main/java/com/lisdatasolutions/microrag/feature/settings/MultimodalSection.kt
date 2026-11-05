package com.lisdatasolutions.microrag.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataLinearProgress
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSettingsRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSwitchRow
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.feature.ingest.summary
import com.lisdatasolutions.microrag.feature.onboarding.setup.bytesText

/** What the «Modo multimodal» block asks for; [MultimodalSettingsViewModel] decides what happens. */
data class MultimodalActions(
    val onToggle: (Boolean) -> Unit = {},
    val onConfirmEnable: () -> Unit = {},
    val onConfirmDisable: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onAction: (MultimodalAction) -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    /** U5: «Modo al importar PDF», only with the mode on. */
    val onImportMode: () -> Unit = {},
)

/**
 * The switch under the model rows (wireframe §1–§2, contract U2): its subtitle is the stage, so TalkBack reads
 * the stage with the switch, and under it the download's real bar and the stage's buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultimodalSection(state: MultimodalUiState, actions: MultimodalActions, modifier: Modifier = Modifier) {
    val spacing = LisdataTheme.spacing
    Column(modifier = modifier.testTag("multimodal_status"), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        LisdataSwitchRow(
            title = stringResource(R.string.multimodal_title),
            checked = state.switchOn,
            onCheckedChange = actions.onToggle,
            subtitle = state.statusText(),
            enabled = state.switchEnabled,
            modifier = Modifier.testTag("multimodal_switch"),
        )
        if (state.showsProgress) {
            LisdataLinearProgress(state.percent?.let { ProgressValue.Determinate(it / 100f) } ?: ProgressValue.Indeterminate)
        }
        // U5 (wireframe §1): the default mode of the imports, with the mode on.
        if (state.stage == MultimodalStage.ON) {
            LisdataCardGroup {
                LisdataSettingsRow(
                    icon = LisdataIcons.Layers,
                    title = stringResource(R.string.import_mode_title),
                    subtitle = stringResource(state.defaultMode.summary()),
                    onClick = actions.onImportMode,
                    modifier = Modifier.testTag("import_mode_row"),
                )
            }
        }
        if (state.actions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space2), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                state.actions.forEach { action ->
                    LisdataOutlinedButton(text = stringResource(action.label()), onClick = { actions.onAction(action) }, compact = true)
                }
            }
        }
    }
}

/** The two confirmations (wireframe §2 and §3, contracts U3 and U4), neither destructive. */
@Composable
fun MultimodalDialogs(state: MultimodalUiState, actions: MultimodalActions) {
    when (state.dialog) {
        MultimodalDialog.ENABLE -> {
            val lines = listOf(
                stringResource(R.string.multimodal_enable_download, bytesText(state.downloadBytes)),
                stringResource(R.string.multimodal_enable_pages),
                stringResource(R.string.multimodal_enable_answers),
                stringResource(R.string.multimodal_enable_text),
            )
            val body = stringResource(R.string.multimodal_enable_body)
            LisdataConfirmationDialog(
                title = stringResource(R.string.multimodal_enable_title),
                body = body,
                confirmText = stringResource(R.string.multimodal_enable_confirm),
                onConfirm = actions.onConfirmEnable,
                onDismiss = actions.onDismissDialog,
                destructive = false,
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
                        DialogText(body)
                        lines.forEach { DialogText("• $it") }
                    }
                },
            )
        }
        MultimodalDialog.DISABLE -> LisdataConfirmationDialog(
            title = stringResource(R.string.multimodal_disable_title),
            body = stringResource(R.string.multimodal_disable_body),
            confirmText = stringResource(R.string.multimodal_disable_confirm),
            onConfirm = actions.onConfirmDisable,
            onDismiss = actions.onDismissDialog,
            destructive = false,
        )
        null -> Unit
    }
}

/** U7: «Modo multimodal activado» once, in the snackbar TalkBack reads, consumed as soon as it arrives. */
@Composable
fun MultimodalNotice(show: Boolean, snackbar: SnackbarHostState, onShown: () -> Unit) {
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        showing = true
        onShown()
    }
    val text = stringResource(R.string.multimodal_enabled_snackbar)
    LaunchedEffect(showing) {
        if (!showing) return@LaunchedEffect
        snackbar.showSnackbar(text)
        showing = false
    }
}

@Composable
private fun DialogText(text: String) {
    Text(text = text, style = LisdataTheme.typography.material.bodyMedium, color = LisdataTheme.colors.textSecondary)
}

@Composable
private fun MultimodalUiState.statusText(): String = when (stage) {
    MultimodalStage.DOWNLOADING -> when {
        error == MultimodalError.NO_SPACE -> stringResource(R.string.multimodal_error_no_space, bytesText(missingBytes ?: 0L))
        error != null -> stringResource(R.string.multimodal_error_download)
        paused -> stringResource(R.string.multimodal_stage_paused)
        offline -> stringResource(R.string.multimodal_error_offline)
        else -> stringResource(R.string.multimodal_stage_downloading, percent ?: 0)
    }
    MultimodalStage.VALIDATING -> stringResource(R.string.multimodal_stage_validating)
    MultimodalStage.SWITCHING -> stringResource(R.string.multimodal_stage_switching)
    MultimodalStage.OFF -> when (error) {
        MultimodalError.IDENTITY_MISMATCH -> stringResource(R.string.multimodal_error_identity)
        MultimodalError.PREPARE_FAILED -> stringResource(R.string.multimodal_error_prepare)
        else -> stringResource(R.string.multimodal_subtitle)
    }
    MultimodalStage.ON -> stringResource(R.string.multimodal_subtitle)
}

private fun MultimodalAction.label(): Int = when (this) {
    MultimodalAction.PAUSE -> R.string.multimodal_action_pause
    MultimodalAction.RESUME -> R.string.multimodal_action_resume
    MultimodalAction.RETRY -> R.string.multimodal_action_retry
    MultimodalAction.CANCEL -> R.string.multimodal_action_cancel
    MultimodalAction.OK -> R.string.multimodal_action_ok
}

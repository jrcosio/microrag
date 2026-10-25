package com.lisdatasolutions.microrag.debug.feasibility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataModelStatusRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import org.koin.compose.viewmodel.koinViewModel

/** «MicroRAG Debug → Viabilidad on-device»: internal tool, never part of the product UI (FR-020). */
@Composable
fun FeasibilityScreen(onBack: () -> Unit, viewModel: FeasibilityViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FeasibilityContent(
        state = state,
        onBack = onBack,
        onImport = viewModel::importDevModels,
        onVerify = viewModel::verifyAgain,
    )
}

@Composable
internal fun FeasibilityContent(
    state: FeasibilityUiState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onVerify: () -> Unit,
) {
    val spacing = LisdataTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LisdataTheme.colors.background)
            .navigationBarsPadding(),
    ) {
        LisdataTopBar(title = stringResource(R.string.feasibility_title), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
        ) {
            item { LisdataSectionHeader(stringResource(R.string.feasibility_section_models)) }
            if (state.noModelsAtAll) {
                item {
                    LisdataEmptyState(
                        icon = LisdataIcons.Cpu,
                        title = stringResource(R.string.feasibility_empty_title),
                        body = stringResource(R.string.feasibility_empty_body),
                    )
                }
            }
            items(state.models, key = { it.file.id }) { row -> ModelRowItem(row) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    LisdataGradientButton(
                        text = stringResource(R.string.feasibility_action_import),
                        onClick = onImport,
                        enabled = state.canImport,
                        loading = state.modelsBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LisdataOutlinedButton(
                        text = stringResource(R.string.feasibility_action_verify),
                        onClick = onVerify,
                        enabled = !state.modelsBusy,
                        leadingIcon = LisdataIcons.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRowItem(row: ModelRow) {
    val file = row.file
    val (statusText, kind) = statusOf(row)
    LisdataModelStatusRow(
        icon = if (file.family == ModelFamily.LLM) LisdataIcons.Sparkle else LisdataIcons.Layers,
        name = file.displayName,
        purpose = stringResource(
            R.string.feasibility_model_meta,
            file.fileName,
            file.sizeBytes / BYTES_PER_MB,
            file.sha256.take(SHORT_HASH),
        ),
        statusText = statusText,
        status = kind,
        progress = (row.state as? ModelFileState.Verifying)?.let { ProgressValue.Determinate(it.fraction()) },
    )
}

@Composable
private fun statusOf(row: ModelRow): Pair<String, StatusKind> = when (val state = row.state) {
    ModelFileState.Missing -> if (row.devCopyBytes != null) {
        stringResource(R.string.feasibility_state_pending) to StatusKind.Queued
    } else {
        stringResource(R.string.feasibility_state_missing) to StatusKind.Warning
    }
    is ModelFileState.Unverified -> stringResource(R.string.feasibility_state_unverified) to StatusKind.Info
    is ModelFileState.Verifying ->
        stringResource(R.string.feasibility_state_verifying, (state.fraction() * 100).toInt()) to StatusKind.Processing
    is ModelFileState.Verified -> stringResource(R.string.feasibility_state_verified) to StatusKind.Ready
    is ModelFileState.Mismatch -> stringResource(R.string.feasibility_state_mismatch) to StatusKind.Error
    is ModelFileState.Failed -> stringResource(R.string.feasibility_state_failed, state.reason) to StatusKind.Error
}

private fun ModelFileState.Verifying.fraction(): Float =
    if (totalBytes <= 0) 0f else (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f)

private const val BYTES_PER_MB = 1024L * 1024L
private const val SHORT_HASH = 12

@LisdataPreviews
@Composable
private fun FeasibilityContentPreview() {
    val states = mapOf(
        ModelId.GEMMA4_E2B to ModelFileState.Verifying(bytesDone = 1_100_000_000, totalBytes = 2_588_147_712),
        ModelId.GEMMA4_E2B_GPU to ModelFileState.Verified(File("/models/gemma-4-E2B-it-gpu.litertlm")),
        ModelId.EG2_740M to ModelFileState.Mismatch("e7a8", "0000"),
        ModelId.EG2_440M to ModelFileState.Missing,
        ModelId.EG2_270M to ModelFileState.Unverified(File("/models/embeddinggemma-2-text-270m.litertlm")),
    )
    LisdataTheme {
        FeasibilityContent(
            state = FeasibilityUiState(
                models = ModelCatalog.all.map { ModelRow(it, states.getValue(it.id), if (it.id == ModelId.EG2_440M) 1L else null) },
            ),
            onBack = {},
            onImport = {},
            onVerify = {},
        )
    }
}

@LisdataPreviews
@Composable
private fun FeasibilityEmptyPreview() {
    LisdataTheme {
        FeasibilityContent(
            state = FeasibilityUiState(models = ModelCatalog.all.map { ModelRow(it, ModelFileState.Missing) }),
            onBack = {},
            onImport = {},
            onVerify = {},
        )
    }
}

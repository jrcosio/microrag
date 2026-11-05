package com.lisdatasolutions.microrag.debug.feasibility.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataLinearProgress
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataModelStatusRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatusChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.debug.feasibility.ModelRow
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.DeviceSetSummary
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualKind
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualMetrics
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualParams
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualScenario
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualStatus
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

/** Actions of the visual diagnostics screen, so the content stays stateless and previewable. */
data class VisualFeasibilityActions(
    val onBack: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onRun: (Set<VisualBlock>) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onThermalAnswer: (Boolean) -> Unit = {},
)

/** «MicroRAG Debug → Viabilidad visual»: internal F18 tool, never part of the product UI. */
@Composable
fun VisualFeasibilityScreen(onBack: () -> Unit, viewModel: VisualFeasibilityViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // FR-017: leaving the screen or sending the app to the background cancels and frees engines.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    val view = LocalView.current
    DisposableEffect(state.isRunning) {
        view.keepScreenOn = state.isRunning
        onDispose { view.keepScreenOn = false }
    }
    VisualFeasibilityContent(
        state = state,
        actions = VisualFeasibilityActions(
            onBack = {
                viewModel.onStop()
                onBack()
            },
            onImport = viewModel::importDevModels,
            onReload = viewModel::reloadDeviceSet,
            onRun = { viewModel.run(it) },
            onCancel = viewModel::cancelRun,
            onThermalAnswer = viewModel::answerThermal,
        ),
    )
}

@Composable
internal fun VisualFeasibilityContent(state: VisualFeasibilityUiState, actions: VisualFeasibilityActions) {
    val spacing = LisdataTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LisdataTheme.colors.background)
            .navigationBarsPadding(),
    ) {
        LisdataTopBar(title = stringResource(R.string.visual_title), onBack = actions.onBack)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
        ) {
            modelsSection(state, actions)
            deviceSetSection(state, actions)
            testsSection(state, actions)
            resultsSection(state)
        }
    }
    state.thermalPrompt?.let { level ->
        LisdataConfirmationDialog(
            title = stringResource(R.string.feasibility_thermal_title),
            body = stringResource(R.string.feasibility_thermal_body, level.name),
            confirmText = stringResource(R.string.feasibility_thermal_confirm),
            dismissText = stringResource(R.string.feasibility_thermal_skip),
            destructive = false,
            onConfirm = { actions.onThermalAnswer(true) },
            onDismiss = { actions.onThermalAnswer(false) },
        )
    }
}

private fun LazyListScope.modelsSection(state: VisualFeasibilityUiState, actions: VisualFeasibilityActions) {
    item { LisdataSectionHeader(stringResource(R.string.visual_section_models)) }
    items(state.models, key = { it.file.id }) { row -> ModelRowItem(row) }
    item {
        LisdataGradientButton(
            text = stringResource(R.string.feasibility_action_import),
            onClick = actions.onImport,
            enabled = state.canImport,
            loading = state.modelsBusy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun LazyListScope.deviceSetSection(state: VisualFeasibilityUiState, actions: VisualFeasibilityActions) {
    item { LisdataSectionHeader(stringResource(R.string.visual_section_set)) }
    item {
        val set = state.deviceSet
        if (!set.present) {
            LisdataEmptyState(
                icon = LisdataIcons.Layers,
                title = stringResource(R.string.visual_set_absent_title),
                body = stringResource(R.string.visual_set_absent_body),
            )
        } else {
            LisdataCard {
                BodyText(pluralStringResource(R.plurals.visual_set_verified, set.verified, set.verified))
                if (set.rejected.isNotEmpty()) {
                    BodyText(stringResource(R.string.visual_set_rejected, set.rejected.joinToString()), secondary = true)
                }
            }
        }
    }
    item {
        LisdataOutlinedButton(
            text = stringResource(R.string.visual_action_reload),
            onClick = actions.onReload,
            enabled = state.canReload,
            leadingIcon = LisdataIcons.Refresh,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun LazyListScope.testsSection(state: VisualFeasibilityUiState, actions: VisualFeasibilityActions) {
    item { LisdataSectionHeader(stringResource(R.string.visual_section_tests)) }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
            if (state.isRunning) {
                state.progress?.let { ProgressCard(it) }
                LisdataOutlinedButton(
                    text = stringResource(R.string.feasibility_action_cancel),
                    onClick = actions.onCancel,
                    destructive = true,
                    leadingIcon = LisdataIcons.Close,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LisdataGradientButton(
                    text = stringResource(R.string.visual_action_all),
                    onClick = { actions.onRun(VisualBlock.entries.filter(state::canRun).toSet()) },
                    enabled = state.canRunAll,
                    modifier = Modifier.fillMaxWidth(),
                )
                VisualBlock.entries.forEach { block ->
                    LisdataOutlinedButton(
                        text = stringResource(blockText(block)),
                        onClick = { actions.onRun(setOf(block)) },
                        enabled = state.canRun(block),
                        leadingIcon = LisdataIcons.Play,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    needsText(state.needs(block))?.let { BodyText(it, secondary = true) }
                }
            }
            state.runError?.let { BodyText(stringResource(R.string.visual_run_error, it)) }
            state.lastReportPath?.let { BodyText(stringResource(R.string.visual_report_path, it), secondary = true) }
        }
    }
}

private fun LazyListScope.resultsSection(state: VisualFeasibilityUiState) {
    item { LisdataSectionHeader(stringResource(R.string.visual_section_results)) }
    if (state.results.isEmpty()) {
        item { BodyText(stringResource(R.string.visual_results_empty)) }
    }
    itemsIndexed(state.results, key = { index, _ -> "result-$index" }) { _, result -> ResultCard(result) }
}

@Composable
private fun ProgressCard(progress: VisualProgress) {
    LisdataCard {
        BodyText(progress.label)
        // Unknown duration (loads, thermal wait) shows an indeterminate bar (constitution V).
        LisdataLinearProgress(
            if (progress.total > 0) ProgressValue.Determinate(progress.step.toFloat() / progress.total) else ProgressValue.Indeterminate,
        )
    }
}

@Composable
private fun ResultCard(result: VisualScenario) {
    LisdataCard {
        BodyText(title(result))
        val (statusText, kind) = statusOf(result.status)
        LisdataStatusChip(text = stringResource(statusText), kind = kind)
        result.metrics?.let { MetricLines(it) }
        result.reason?.let { BodyText(stringResource(R.string.visual_metric_reason, it), secondary = true) }
        BodyText(
            stringResource(R.string.visual_metric_context, result.peakRssMb?.toString() ?: "—", result.thermalStart.name, result.thermalEnd.name),
            secondary = true,
        )
    }
}

@Composable
private fun MetricLines(m: VisualMetrics) {
    if (m.p50Ms != null && m.p95Ms != null) {
        BodyText(pluralStringResource(R.plurals.visual_metric_time, m.samples ?: 0, num(m.p50Ms), num(m.p95Ms), m.samples ?: 0))
    }
    m.initColdMs?.let { BodyText(stringResource(R.string.visual_metric_load, it, m.initWarmMs?.toString() ?: "—"), secondary = true) }
    m.prefillTokens?.let { BodyText(pluralStringResource(R.plurals.visual_metric_prefill, it, it), secondary = true) }
    m.bytesP50?.let { BodyText(stringResource(R.string.visual_metric_size, it / BYTES_PER_KB), secondary = true) }
    m.projectedP50S?.let { BodyText(stringResource(R.string.visual_metric_projection, num(it)), secondary = true) }
}

private fun title(result: VisualScenario): String = listOfNotNull(
    result.kind.name,
    result.model?.let { ModelCatalog.byId(it).displayName },
    result.backend?.name,
    result.params.stage?.let { "etapa $it" },
    result.params.combination,
    result.params.visionBackend?.let { "visión $it" },
    result.params.tokensPerImage?.let { "$it tokens" },
    result.params.document,
    result.params.px?.let { "$it px" },
    result.params.format?.name,
).joinToString(" · ")

@Composable
private fun ModelRowItem(row: ModelRow) {
    val file = row.file
    val (statusText, kind) = modelStatusOf(row)
    LisdataModelStatusRow(
        icon = if (file.family == ModelFamily.LLM) LisdataIcons.Sparkle else LisdataIcons.Layers,
        name = file.displayName,
        purpose = stringResource(R.string.feasibility_model_meta, file.fileName, file.sizeBytes / BYTES_PER_MB, file.sha256.take(SHORT_HASH)),
        statusText = statusText,
        status = kind,
    )
}

@Composable
private fun modelStatusOf(row: ModelRow): Pair<String, StatusKind> = when (val state = row.state) {
    ModelFileState.Missing -> if (row.devCopyBytes != null) {
        stringResource(R.string.feasibility_state_pending) to StatusKind.Queued
    } else {
        stringResource(R.string.feasibility_state_missing) to StatusKind.Warning
    }
    is ModelFileState.Unverified -> stringResource(R.string.feasibility_state_unverified) to StatusKind.Info
    is ModelFileState.Verifying -> stringResource(R.string.feasibility_state_verifying, 0) to StatusKind.Processing
    is ModelFileState.Verified -> stringResource(R.string.feasibility_state_verified) to StatusKind.Ready
    is ModelFileState.Mismatch -> stringResource(R.string.feasibility_state_mismatch) to StatusKind.Error
    is ModelFileState.Failed -> stringResource(R.string.feasibility_state_failed, state.reason) to StatusKind.Error
}

private fun statusOf(status: VisualStatus): Pair<Int, StatusKind> = when (status) {
    VisualStatus.COMPLETED -> R.string.visual_status_completed to StatusKind.Ready
    VisualStatus.CANCELLED -> R.string.visual_status_cancelled to StatusKind.Paused
    VisualStatus.NOT_SUPPORTED -> R.string.visual_status_not_supported to StatusKind.Info
    VisualStatus.NOT_AVAILABLE -> R.string.visual_status_not_available to StatusKind.Warning
    VisualStatus.FAILED -> R.string.visual_status_failed to StatusKind.Error
}

private fun blockText(block: VisualBlock): Int = when (block) {
    VisualBlock.GEMMA_INFO -> R.string.visual_block_gemma_info
    VisualBlock.EMBED_TEXT -> R.string.visual_block_embed_text
    VisualBlock.EMBED_IMAGE -> R.string.visual_block_embed_image
    VisualBlock.PARITY -> R.string.visual_block_parity
    VisualBlock.RASTER -> R.string.visual_block_raster
    VisualBlock.IMAGE_DECODE -> R.string.visual_block_image_decode
    VisualBlock.PROJECTION -> R.string.visual_block_projection
    VisualBlock.GEMMA_PAGES -> R.string.visual_block_gemma_pages
    VisualBlock.READING -> R.string.visual_block_reading
}

@Composable
private fun BodyText(text: String, secondary: Boolean = false) {
    Text(
        text = text,
        style = LisdataTheme.typography.material.bodyMedium,
        color = if (secondary) LisdataTheme.colors.textSecondary else LisdataTheme.colors.textPrimary,
    )
}

private fun num(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

private const val BYTES_PER_MB = 1024L * 1024L
private const val BYTES_PER_KB = 1024L
private const val SHORT_HASH = 12

internal object VisualFeasibilityPreviewData {
    private val verified = VISUAL_MODELS.map { ModelRow(ModelCatalog.byId(it), ModelFileState.Verified(File("/models/$it"))) }

    // Illustrative preview values only, never shown as measurements (constitution V).
    private fun scenario(kind: VisualKind, status: VisualStatus, params: VisualParams, metrics: VisualMetrics? = null) = VisualScenario(
        kind = kind, model = ModelId.GEMMA4_E2B, backend = ComputeBackend.GPU, params = params, status = status,
        reason = if (status == VisualStatus.NOT_SUPPORTED) "budget 560 over the file's maximum 280" else null,
        startedAt = "2026-11-05T10:00:00+01:00", durationMs = 1000, thermalStart = ThermalLevel.NONE,
        thermalEnd = ThermalLevel.LIGHT, charging = false, peakRssMb = 3400, peakRssResetSupported = true, metrics = metrics,
    )

    val empty = VisualFeasibilityUiState(models = VISUAL_MODELS.map { ModelRow(ModelCatalog.byId(it), ModelFileState.Missing) })

    val results = VisualFeasibilityUiState(
        models = verified,
        deviceSet = DeviceSetSummary(present = true, verified = 48, rejected = emptyList()),
        results = listOf(
            scenario(VisualKind.GEMMA_PAGES, VisualStatus.COMPLETED, VisualParams(stage = "A", combination = "mezcla-p1-b280-t0",
                visionBackend = ComputeBackend.CPU), VisualMetrics(samples = 3, p50Ms = 6200.0, p95Ms = 6400.0, prefillTokens = 2000)),
            scenario(VisualKind.GEMMA_PAGES, VisualStatus.NOT_AVAILABLE, VisualParams(stage = "A", combination = "paginas-p1-b280-t0",
                visionBackend = ComputeBackend.GPU)),
        ),
        lastReportPath = "files/reports/visual-20261105-100000.md",
    )

    val running = VisualFeasibilityUiState(models = verified, isRunning = true, progress = VisualProgress("GEMMA_PAGES · ANSWER", 2, 3))
}

@LisdataPreviews
@Composable
private fun VisualFeasibilityResultsPreview() {
    LisdataTheme { VisualFeasibilityContent(VisualFeasibilityPreviewData.results, VisualFeasibilityActions()) }
}

@LisdataPreviews
@Composable
private fun VisualFeasibilityRunningPreview() {
    LisdataTheme { VisualFeasibilityContent(VisualFeasibilityPreviewData.running, VisualFeasibilityActions()) }
}

@LisdataPreviews
@Composable
private fun VisualFeasibilityEmptyPreview() {
    LisdataTheme { VisualFeasibilityContent(VisualFeasibilityPreviewData.empty, VisualFeasibilityActions()) }
}

/** Why a block is disabled: the models it needs (any one of them) and the device set, or null when it can run. */
@Composable
private fun needsText(needs: BlockNeeds): String? {
    val parts = buildList {
        if (needs.models.isNotEmpty()) {
            add(needs.models.joinToString(stringResource(R.string.visual_needs_or)) { ModelCatalog.byId(it).displayName })
        }
        if (needs.deviceSet) add(stringResource(R.string.visual_needs_set))
    }
    return parts.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.visual_needs, it.joinToString(stringResource(R.string.visual_needs_and))) }
}

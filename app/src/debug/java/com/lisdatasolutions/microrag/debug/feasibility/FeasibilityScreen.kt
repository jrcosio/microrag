package com.lisdatasolutions.microrag.debug.feasibility

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import com.lisdatasolutions.microrag.debug.feasibility.report.CompatibilityMetrics
import com.lisdatasolutions.microrag.debug.feasibility.report.EmbeddingMetrics
import com.lisdatasolutions.microrag.debug.feasibility.report.GenerationMetrics
import com.lisdatasolutions.microrag.debug.feasibility.report.ResultStatus
import com.lisdatasolutions.microrag.debug.feasibility.report.ScenarioKind
import com.lisdatasolutions.microrag.debug.feasibility.report.ScenarioResult
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

/** Actions of the diagnostics screen, so the content stays stateless and previewable. */
data class FeasibilityActions(
    val onBack: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onVerify: () -> Unit = {},
    val onRun: (Set<SuiteBlock>) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onThermalAnswer: (Boolean) -> Unit = {},
)

/** «MicroRAG Debug → Viabilidad on-device»: internal tool, never part of the product UI (FR-020). */
@Composable
fun FeasibilityScreen(onBack: () -> Unit, viewModel: FeasibilityViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // FR-013: leaving the screen or sending the app to the background cancels and frees engines.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    val view = LocalView.current
    DisposableEffect(state.isRunning) {
        view.keepScreenOn = state.isRunning
        onDispose { view.keepScreenOn = false }
    }
    FeasibilityContent(
        state = state,
        actions = FeasibilityActions(
            onBack = {
                viewModel.onStop()
                onBack()
            },
            onImport = viewModel::importDevModels,
            onVerify = viewModel::verifyAgain,
            onRun = viewModel::run,
            onCancel = viewModel::cancelRun,
            onThermalAnswer = viewModel::answerThermal,
        ),
    )
}

@Composable
internal fun FeasibilityContent(state: FeasibilityUiState, actions: FeasibilityActions) {
    val spacing = LisdataTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LisdataTheme.colors.background)
            .navigationBarsPadding(),
    ) {
        LisdataTopBar(title = stringResource(R.string.feasibility_title), onBack = actions.onBack)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
        ) {
            modelsSection(state, actions)
            testsSection(state, actions)
            resultsSection(state)
            summarySection(state)
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

private fun LazyListScope.modelsSection(state: FeasibilityUiState, actions: FeasibilityActions) {
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
        ButtonColumn {
            LisdataGradientButton(
                text = stringResource(R.string.feasibility_action_import),
                onClick = actions.onImport,
                enabled = state.canImport,
                loading = state.modelsBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            LisdataOutlinedButton(
                text = stringResource(R.string.feasibility_action_verify),
                onClick = actions.onVerify,
                enabled = state.canVerify,
                leadingIcon = LisdataIcons.Refresh,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun LazyListScope.testsSection(state: FeasibilityUiState, actions: FeasibilityActions) {
    item { LisdataSectionHeader(stringResource(R.string.feasibility_section_tests)) }
    item {
        ButtonColumn {
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
                    text = stringResource(R.string.feasibility_action_all),
                    onClick = { actions.onRun(SuiteBlock.entries.toSet()) },
                    enabled = state.canRunAll,
                    modifier = Modifier.fillMaxWidth(),
                )
                RunButton(R.string.feasibility_action_embeddings, state.canRunEmbeddings) {
                    actions.onRun(setOf(SuiteBlock.EMBEDDINGS))
                }
                RunButton(R.string.feasibility_action_compatibility, state.canRunEmbeddings) {
                    actions.onRun(setOf(SuiteBlock.COMPATIBILITY))
                }
                RunButton(R.string.feasibility_action_generation, state.canRunGeneration) {
                    actions.onRun(setOf(SuiteBlock.GENERATION))
                }
            }
            state.runError?.let { BodyText(stringResource(R.string.feasibility_run_error, it)) }
        }
    }
}

private fun LazyListScope.resultsSection(state: FeasibilityUiState) {
    item { LisdataSectionHeader(stringResource(R.string.feasibility_section_results)) }
    if (state.results.isEmpty()) {
        item { BodyText(stringResource(R.string.feasibility_results_empty)) }
    }
    itemsIndexed(state.results, key = { index, _ -> "result-$index" }) { _, result -> ResultCard(result) }
}

private fun LazyListScope.summarySection(state: FeasibilityUiState) {
    item { LisdataSectionHeader(stringResource(R.string.feasibility_section_summary)) }
    item {
        val exit = state.exitCriteria
        LisdataCard {
            BodyText(stringResource(R.string.feasibility_summary_ttft, yesNo(exit.ttftMeasured)))
            BodyText(
                stringResource(
                    R.string.feasibility_summary_query,
                    exit.bestQueryP95CpuMs?.let { "${num(it)} ms" } ?: stringResource(R.string.feasibility_not_measured),
                ),
            )
            BodyText(stringResource(R.string.feasibility_summary_budget, yesNo(exit.queryUnder150msCpu)))
            state.lastReportPath?.let { BodyText(stringResource(R.string.feasibility_report_path, it), secondary = true) }
        }
    }
}

@Composable
private fun ProgressCard(progress: SuiteProgress) {
    LisdataCard {
        BodyText(progress.label)
        val phase = stringResource(phaseText(progress.phase))
        BodyText(
            if (progress.total > 0) stringResource(R.string.feasibility_running_step, phase, progress.step, progress.total) else phase,
            secondary = true,
        )
        // Unknown duration (initialization, thermal wait) shows an indeterminate bar (constitution V).
        LisdataLinearProgress(
            if (progress.total > 0) ProgressValue.Determinate(progress.step.toFloat() / progress.total) else ProgressValue.Indeterminate,
        )
    }
}

@Composable
private fun ResultCard(result: ScenarioResult) {
    val name = ModelCatalog.byId(result.model).displayName
    val title = when (result.kind) {
        ScenarioKind.COMPATIBILITY -> stringResource(R.string.feasibility_kind_compatibility, name)
        else -> listOfNotNull(name, result.backend?.name, result.activationPrecision?.name).joinToString(" · ")
    }
    LisdataCard {
        BodyText(title)
        val (statusText, kind) = statusOf(result.status)
        LisdataStatusChip(text = stringResource(statusText), kind = kind)
        result.embedding?.let { EmbeddingLines(it) }
        result.compatibility?.let { CompatibilityLines(it) }
        result.generation?.let { GenerationLines(it) }
        result.reason?.let { BodyText(stringResource(R.string.feasibility_metric_reason, it), secondary = true) }
        BodyText(
            stringResource(
                R.string.feasibility_metric_context,
                result.peakRssMb?.toString() ?: "—",
                result.thermalStart.name,
                result.thermalEnd.name,
            ),
            secondary = true,
        )
    }
}

@Composable
private fun EmbeddingLines(m: EmbeddingMetrics) {
    BodyText(stringResource(R.string.feasibility_metric_init, m.initColdMs, m.initWarmMs))
    BodyText(stringResource(R.string.feasibility_metric_query, num(m.queryP50Ms), num(m.queryP95Ms)))
    BodyText(stringResource(R.string.feasibility_metric_document, num(m.documentP50Ms), num(m.documentP95Ms)))
    BodyText(
        stringResource(
            R.string.feasibility_metric_vector,
            m.rawDims,
            m.outputDims,
            num(m.l2Norm, 6),
            m.declaredMaxContextTokens?.toString() ?: "—",
        ),
        secondary = true,
    )
}

@Composable
private fun CompatibilityLines(c: CompatibilityMetrics) {
    BodyText(stringResource(R.string.feasibility_metric_cosine, num(c.cosineMin, 5), num(c.cosineMean, 5), c.textCount))
    BodyText(
        stringResource(if (c.compatible) R.string.feasibility_metric_compatible else R.string.feasibility_metric_not_compatible),
    )
}

@Composable
private fun GenerationLines(g: GenerationMetrics) {
    var showOutput by rememberSaveable { mutableStateOf(false) }
    BodyText(stringResource(R.string.feasibility_metric_init, g.initColdMs, g.initWarmMs))
    BodyText(
        stringResource(
            R.string.feasibility_metric_prompt,
            g.promptTokens?.toString() ?: "—",
            g.ttftMs?.let { num(it) } ?: "—",
            g.ttftWallMs,
        ),
    )
    BodyText(
        stringResource(
            R.string.feasibility_metric_speed,
            g.prefillTokensPerSecond?.let { num(it) } ?: "—",
            g.decodeTokensPerSecond?.let { num(it) } ?: "—",
        ),
    )
    g.fallbackReason?.let { BodyText(stringResource(R.string.feasibility_metric_fallback, it), secondary = true) }
    LisdataOutlinedButton(
        text = stringResource(if (showOutput) R.string.feasibility_action_hide_output else R.string.feasibility_action_show_output),
        onClick = { showOutput = !showOutput },
        modifier = Modifier.fillMaxWidth(),
    )
    if (showOutput) BodyText(g.sampleOutput, secondary = true)
}

@Composable
private fun ModelRowItem(row: ModelRow) {
    val file = row.file
    val (statusText, kind) = modelStatusOf(row)
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
private fun modelStatusOf(row: ModelRow): Pair<String, StatusKind> = when (val state = row.state) {
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

private fun statusOf(status: ResultStatus): Pair<Int, StatusKind> = when (status) {
    ResultStatus.COMPLETED -> R.string.feasibility_status_completed to StatusKind.Ready
    ResultStatus.CANCELLED -> R.string.feasibility_status_cancelled to StatusKind.Paused
    ResultStatus.FAILED -> R.string.feasibility_status_failed to StatusKind.Error
    ResultStatus.NOT_AVAILABLE -> R.string.feasibility_status_not_available to StatusKind.Warning
}

private fun phaseText(phase: SuitePhase): Int = when (phase) {
    SuitePhase.VERIFY -> R.string.feasibility_phase_verify
    SuitePhase.METADATA -> R.string.feasibility_phase_metadata
    SuitePhase.THERMAL -> R.string.feasibility_phase_thermal
    SuitePhase.INIT_COLD -> R.string.feasibility_phase_init_cold
    SuitePhase.INIT_WARM -> R.string.feasibility_phase_init_warm
    SuitePhase.QUERY_WARMUP -> R.string.feasibility_phase_query_warmup
    SuitePhase.QUERY -> R.string.feasibility_phase_query
    SuitePhase.DOCUMENT_WARMUP -> R.string.feasibility_phase_document_warmup
    SuitePhase.DOCUMENT -> R.string.feasibility_phase_document
    SuitePhase.COMPAT_CPU -> R.string.feasibility_phase_compat_cpu
    SuitePhase.COMPAT_GPU -> R.string.feasibility_phase_compat_gpu
    SuitePhase.GENERATION_WARMUP -> R.string.feasibility_phase_generation_warmup
    SuitePhase.GENERATION -> R.string.feasibility_phase_generation
}

@Composable
private fun RunButton(text: Int, enabled: Boolean, onClick: () -> Unit) {
    LisdataOutlinedButton(
        text = stringResource(text),
        onClick = onClick,
        enabled = enabled,
        leadingIcon = LisdataIcons.Play,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ButtonColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) { content() }
}

@Composable
private fun BodyText(text: String, secondary: Boolean = false) {
    Text(
        text = text,
        style = LisdataTheme.typography.material.bodyMedium,
        color = if (secondary) LisdataTheme.colors.textSecondary else LisdataTheme.colors.textPrimary,
    )
}

@Composable
private fun yesNo(value: Boolean) = stringResource(if (value) R.string.feasibility_yes else R.string.feasibility_no)

private fun ModelFileState.Verifying.fraction(): Float =
    if (totalBytes <= 0) 0f else (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f)

private fun num(value: Double, decimals: Int = 1): String = String.format(Locale.ROOT, "%.${decimals}f", value)

private const val BYTES_PER_MB = 1024L * 1024L
private const val SHORT_HASH = 12

internal object FeasibilityPreviewData {
    private val verified = ModelCatalog.all.map { ModelRow(it, ModelFileState.Verified(File("/models/${it.fileName}"))) }

    private fun result(kind: ScenarioKind, model: ModelId, backend: ComputeBackend?, status: ResultStatus) = ScenarioResult(
        kind = kind, model = model, backend = backend, status = status,
        reason = if (status == ResultStatus.NOT_AVAILABLE) "LiteRtLmJniException: GPU delegate unavailable" else null,
        atPhase = if (status == ResultStatus.COMPLETED) null else SuitePhase.INIT_COLD.name,
        protocol = MeasurementProtocol.EMBEDDING, startedAt = "2026-10-25T23:00:00+01:00", durationMs = 1000,
        thermalStart = ThermalLevel.NONE, thermalEnd = ThermalLevel.LIGHT, charging = true, peakRssMb = 512,
        peakRssResetSupported = true,
        // Illustrative preview values only, never shown as measurements (constitution V).
        embedding = if (kind == ScenarioKind.EMBEDDING && status == ResultStatus.COMPLETED) {
            EmbeddingMetrics(800, 300, 30.0, 35.0, 90.0, 95.0, 768, 256, 1.0, 8192, ComputeBackend.entries)
        } else {
            null
        },
        compatibility = if (kind == ScenarioKind.COMPATIBILITY) CompatibilityMetrics(20, 0.995, 0.998, true) else null,
        generation = if (kind == ScenarioKind.GENERATION) {
            GenerationMetrics(9000, 3000, 3.0, 2010, 1500.0, 1550, 1300.0, 18.0, 128, emptyList(), "Respuesta [1].", false)
        } else {
            null
        },
    )

    val missing = FeasibilityUiState(models = ModelCatalog.all.map { ModelRow(it, ModelFileState.Missing) })

    val verifying = FeasibilityUiState(
        models = ModelCatalog.all.mapIndexed { i, file ->
            ModelRow(file, if (i == 0) ModelFileState.Verifying(1_100_000_000, file.sizeBytes) else ModelFileState.Unverified(File("/m")))
        },
        modelsBusy = true,
    )

    val running = FeasibilityUiState(
        models = verified,
        isRunning = true,
        progress = SuiteProgress("EmbeddingGemma 2 270M · CPU", SuitePhase.QUERY, 12, 30),
    )

    val results = FeasibilityUiState(
        models = verified,
        results = listOf(
            result(ScenarioKind.EMBEDDING, ModelId.EG2_270M, ComputeBackend.CPU, ResultStatus.COMPLETED),
            result(ScenarioKind.EMBEDDING, ModelId.EG2_270M, ComputeBackend.GPU, ResultStatus.NOT_AVAILABLE),
            result(ScenarioKind.COMPATIBILITY, ModelId.EG2_270M, null, ResultStatus.COMPLETED),
            result(ScenarioKind.GENERATION, ModelId.GEMMA4_E2B_GPU, ComputeBackend.GPU, ResultStatus.COMPLETED),
        ),
        lastReportPath = "files/reports/feasibility-20261025-231005.md",
    )

    val error = FeasibilityUiState(
        models = ModelCatalog.all.mapIndexed { i, file ->
            ModelRow(
                file,
                when (i) {
                    0 -> ModelFileState.Mismatch(file.sha256, "0".repeat(64))
                    1 -> ModelFileState.Failed("not enough space: 1979 MB needed")
                    else -> ModelFileState.Missing
                },
                devCopyBytes = if (i == 2) 1L else null,
            )
        },
        runError = "IllegalStateException: texts not found",
    )
}

@LisdataPreviews
@Composable
private fun FeasibilityResultsPreview() {
    LisdataTheme { FeasibilityContent(FeasibilityPreviewData.results, FeasibilityActions()) }
}

@LisdataPreviews
@Composable
private fun FeasibilityRunningPreview() {
    LisdataTheme { FeasibilityContent(FeasibilityPreviewData.running, FeasibilityActions()) }
}

@LisdataPreviews
@Composable
private fun FeasibilityEmptyPreview() {
    LisdataTheme { FeasibilityContent(FeasibilityPreviewData.missing, FeasibilityActions()) }
}

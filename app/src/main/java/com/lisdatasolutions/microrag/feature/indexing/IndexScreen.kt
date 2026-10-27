package com.lisdatasolutions.microrag.feature.indexing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDarkButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataHeroCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataInfoCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataProgressRing
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataQueueRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStageRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatusChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.indexing.FailedDocument
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.feature.ingest.chipText
import org.koin.androidx.compose.koinViewModel

/** What the stateless screen can ask for. */
data class IndexActions(
    val onBack: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onRetry: (Long) -> Unit = {},
    val onPrepareModels: () -> Unit = {},
)

@Composable
fun IndexRoute(onBack: () -> Unit, onPrepareModels: () -> Unit, viewModel: IndexViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.indexEvents.collect { event ->
            when (event) {
                IndexEvent.OpenSetup -> onPrepareModels()
            }
        }
    }
    IndexScreen(
        state = state,
        actions = IndexActions(
            onBack = onBack,
            onPause = viewModel::onPause,
            onResume = viewModel::onResume,
            onRetry = viewModel::onRetry,
            onPrepareModels = viewModel::onPrepareModels,
        ),
    )
}

/** P08 (PNG 08 with G29 and the approved proposals P1–P12, contracts/index-ui.md). */
@Composable
fun IndexScreen(state: IndexUiState, actions: IndexActions, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val announcement = announcement(state.indexing)
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        LisdataTopBar(title = stringResource(R.string.index_title), onBack = actions.onBack)
        // U3: important changes are read politely, without moving the focus.
        Box(
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                if (announcement != null) contentDescription = announcement
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
        ) {
            Spacer(Modifier.height(spacing.space2))
            when (val indexing = state.indexing) {
                IndexingState.Loading -> Loading()
                IndexingState.IndexUnavailable -> LisdataEmptyState(
                    icon = LisdataIcons.Warning,
                    title = stringResource(R.string.index_unavailable_title),
                    body = stringResource(R.string.index_unavailable_body),
                )
                is IndexingState.Idle -> {
                    LisdataEmptyState(
                        icon = LisdataIcons.CheckCircle,
                        title = stringResource(R.string.index_idle_title),
                        body = stringResource(R.string.index_idle_body),
                    )
                    Failed(indexing.failed, actions.onRetry)
                }
                is IndexingState.Active -> if (indexing.pause == PauseReason.MODEL_MISSING) {
                    LisdataEmptyState(
                        icon = LisdataIcons.Cpu,
                        title = stringResource(R.string.index_model_missing_title),
                        body = stringResource(R.string.index_model_missing_body),
                        action = {
                            LisdataGradientButton(text = stringResource(R.string.index_prepare_models), onClick = actions.onPrepareModels)
                        },
                    )
                } else {
                    Running(indexing)
                    Failed(indexing.failed, actions.onRetry)
                }
            }
            Spacer(Modifier.height(spacing.space4))
        }
        state.action?.let { ActionBar(it, enabled = !state.busy, actions) }
    }
}

@Composable
private fun Loading() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = LisdataTheme.spacing.space6),
        contentAlignment = Alignment.Center,
    ) {
        LisdataProgressRing(progress = ProgressValue.Indeterminate, label = stringResource(R.string.index_loading), size = 72.dp)
    }
}

@Composable
private fun Running(state: IndexingState.Active) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val progress = state.progress
    val ring = progress.ring()
    val total = progress.total
    val ringDescription = if (ring is ProgressValue.Determinate && total != null) {
        stringResource(R.string.index_ring_description, ring.percent, progress.done.grouped(), total.grouped())
    } else {
        stringResource(R.string.index_preparing)
    }
    // D-042: compact hero and ring as in PNG 08, so «Procesando ahora» shows without scrolling.
    LisdataHeroCard(title = null, eyebrow = stringResource(R.string.index_eyebrow), modifier = Modifier.fillMaxWidth(), compact = true) {
        // U1: the ring, its percentage and the chunks are read as one.
        Column(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = ringDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.space1),
        ) {
            LisdataProgressRing(progress = ring, label = stringResource(R.string.index_ring_label), size = HERO_RING_SIZE)
            Text(
                text = if (total != null) {
                    stringResource(R.string.index_chunks, progress.done.grouped(), total.grouped())
                } else {
                    stringResource(R.string.index_preparing)
                },
                style = LisdataTheme.typography.material.titleMedium,
                color = colors.onDarkText,
                textAlign = TextAlign.Center,
            )
        }
        progress.remainingSeconds?.let { seconds ->
            Text(
                text = remainingText(seconds),
                style = LisdataTheme.typography.material.bodyMedium,
                color = colors.onDarkAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.pause?.chip()?.let { (text, kind) ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LisdataStatusChip(text = text, kind = kind, onDark = true)
            }
        }
    }

    LisdataSectionHeader(text = stringResource(R.string.index_stages), modifier = Modifier.padding(top = spacing.space2))
    val paused = state.pause != null
    val stages = stagesOf(state.current?.stage, paused)
    val currentStage = state.current?.stage?.indexStage()
    stages.forEach { (stage, status) ->
        LisdataStageRow(
            icon = stage.icon,
            title = stringResource(stage.title),
            statusText = stageText(status, isCurrent = stage == currentStage, paused = paused),
            status = status,
            modifier = Modifier.fillMaxWidth(),
            compact = true,
        )
    }

    state.current?.let { current ->
        LisdataInfoCard(
            icon = LisdataIcons.FileText,
            title = stringResource(R.string.index_processing_now),
            body = listOfNotNull(
                current.displayName,
                state.queuedDocuments.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.index_more_queued, it, it) },
            ).joinToString("\n"),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.pause?.explanation()?.let { body ->
        LisdataInfoCard(icon = LisdataIcons.Warning, title = state.pause.chip()?.first.orEmpty(), body = body, modifier = Modifier.fillMaxWidth())
    }
}

/** P6: documents in error, with Retry when it can help. */
@Composable
private fun Failed(failed: List<FailedDocument>, onRetry: (Long) -> Unit) {
    if (failed.isEmpty()) return
    LisdataSectionHeader(text = stringResource(R.string.index_failed_header), modifier = Modifier.padding(top = LisdataTheme.spacing.space2))
    failed.forEach { document ->
        val cause = document.error.chipText()
        LisdataQueueRow(
            name = document.displayName,
            meta = "",
            statusText = cause,
            status = StatusKind.Error,
            // One sentence on the clickable card, so TalkBack names what «Reintentar» acts on.
            accessibilityText = stringResource(R.string.index_failed_row, document.displayName, cause),
            onClickLabel = if (document.canRetry) stringResource(R.string.index_retry) else null,
            onClick = if (document.canRetry) ({ onRetry(document.id) }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionBar(action: IndexAction, enabled: Boolean, actions: IndexActions) {
    val spacing = LisdataTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LisdataTheme.colors.background)
            .navigationBarsPadding()
            .padding(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space3),
    ) {
        val modifier = Modifier.fillMaxWidth()
        when (action) {
            IndexAction.Pause -> LisdataDarkButton(
                text = stringResource(R.string.index_pause),
                onClick = actions.onPause,
                enabled = enabled,
                trailingIcon = LisdataIcons.Pause,
                modifier = modifier,
            )
            IndexAction.Resume -> LisdataGradientButton(
                text = stringResource(R.string.index_resume),
                onClick = actions.onResume,
                enabled = enabled,
                trailingIcon = LisdataIcons.Play,
                modifier = modifier,
            )
            is IndexAction.Retry -> LisdataGradientButton(
                text = stringResource(R.string.index_retry),
                onClick = { actions.onRetry(action.documentId) },
                enabled = enabled,
                trailingIcon = LisdataIcons.Refresh,
                modifier = modifier,
            )
        }
    }
}

/** U3: what changed since the previous state, as TalkBack announces it; progress per batch is not announced. */
@Composable
private fun announcement(state: IndexingState): String? {
    var previous by remember { mutableStateOf<IndexingState?>(null) }
    var announced by remember { mutableStateOf<Announcement?>(null) }
    LaunchedEffect(state) {
        val before = previous
        if (before != null && before !is IndexingState.Loading) {
            announced = Announcement.between(before, state) ?: announced
        }
        previous = state
    }
    return when (val item = announced) {
        null -> null
        is Announcement.Started -> stringResource(R.string.index_announce_document_started, item.name)
        is Announcement.Paused -> stringResource(R.string.index_announce_paused, item.reason.chip()?.first.orEmpty())
        is Announcement.Failed -> stringResource(R.string.index_announce_failed, item.document.displayName, item.document.error.chipText())
        Announcement.Idle -> stringResource(R.string.index_idle_title)
    }
}

private sealed interface Announcement {
    data class Started(val name: String) : Announcement
    data class Paused(val reason: PauseReason) : Announcement
    data class Failed(val document: FailedDocument) : Announcement
    data object Idle : Announcement

    companion object {
        fun between(before: IndexingState, after: IndexingState): Announcement? {
            val failedBefore = (before as? IndexingState.Active)?.failed ?: (before as? IndexingState.Idle)?.failed.orEmpty()
            val failedAfter = (after as? IndexingState.Active)?.failed ?: (after as? IndexingState.Idle)?.failed.orEmpty()
            failedAfter.lastOrNull { it !in failedBefore }?.let { return Failed(it) }
            if (before is IndexingState.Active && after is IndexingState.Idle) return Idle
            if (after !is IndexingState.Active) return null
            val pausedBefore = (before as? IndexingState.Active)?.pause
            if (after.pause != null && after.pause != pausedBefore && after.pause != PauseReason.MODEL_MISSING) return Paused(after.pause)
            val current = after.current ?: return null
            if (current.id != (before as? IndexingState.Active)?.current?.id) return Started(current.displayName)
            return null
        }
    }
}

/** The ring of PNG 08, smaller than the design system's default (D-042). */
internal val HERO_RING_SIZE = 154.dp

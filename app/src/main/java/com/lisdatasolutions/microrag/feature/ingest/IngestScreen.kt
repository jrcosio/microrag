package com.lisdatasolutions.microrag.feature.ingest

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCountChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEyebrow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataFilePickerCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataQueueRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import org.koin.androidx.compose.koinViewModel

/** What the stateless screen can ask for. */
data class IngestActions(
    val onBack: () -> Unit = {},
    val onPickFiles: () -> Unit = {},
    val onRowClick: (QueueKey) -> Unit = {},
    val onStartIndexing: () -> Unit = {},
    val onDismissSheet: () -> Unit = {},
    val onRetry: (QueueKey) -> Unit = {},
    val onRemove: (QueueKey) -> Unit = {},
    val onConfirmRemoval: () -> Unit = {},
    val onDismissRemoval: () -> Unit = {},
)

@Composable
fun IngestRoute(onBack: () -> Unit, onStartIndexing: () -> Unit, viewModel: IngestViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        viewModel.onFilesPicked(uris.map(Uri::toString))
    }
    LaunchedEffect(viewModel) {
        viewModel.ingestEvents.collect { event ->
            when (event) {
                IngestEvent.OpenIndex -> onStartIndexing()
            }
        }
    }
    IngestScreen(
        state = state,
        actions = IngestActions(
            onBack = onBack,
            onPickFiles = { picker.launch(IngestMimeTypes.ALL) },
            onRowClick = viewModel::onRowClick,
            onStartIndexing = viewModel::onStartIndexing,
            onDismissSheet = viewModel::onDismissSheet,
            onRetry = viewModel::onRetry,
            onRemove = viewModel::onRemove,
            onConfirmRemoval = viewModel::onConfirmRemoval,
            onDismissRemoval = viewModel::onDismissRemoval,
        ),
    )
}

/** P05 (PNG 05 with G14/G15 and the approved proposals P1–P5, contracts/ingest-ui.md). */
@Composable
fun IngestScreen(state: IngestUiState, actions: IngestActions, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val typography = LisdataTheme.typography.material
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        LisdataTopBar(title = stringResource(R.string.ingest_title), onBack = actions.onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
        ) {
            LisdataEyebrow(stringResource(R.string.ingest_eyebrow), modifier = Modifier.padding(top = spacing.space2))
            Text(
                stringResource(R.string.ingest_headline),
                style = typography.headlineLarge,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.ingest_intro), style = typography.bodyLarge, color = colors.textSecondary)
            Spacer(Modifier.height(spacing.space2))
            LisdataFilePickerCard(
                title = stringResource(R.string.ingest_picker_title),
                subtitle = stringResource(R.string.ingest_picker_subtitle),
                buttonText = stringResource(R.string.ingest_picker_button),
                onPick = actions.onPickFiles,
                enabled = state.pickEnabled,
            )
            Text(
                stringResource(if (state.queue.pdfSupported) R.string.ingest_formats else R.string.ingest_formats_no_pdf),
                style = typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.space2))
            val announcement = queueAnnouncement(state)
            LisdataSectionHeader(
                text = stringResource(R.string.ingest_queue_header),
                // U5: rows that become ready or fail are read politely, without moving the focus.
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (announcement != null) stateDescription = announcement
                },
                trailing = if (state.items.isNotEmpty()) {
                    { LisdataCountChip(pluralStringResource(R.plurals.ingest_queue_count, state.items.size, state.items.size)) }
                } else {
                    null
                },
            )
            QueueContent(state, actions)
            Spacer(Modifier.height(spacing.space4))
        }
        StartBar(state, actions.onStartIndexing)
    }
    state.selectedItem?.let { item ->
        QueueItemSheet(
            item = item,
            onRetry = { actions.onRetry(item.key) },
            onRemove = { actions.onRemove(item.key) },
            onDismiss = actions.onDismissSheet,
        )
    }
    state.removalItem?.let { item ->
        RemovalConfirmation(item = item, onConfirm = actions.onConfirmRemoval, onDismiss = actions.onDismissRemoval)
    }
}

/** The last row that became ready or failed since the previous state, as TalkBack announces it (U5). */
@Composable
private fun queueAnnouncement(state: IngestUiState): String? {
    var previous by remember { mutableStateOf<Map<QueueKey, QueueItemState>?>(null) }
    var announced by remember { mutableStateOf<QueueItem?>(null) }
    val current = state.items.associate { it.key to it.state }
    LaunchedEffect(current) {
        val before = previous
        if (before != null) {
            state.items.lastOrNull { item ->
                before[item.key] != item.state && (item.state == QueueItemState.Ready || item.state.error != null)
            }?.let { announced = it }
        }
        previous = current
    }
    val item = announced ?: return null
    val error = item.state.error
    return if (error == null) {
        stringResource(R.string.ingest_announce_ready, item.displayName)
    } else {
        stringResource(R.string.ingest_announce_error, item.displayName, error.chipText())
    }
}

@Composable
private fun QueueContent(state: IngestUiState, actions: IngestActions) {
    when {
        state.loading -> Unit
        !state.queue.indexAvailable -> LisdataEmptyState(
            icon = LisdataIcons.Warning,
            title = stringResource(R.string.ingest_unavailable_title),
            body = stringResource(R.string.ingest_unavailable_body),
        )
        state.items.isEmpty() -> LisdataEmptyState(
            icon = LisdataIcons.Folder,
            title = stringResource(R.string.ingest_empty_title),
            body = stringResource(R.string.ingest_empty_body),
        )
        else -> state.items.forEach { item ->
            val error = item.state.error
            LisdataQueueRow(
                name = item.displayName,
                meta = item.metaText(),
                statusText = error?.chipText(),
                status = error?.let { StatusKind.Error },
                progress = item.state.progress(),
                accessibilityText = item.accessibilityText(),
                onClickLabel = stringResource(R.string.ingest_row_action),
                onClick = { actions.onRowClick(item.key) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StartBar(state: IngestUiState, onStart: () -> Unit) {
    val spacing = LisdataTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LisdataTheme.colors.background)
            .navigationBarsPadding()
            .padding(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space3),
    ) {
        LisdataGradientButton(
            text = stringResource(R.string.ingest_start),
            onClick = onStart,
            enabled = state.startEnabled,
            loading = state.starting,
            trailingIcon = LisdataIcons.ChevronRight,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

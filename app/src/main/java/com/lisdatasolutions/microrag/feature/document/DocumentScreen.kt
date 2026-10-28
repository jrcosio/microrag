package com.lisdatasolutions.microrag.feature.document

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCountChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataHeroCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataIconTile
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataInfoCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataProgressCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataProgressRing
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSkeletonRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatusChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSwitchRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.ReindexState
import com.lisdatasolutions.microrag.feature.ingest.chipText
import com.lisdatasolutions.microrag.feature.ingest.detailText
import com.lisdatasolutions.microrag.feature.ingest.sizeText
import com.lisdatasolutions.microrag.feature.library.grouped
import org.koin.androidx.compose.koinViewModel
import kotlin.math.max

/** What the stateless screen can ask for. */
data class DocumentActions(
    val onBack: () -> Unit = {},
    val onIncludedChange: (Boolean) -> Unit = {},
    val onOpenIndex: () -> Unit = {},
    val onReindex: () -> Unit = {},
    /** D3 retries the document, D6 its failed reindex. */
    val onRetry: () -> Unit = {},
    val onDiscardReindex: () -> Unit = {},
    /** D8: both ask for confirmation first. */
    val onDelete: () -> Unit = {},
    val onRemoveFromIndex: () -> Unit = {},
    val onConfirmDialog: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
fun DocumentRoute(
    onBack: () -> Unit,
    onGone: () -> Unit,
    onDeleted: () -> Unit,
    onOpenIndex: () -> Unit,
    viewModel: DocumentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DocumentEvent.Gone -> onGone()
                DocumentEvent.Deleted -> onDeleted()
            }
        }
    }
    DocumentScreen(
        state = state,
        actions = DocumentActions(
            onBack = onBack,
            onIncludedChange = viewModel::onIncludedChange,
            onOpenIndex = onOpenIndex,
            onReindex = viewModel::onReindex,
            onRetry = viewModel::onRetry,
            onDiscardReindex = viewModel::onDiscardReindex,
            onDelete = viewModel::onDelete,
            onRemoveFromIndex = viewModel::onRemoveFromIndex,
            onConfirmDialog = viewModel::onConfirmDialog,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

/** P07 (PNG 07 with the approved proposals D1–D11, contracts/library-ui.md). */
@Composable
fun DocumentScreen(state: DocumentUiState, actions: DocumentActions, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val detail = (state.content as? DocumentContent.Loaded)?.detail
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        LisdataTopBar(
            title = stringResource(R.string.document_title),
            onBack = actions.onBack,
            actions = {
                // The «⋯» menu only has «Quitar del índice», for indexed documents.
                if (detail?.state == DocumentDetailState.Indexed && !state.deleting) MoreMenu(actions.onRemoveFromIndex)
            },
        )
        when (state.content) {
            DocumentContent.Loading -> Loading()
            DocumentContent.Unavailable -> LisdataEmptyState(
                icon = LisdataIcons.Warning,
                title = stringResource(R.string.library_unavailable_title),
                body = stringResource(R.string.library_unavailable_body),
            )
            is DocumentContent.Loaded -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenPaddingHorizontal)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(spacing.listGap),
            ) {
                Spacer(Modifier.height(spacing.space1))
                Hero(state.content.detail)
                Stats(state.content.detail)
                LisdataSwitchRow(
                    title = stringResource(R.string.document_include_title),
                    subtitle = includeSubtitle(state.content.detail, state.included),
                    checked = state.included,
                    onCheckedChange = actions.onIncludedChange,
                    enabled = state.content.detail.canChangeInclusion && !state.deleting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Reindex(state.content.detail, actions)
                if (state.content.detail.copyMissing) {
                    // D10: the document keeps working in answers, but cannot be indexed again.
                    LisdataInfoCard(
                        icon = LisdataIcons.Warning,
                        title = stringResource(R.string.document_copy_missing_title),
                        body = stringResource(R.string.document_copy_missing_body),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LisdataSectionHeader(
                    text = stringResource(R.string.document_preview_header),
                    modifier = Modifier.padding(top = spacing.space3),
                )
                Preview(state.content.detail, state.preview, actions)
                Actions(state, state.content.detail, actions)
                Spacer(Modifier.height(spacing.space4))
            }
        }
    }
    Announcement(state)
    if (detail != null) Dialog(state.dialog, detail, actions)
}

/** What the switch does now: PNG 07 only shows the included case (constitution V). */
@Composable
private fun includeSubtitle(detail: DocumentDetail, included: Boolean): String = stringResource(
    when {
        !detail.canChangeInclusion -> R.string.document_include_subtitle_not_indexed
        included -> R.string.document_include_subtitle
        else -> R.string.document_include_subtitle_excluded
    },
)

@Composable
private fun Loading() {
    // D9, as F07 P12.
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = LisdataTheme.spacing.space6), contentAlignment = Alignment.Center) {
        LisdataProgressRing(progress = ProgressValue.Indeterminate, label = stringResource(R.string.state_loading), size = 72.dp)
    }
}

@Composable
private fun MoreMenu(onRemoveFromIndex: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            LisdataIcon(LisdataIcons.MoreHorizontal, contentDescription = stringResource(R.string.document_more), tint = LisdataTheme.colors.textPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = LisdataTheme.colors.surface) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_remove_from_index), color = LisdataTheme.colors.textPrimary) },
                onClick = {
                    open = false
                    onRemoveFromIndex()
                },
            )
        }
    }
}

@Composable
private fun Hero(detail: DocumentDetail) {
    val (chipText, chipKind) = detail.chip()
    // PNG 07: dark hero without eyebrow or orbits; the state chip keeps its light colors.
    LisdataHeroCard(title = null, decoration = false, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            LisdataIconTile(icon = LisdataIcons.FileText, style = IconTileStyle.OnDark)
            Spacer(Modifier.weight(1f))
            LisdataStatusChip(text = chipText, kind = chipKind)
        }
        Text(
            text = detail.displayName,
            style = LisdataTheme.typography.material.headlineMedium,
            color = LisdataTheme.colors.onDarkText,
        )
    }
}

@Composable
private fun Stats(detail: DocumentDetail) {
    val stats = listOf(
        stat(R.string.document_stat_pages, detail.pages?.grouped(), R.string.document_no_pages),
        stat(R.string.document_stat_chunks, detail.chunks?.grouped(), R.string.document_no_chunks),
        stat(R.string.document_stat_size, sizeText(detail.sizeBytes), R.string.document_no_value),
        stat(R.string.document_stat_imported, importedText(detail.importedAtMs), R.string.document_no_value),
    )
    Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.gridGap)) {
        stats.chunked(2).forEachIndexed { row, pair ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.gridGap),
            ) {
                pair.forEachIndexed { column, stat ->
                    LisdataStatCard(
                        label = stat.label,
                        value = stat.value,
                        // «FRAGMENTOS» in brand blue, as in PNG 07.
                        emphasis = row == 0 && column == 1,
                        accessibilityText = stat.accessibilityText,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** D5 and D6: a reindex in progress or failed, under the grid. */
@Composable
private fun Reindex(detail: DocumentDetail, actions: DocumentActions) {
    when (val reindex = detail.reindex) {
        null -> Unit
        is ReindexState.Running -> Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
            val total = reindex.total
            LisdataProgressCard(
                stage = if (total != null) {
                    stringResource(R.string.document_reindex_progress, reindex.done.grouped(), total.grouped())
                } else {
                    stringResource(R.string.document_reindex_preparing)
                },
                progress = progressOf(reindex.done, total),
                detail = stringResource(R.string.document_reindex_body),
                modifier = Modifier.fillMaxWidth(),
            )
            reindex.pause?.let { LisdataStatusChip(text = it.text(), kind = StatusKind.Paused) }
            IndexLink(actions.onOpenIndex)
        }
        is ReindexState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
            LisdataInfoCard(
                icon = LisdataIcons.Warning,
                title = stringResource(R.string.document_reindex_failed_title, reindex.error.causeText(detail.format)),
                body = stringResource(R.string.document_reindex_failed_body),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space3)) {
                if (reindex.error.retryable && !detail.copyMissing) {
                    LisdataOutlinedButton(text = stringResource(R.string.document_retry), onClick = actions.onRetry, leadingIcon = LisdataIcons.Refresh)
                }
                LisdataOutlinedButton(text = stringResource(R.string.document_reindex_discard), onClick = actions.onDiscardReindex)
            }
        }
    }
}

@Composable
private fun IndexLink(onOpenIndex: () -> Unit) {
    TextButton(onClick = onOpenIndex) {
        Text(
            stringResource(R.string.document_view_indexing),
            style = LisdataTheme.typography.material.labelLarge,
            color = LisdataTheme.colors.brandBlue,
        )
    }
}

private fun progressOf(done: Int, total: Int?): ProgressValue =
    total?.takeIf { it > 0 }?.let { ProgressValue.Determinate((done.toFloat() / it).coerceIn(0f, 1f)) } ?: ProgressValue.Indeterminate

/** The preview of an indexed document, or the card of its state (D1–D4). */
@Composable
private fun Preview(detail: DocumentDetail, preview: PreviewContent, actions: DocumentActions) {
    val wide = Modifier.fillMaxWidth()
    when (val state = detail.state) {
        DocumentDetailState.Indexed -> when (preview) {
            PreviewContent.Loading -> LisdataSkeletonRow(lines = 3)
            PreviewContent.None -> LisdataInfoCard(icon = LisdataIcons.Info, title = stringResource(R.string.document_no_preview), modifier = wide)
            is PreviewContent.Shown -> {
                if (preview.preview.citedChunkMissing) {
                    // D7.
                    LisdataInfoCard(icon = LisdataIcons.Info, title = stringResource(R.string.document_citation_missing), modifier = wide)
                }
                PreviewCard(preview.preview)
            }
        }
        // D1.
        DocumentDetailState.Pending -> LisdataInfoCard(
            icon = LisdataIcons.Info,
            title = stringResource(R.string.document_pending_title),
            body = stringResource(R.string.document_pending_body),
            modifier = wide,
        )
        // D2.
        is DocumentDetailState.Indexing -> Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
            val total = state.total
            LisdataProgressCard(
                stage = if (total != null) chunksProgress(state.done, total) else stringResource(R.string.document_indexing_preparing),
                progress = progressOf(state.done, total),
                modifier = wide,
            )
            IndexLink(actions.onOpenIndex)
        }
        // D3: the cause and the sentence of the Ingest.
        is DocumentDetailState.Failed -> LisdataInfoCard(
            icon = LisdataIcons.Warning,
            title = state.error.chipText(),
            body = state.error.detailText(detail.format),
            modifier = wide,
        )
        // D4.
        DocumentDetailState.OutOfIndex -> LisdataInfoCard(
            icon = LisdataIcons.Info,
            title = stringResource(R.string.document_out_of_index_title),
            body = stringResource(R.string.document_out_of_index_body),
            modifier = wide,
        )
    }
}

/** PNG 07: page chip, then the text; a citation is highlighted between its neighbouring sentences (research R7). */
@Composable
private fun PreviewCard(preview: DocumentPreview) {
    val colors = LisdataTheme.colors
    val body = LisdataTheme.typography.material.bodyLarge
    LisdataCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space3)) {
            // Never a made-up page: TXT and Markdown have none, and then there is no chip.
            preview.page?.let { LisdataCountChip(stringResource(R.string.citation_page, it)) }
            if (preview.highlighted) {
                preview.before?.let { Text(it, style = body, color = colors.textPrimary) }
                val cited = stringResource(R.string.document_cited_fragment, preview.text)
                Text(
                    text = preview.text,
                    style = body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.blue800,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.citationHighlight)
                        .padding(horizontal = LisdataTheme.spacing.space2, vertical = LisdataTheme.spacing.space1)
                        .semantics { contentDescription = cited },
                )
                preview.after?.let { Text(it, style = body, color = colors.textPrimary) }
            } else {
                Text(preview.text, style = body, color = colors.textPrimary)
            }
        }
    }
}

/**
 * The main actions of each state; side by side as in PNG 07 when both labels fit on one line, stacked
 * otherwise (narrow screens) and at large font sizes (A6).
 */
@Composable
private fun Actions(state: DocumentUiState, detail: DocumentDetail, actions: DocumentActions) {
    val enabled = !state.busy && !state.deleting
    val primary: (@Composable () -> Unit)? = when {
        detail.copyMissing -> null
        detail.state is DocumentDetailState.Failed -> if ((detail.state as DocumentDetailState.Failed).error.retryable) {
            { LisdataGradientButton(text = stringResource(R.string.document_retry), onClick = actions.onRetry, enabled = enabled, compact = true) }
        } else {
            null
        }
        detail.reindex is ReindexState.Running -> {
            { LisdataGradientButton(text = stringResource(R.string.document_reindexing_button), onClick = {}, enabled = false, compact = true) }
        }
        detail.canReindex -> {
            { LisdataGradientButton(text = stringResource(R.string.document_reindex), onClick = actions.onReindex, enabled = enabled, compact = true) }
        }
        else -> null
    }
    val delete: @Composable () -> Unit = {
        LisdataOutlinedButton(
            text = stringResource(if (state.deleting) R.string.document_deleting else R.string.document_delete),
            onClick = actions.onDelete,
            enabled = enabled,
            destructive = true,
            leadingIcon = LisdataIcons.Trash,
            compact = true,
        )
    }
    val spacing = LisdataTheme.spacing
    SideBySideOrStacked(
        first = primary,
        second = delete,
        gap = spacing.space3,
        stacked = LocalDensity.current.fontScale >= STACKED_FONT_SCALE,
        modifier = Modifier.fillMaxWidth().padding(top = spacing.space3),
    )
}

/**
 * [first] takes the rest of the row and [second] what its label needs (at least a [PRIMARY_WEIGHT] share,
 * as in PNG 07), both one line high and the same height. When their labels do not fit side by side on
 * one line, or with [stacked], each takes the whole width, one under the other.
 */
@Composable
private fun SideBySideOrStacked(
    first: (@Composable () -> Unit)?,
    second: @Composable () -> Unit,
    gap: Dp,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(first ?: {}, second), modifier = modifier) { (firsts, seconds), constraints ->
        val width = constraints.maxWidth
        val space = gap.roundToPx()
        val one = firsts.singleOrNull()
        val two = seconds.single()
        val needed = (one?.maxIntrinsicWidth(Constraints.Infinity) ?: 0) + space + two.maxIntrinsicWidth(Constraints.Infinity)
        if (one != null && !stacked && needed <= width) {
            val secondWidth = max(two.maxIntrinsicWidth(Constraints.Infinity), ((width - space) / (1 + PRIMARY_WEIGHT)).toInt())
            val firstWidth = width - space - secondWidth
            val height = max(one.minIntrinsicHeight(firstWidth), two.minIntrinsicHeight(secondWidth))
            val a = one.measure(Constraints.fixed(firstWidth, height))
            val b = two.measure(Constraints.fixed(secondWidth, height))
            layout(width, height) {
                a.place(0, 0)
                b.place(firstWidth + space, 0)
            }
        } else {
            val full = Constraints(minWidth = width, maxWidth = width)
            val a = one?.measure(full)
            val b = two.measure(full)
            val top = a?.let { it.height + space } ?: 0
            layout(width, top + b.height) {
                a?.place(0, 0)
                b.place(0, top)
            }
        }
    }
}

/** A7: state changes read politely, without moving the focus. */
@Composable
private fun Announcement(state: DocumentUiState) {
    val text = when (state.announcement) {
        null -> return
        DocumentAnnouncement.REMOVED_FROM_INDEX -> stringResource(R.string.document_announce_removed)
        DocumentAnnouncement.REINDEXING -> stringResource(R.string.document_announce_reindexing)
        DocumentAnnouncement.EXCLUDED -> stringResource(R.string.document_announce_excluded)
        DocumentAnnouncement.INCLUDED -> stringResource(R.string.document_announce_included)
    }
    Box(
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = text
        },
    )
}

@Composable
private fun Dialog(dialog: DocumentDialog?, detail: DocumentDetail, actions: DocumentActions) {
    when (dialog) {
        null -> Unit
        DocumentDialog.DELETE -> LisdataConfirmationDialog(
            title = stringResource(R.string.document_delete_title, detail.displayName),
            body = stringResource(R.string.document_delete_body),
            confirmText = stringResource(R.string.document_delete_confirm),
            onConfirm = actions.onConfirmDialog,
            onDismiss = actions.onDismissDialog,
        )
        DocumentDialog.REMOVE_FROM_INDEX -> LisdataConfirmationDialog(
            title = stringResource(R.string.document_remove_title),
            body = stringResource(R.string.document_remove_body),
            confirmText = stringResource(R.string.document_remove_confirm),
            onConfirm = actions.onConfirmDialog,
            onDismiss = actions.onDismissDialog,
        )
    }
}

private const val STACKED_FONT_SCALE = 1.5f

/** PNG 07: «Reindexar documento» takes about two thirds of the row. */
private const val PRIMARY_WEIGHT = 1.8f

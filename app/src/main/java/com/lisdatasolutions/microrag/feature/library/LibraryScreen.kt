package com.lisdatasolutions.microrag.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDocumentRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataErrorState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataFilterChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataInfoCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSearchField
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSkeletonRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import org.koin.androidx.compose.koinViewModel

/** What the stateless screen can ask for. */
data class LibraryActions(
    val onBack: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onFilterChange: (LibraryFilter) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onIncludedChange: (Long, Boolean) -> Unit = { _, _ -> },
)

@Composable
fun LibraryRoute(
    onBack: () -> Unit,
    onImport: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        actions = LibraryActions(
            onBack = onBack,
            onImport = onImport,
            onOpen = onOpenDocument,
            onTextChange = viewModel::onTextChange,
            onFilterChange = viewModel::onFilterChange,
            onClearFilters = viewModel::onClearFilters,
            onLoadMore = viewModel::onLoadMore,
            onRetry = viewModel::onRetry,
            onIncludedChange = viewModel::onIncludedChange,
        ),
    )
}

/** P06 (PNG 06 with the approved proposals B1–B8, contracts/library-ui.md). */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    // Saveable: the position survives the detail and rotation (research R9).
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        LisdataTopBar(
            title = stringResource(R.string.library_title),
            onBack = actions.onBack,
            actions = {
                IconButton(onClick = actions.onImport) {
                    LisdataIcon(LisdataIcons.Plus, contentDescription = stringResource(R.string.library_import), tint = colors.textPrimary)
                }
            },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = spacing.screenPaddingHorizontal,
                end = spacing.screenPaddingHorizontal,
                top = spacing.space2,
                bottom = spacing.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
        ) {
            item(key = "headline") { Headline() }
            if (state.showsQuery) {
                item(key = "search") {
                    LisdataSearchField(
                        query = state.text,
                        onQueryChange = actions.onTextChange,
                        placeholder = stringResource(R.string.library_search),
                    )
                }
                item(key = "filters") { Filters(state.filter, actions.onFilterChange) }
            }
            content(state.content, actions)
        }
    }
    LoadMoreNearTheEnd(listState, state.content, actions.onLoadMore)
}

@Composable
private fun Headline() {
    val colors = LisdataTheme.colors
    val typography = LisdataTheme.typography.material
    Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
        Text(
            stringResource(R.string.library_headline),
            style = typography.headlineLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.library_intro), style = typography.bodyLarge, color = colors.textSecondary)
    }
}

@Composable
private fun Filters(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    // Wraps at large font sizes instead of cutting a chip (A6).
    FlowRow(horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
        LibraryFilter.entries.forEach { filter ->
            LisdataFilterChip(
                text = stringResource(
                    when (filter) {
                        LibraryFilter.ALL -> R.string.library_filter_all
                        LibraryFilter.INDEXED -> R.string.library_filter_indexed
                        LibraryFilter.PENDING -> R.string.library_filter_pending
                    },
                ),
                selected = filter == selected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

private fun LazyListScope.content(content: LibraryContent, actions: LibraryActions) {
    when (content) {
        // B1: four skeleton rows under the filters, read as «Cargando».
        LibraryContent.Loading -> items(SKELETON_ROWS, key = { "skeleton $it" }) { LisdataSkeletonRow() }
        LibraryContent.Empty -> item(key = "empty") {
            LisdataEmptyState(
                icon = LisdataIcons.Folder,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
                action = { LisdataGradientButton(text = stringResource(R.string.library_import), onClick = actions.onImport) },
            )
        }
        LibraryContent.NoResults -> item(key = "no results") {
            LisdataEmptyState(
                icon = LisdataIcons.Search,
                title = stringResource(R.string.library_no_results_title),
                body = stringResource(R.string.library_no_results_body),
                action = { LisdataOutlinedButton(text = stringResource(R.string.library_clear_filters), onClick = actions.onClearFilters) },
            )
        }
        LibraryContent.Error -> item(key = "error") {
            LisdataErrorState(message = stringResource(R.string.library_error), onRetry = actions.onRetry)
        }
        LibraryContent.Unavailable -> item(key = "unavailable") {
            LisdataEmptyState(
                icon = LisdataIcons.Warning,
                title = stringResource(R.string.library_unavailable_title),
                body = stringResource(R.string.library_unavailable_body),
            )
        }
        is LibraryContent.Documents -> {
            item(key = "header") {
                LisdataSectionHeader(
                    text = countText(content.total),
                    modifier = Modifier.padding(top = LisdataTheme.spacing.space2),
                    trailing = {
                        Text(
                            stringResource(R.string.library_include_header),
                            style = LisdataTheme.typography.material.labelMedium,
                            color = LisdataTheme.colors.textSecondary,
                        )
                    },
                )
            }
            items(content.documents, key = { it.id }) { document -> DocumentRow(document, actions) }
            item(key = "info") {
                LisdataInfoCard(
                    icon = LisdataIcons.Info,
                    title = stringResource(R.string.library_info_title),
                    body = stringResource(R.string.library_info_body),
                    modifier = Modifier.fillMaxWidth().padding(top = LisdataTheme.spacing.space3),
                )
            }
        }
    }
}

@Composable
private fun DocumentRow(document: LibraryDocument, actions: LibraryActions) {
    val chip = document.chip()
    LisdataDocumentRow(
        name = document.displayName,
        meta = document.metaText(),
        metaAccent = document.metaAccent(),
        statusText = chip?.first,
        status = chip?.second,
        included = document.included,
        includeEnabled = document.canChangeInclusion,
        onIncludedChange = { actions.onIncludedChange(document.id, it) },
        onClick = { actions.onOpen(document.id) },
        accessibilityText = document.accessibilityText(),
        onClickLabel = stringResource(R.string.library_row_action),
        switchLabel = stringResource(R.string.library_include_switch),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Research R2: the next block is asked for when the end of the loaded rows comes into view. */
@Composable
private fun LoadMoreNearTheEnd(listState: LazyListState, content: LibraryContent, onLoadMore: () -> Unit) {
    val documents = content as? LibraryContent.Documents ?: return
    val nearTheEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= layout.totalItemsCount - LOAD_MORE_DISTANCE
        }
    }
    LaunchedEffect(nearTheEnd, documents.documents.size, documents.hasMore) {
        if (nearTheEnd && documents.hasMore) onLoadMore()
    }
}

private const val SKELETON_ROWS = 4

/** Rows from the end at which the next block is asked for: it arrives before the user gets there. */
private const val LOAD_MORE_DISTANCE = 10

package com.lisdatasolutions.microrag.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConversationRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataErrorState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSkeletonRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.component.LoadMoreNearTheEnd
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** What the stateless screen can ask for. */
data class HistoryActions(
    val onBack: () -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
)

/**
 * F13 Historial (wireframe H1–H11, contracts/history-ui.md). Opening a row or «Nuevo chat» leaves
 * Historial through [onOpenConversation] and [onNewChat], which hand the request to the chat (research R4).
 */
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewChat: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleted = stringResource(R.string.chat_announce_deleted)
    val deleteFailed = stringResource(R.string.history_delete_failed)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HistoryEvent.Open -> onOpenConversation(event.conversationId)
                HistoryEvent.NewChat -> onNewChat()
                // Consumed as it arrives, so a rotation while it shows does not repeat it.
                HistoryEvent.Deleted -> scope.launch { snackbar.showSnackbar(deleted) }
                HistoryEvent.DeleteFailed -> scope.launch { snackbar.showSnackbar(deleteFailed) }
            }
        }
    }
    // Research R3: a new day or zone shows when the screen does.
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose {}
    }
    HistoryScreen(
        state = state,
        actions = HistoryActions(
            onBack = onBack,
            onNewChat = viewModel::onNewChat,
            onOpen = viewModel::onOpen,
            onDelete = viewModel::onDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onLoadMore = viewModel::onLoadMore,
        ),
        snackbarHostState = snackbar,
    )
}

/** The list by date groups, with its empty, loading and error states (contracts/history-ui.md §1). */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    actions: HistoryActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val texts = rememberHistoryTexts(state.zone)
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
        // A phone in landscape: the empty state leaves its icon out, as the other screens.
        val compact = maxHeight < COMPACT_HEIGHT
        Column(modifier = Modifier.fillMaxSize()) {
            LisdataTopBar(
                title = stringResource(R.string.history_title),
                onBack = actions.onBack,
                actions = {
                    IconButton(onClick = actions.onNewChat) {
                        LisdataIcon(LisdataIcons.Plus, contentDescription = stringResource(R.string.chat_new), tint = colors.textPrimary)
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
                content(state, texts, actions, compact)
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(spacing.space3))
    }
    (state.content as? HistoryScreenContent.Sections)?.let { sections ->
        LoadMoreNearTheEnd(listState, loaded = sections.loaded, hasMore = sections.hasMore, onLoadMore = actions.onLoadMore)
    }
    if (state.confirmDeleteId != null) DeleteDialog(state.confirmDeleteAnswering, actions)
}

private fun LazyListScope.content(state: HistoryUiState, texts: HistoryTexts, actions: HistoryActions, compact: Boolean) {
    when (val content = state.content) {
        // H5: four skeleton rows, read as «Cargando».
        HistoryScreenContent.Loading -> items(SKELETON_ROWS, key = { "skeleton $it" }) { LisdataSkeletonRow() }
        // H4.
        HistoryScreenContent.Empty -> item(key = "empty") {
            LisdataEmptyState(
                icon = LisdataIcons.Chat,
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                action = { LisdataGradientButton(text = stringResource(R.string.chat_new), onClick = actions.onNewChat) },
                compact = compact,
            )
        }
        // H5 (D-065): the C17 text, without buttons: nothing here could fix an unreadable file.
        HistoryScreenContent.Error -> item(key = "error") {
            LisdataErrorState(message = stringResource(R.string.chat_storage_unavailable), onRetry = null, compact = compact)
        }
        is HistoryScreenContent.Sections -> content.sections.forEach { section ->
            item(key = "header ${section.group}") {
                LisdataSectionHeader(texts.header(section.group), modifier = Modifier.padding(top = LisdataTheme.spacing.space2))
            }
            items(section.entries, key = { it.id }) { entry ->
                val open = entry.id == state.openConversationId
                LisdataConversationRow(
                    title = entry.title,
                    meta = texts.meta(entry, section.group),
                    onOpen = { actions.onOpen(entry.id) },
                    onDelete = { actions.onDelete(entry.id) },
                    deleteLabel = texts.deleteLabel(entry),
                    chipText = if (open) stringResource(R.string.history_open_chip) else null,
                    accessibilityText = texts.sentence(entry, section.group, open),
                    onClickLabel = stringResource(R.string.history_open_action),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** H6: the chat's confirmation (C15), with F12's line when that conversation is being answered. */
@Composable
private fun DeleteDialog(answering: Boolean, actions: HistoryActions) {
    val body = stringResource(R.string.chat_delete_body)
    LisdataConfirmationDialog(
        title = stringResource(R.string.chat_delete_title),
        body = if (answering) body + "\n\n" + stringResource(R.string.settings_clear_body_answering) else body,
        confirmText = stringResource(R.string.chat_delete_confirm),
        onConfirm = actions.onConfirmDelete,
        onDismiss = actions.onDismissDelete,
    )
}

private const val SKELETON_ROWS = 4

/** Below this height (a phone in landscape) the empty and error states leave their icon out. */
private val COMPACT_HEIGHT = 480.dp

package com.lisdatasolutions.microrag.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOverflowMenu
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSearchField
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSkeletonRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.component.LoadMoreNearTheEnd
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import com.lisdatasolutions.microrag.feature.folders.FolderDialogs
import com.lisdatasolutions.microrag.feature.folders.FolderMessage
import com.lisdatasolutions.microrag.feature.folders.FoldersUiState
import com.lisdatasolutions.microrag.feature.folders.FoldersViewModel
import com.lisdatasolutions.microrag.feature.folders.dialogActions
import com.lisdatasolutions.microrag.feature.folders.text
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.emptyParametersHolder
import org.koin.core.parameter.parametersOf

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
    /** F17: null opens «General». */
    val onOpenFolder: (Long?) -> Unit = {},
    val onNewFolder: () -> Unit = {},
    val onRenameFolder: (Long) -> Unit = {},
    val onDeleteFolder: (Long) -> Unit = {},
)

/**
 * The Library root ([folder] null) or one folder (F17 K1, K2). [onFolderGone] leaves a folder screen
 * whose folder no longer exists, saying whether it was deleted here.
 */
@Composable
fun LibraryRoute(
    onBack: () -> Unit,
    onImport: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    message: LibraryMessage? = null,
    onMessageShown: () -> Unit = {},
    folder: LibraryFolderFilter? = null,
    onOpenFolder: (Long?) -> Unit = {},
    onFolderGone: (deleted: Boolean) -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel(parameters = { folder?.let { parametersOf(it) } ?: emptyParametersHolder() }),
    foldersViewModel: FoldersViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folders by foldersViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val folderId = (folder as? LibraryFolderFilter.Folder)?.id
    // K2: deleted from its «⋯», the folder screen goes back and the root says so.
    LaunchedEffect(folders.deletedFolder) {
        if (folderId != null && folders.deletedFolder == folderId) {
            foldersViewModel.onMessageShown()
            onFolderGone(true)
        }
    }
    val gone = folderId != null && folders.folders?.folders?.none { it.id == folderId } == true && !folders.busy
    LaunchedEffect(gone) { if (gone && folders.deletedFolder == null) onFolderGone(false) }
    val folderText = folders.message?.takeUnless { folderId != null && folders.deletedFolder == folderId }?.let { stringResource(it.text()) }
    LaunchedEffect(folders.message) {
        if (folderText == null) return@LaunchedEffect
        foldersViewModel.onMessageShown()
        snackbar.showSnackbar(folderText)
    }
    // Consumed as soon as it arrives, so a rotation while it shows does not repeat it; the snackbar
    // follows this copy, as consuming the message must not cancel it.
    var showing by remember { mutableStateOf<LibraryMessage?>(null) }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        showing = message
        onMessageShown()
    }
    val text = when (showing) {
        null -> null
        LibraryMessage.DOCUMENT_GONE -> stringResource(R.string.library_document_gone)
        LibraryMessage.DOCUMENT_DELETED -> stringResource(R.string.document_announce_deleted)
        LibraryMessage.FOLDER_DELETED -> stringResource(R.string.folder_deleted)
    }
    LaunchedEffect(showing) {
        if (text == null) return@LaunchedEffect
        snackbar.showSnackbar(text)
        showing = null
    }
    LibraryScreen(
        snackbarHostState = snackbar,
        state = state,
        folders = folders,
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
            onOpenFolder = onOpenFolder,
            onNewFolder = foldersViewModel::onNewFolder,
            onRenameFolder = foldersViewModel::onRename,
            onDeleteFolder = foldersViewModel::onDelete,
        ),
    )
    FolderDialogs(folders.dialog, folders.busy, foldersViewModel.dialogActions())
}

/** P06 (PNG 06 with the approved proposals B1–B8, contracts/library-ui.md), by folders since F17 (K1, K2). */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    folders: FoldersUiState = FoldersUiState(),
    // Saveable: the position survives the detail and rotation (research R9).
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        val folderName = state.folder?.let { folderName(it, folders) }
        Column(modifier = Modifier.fillMaxSize()) {
            LisdataTopBar(
                title = folderName ?: stringResource(R.string.library_title),
                onBack = actions.onBack,
                actions = {
                    IconButton(onClick = actions.onImport) {
                        LisdataIcon(
                            LisdataIcons.Plus,
                            contentDescription = folderName?.let { stringResource(R.string.library_folder_import, it) }
                                ?: stringResource(R.string.library_import),
                            tint = colors.textPrimary,
                        )
                    }
                    // K2: «General» is not a folder that can be renamed or deleted.
                    (state.folder as? LibraryFolderFilter.Folder)?.let { folder ->
                        LisdataOverflowMenu(
                            items = listOf(
                                stringResource(R.string.folder_rename) to { actions.onRenameFolder(folder.id) },
                                stringResource(R.string.folder_delete) to { actions.onDeleteFolder(folder.id) },
                            ),
                        )
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
                if (state.isRoot) item(key = "headline") { Headline() }
                if (state.showsQuery) {
                    item(key = "search") {
                        LisdataSearchField(
                            query = state.text,
                            onQueryChange = actions.onTextChange,
                            placeholder = folderName?.let { stringResource(R.string.library_folder_search, it) }
                                ?: stringResource(R.string.library_search),
                        )
                    }
                    // K1: the root only searches; the filters are inside each folder.
                    if (!state.isRoot) item(key = "filters") { Filters(state.filter, actions.onFilterChange) }
                }
                when {
                    !state.showsFolders -> content(state.content, actions, folderName, state.isRoot)
                    // The root without a search: the folders, under the empty state when there is nothing (D-072).
                    state.content == LibraryContent.Empty || state.content is LibraryContent.Documents ||
                        state.content == LibraryContent.NoResults -> {
                        if (state.content == LibraryContent.Empty) content(state.content, actions, folderName, state.isRoot)
                        folders(folders, actions)
                    }
                    else -> content(state.content, actions, folderName, state.isRoot)
                }
            }
        }
        // B8: «Este documento ya no existe», and A7: «Documento eliminado».
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(spacing.space3))
    }
    (state.content as? LibraryContent.Documents)?.let { documents ->
        LoadMoreNearTheEnd(listState, loaded = documents.documents.size, hasMore = documents.hasMore, onLoadMore = actions.onLoadMore)
    }
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

/** The root's folders, or the skeleton while they load; an unavailable index already says so above. */
private fun LazyListScope.folders(folders: FoldersUiState, actions: LibraryActions) {
    val list = folders.folders
    when {
        folders.unavailable -> item(key = "folders unavailable") {
            LisdataEmptyState(
                icon = LisdataIcons.Warning,
                title = stringResource(R.string.library_unavailable_title),
                body = stringResource(R.string.library_unavailable_body),
            )
        }
        list == null -> items(SKELETON_ROWS, key = { "folder skeleton $it" }) { LisdataSkeletonRow() }
        else -> item(key = "folders") {
            FoldersSection(
                folders = list,
                onOpen = actions.onOpenFolder,
                onNewFolder = actions.onNewFolder,
                modifier = Modifier.padding(top = LisdataTheme.spacing.space2),
            )
        }
    }
}

/** The top bar's name: the folder's, «General», or null while the folders load. */
@Composable
private fun folderName(folder: LibraryFolderFilter, folders: FoldersUiState): String? = when (folder) {
    LibraryFolderFilter.All -> null
    LibraryFolderFilter.Unfiled -> stringResource(R.string.library_folder_general)
    is LibraryFolderFilter.Folder -> folders.folders?.folders?.firstOrNull { it.id == folder.id }?.name
}

private fun LazyListScope.content(content: LibraryContent, actions: LibraryActions, folderName: String?, root: Boolean) {
    when (content) {
        // B1: four skeleton rows under the filters, read as «Cargando».
        LibraryContent.Loading -> items(SKELETON_ROWS, key = { "skeleton $it" }) { LisdataSkeletonRow() }
        LibraryContent.Empty -> item(key = "empty") {
            if (root) {
                LisdataEmptyState(
                    icon = LisdataIcons.Folder,
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                    action = { LisdataGradientButton(text = stringResource(R.string.library_import), onClick = actions.onImport) },
                )
            } else {
                // K2: «Esta carpeta está vacía», with «Importar a Legal».
                LisdataEmptyState(
                    icon = LisdataIcons.Folder,
                    title = stringResource(R.string.library_folder_empty_title),
                    body = stringResource(R.string.library_empty_body),
                    action = {
                        LisdataGradientButton(
                            text = folderName?.let { stringResource(R.string.library_folder_import, it) } ?: stringResource(R.string.library_import),
                            onClick = actions.onImport,
                        )
                    },
                )
            }
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
            items(content.documents, key = { it.id }) { document -> DocumentRow(document, actions, showFolder = root) }
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

/** [showFolder]: the root's search adds the folder to the data line (K1). */
@Composable
private fun DocumentRow(document: LibraryDocument, actions: LibraryActions, showFolder: Boolean) {
    val chip = document.chip()
    val folder = if (showFolder) document.folderName ?: stringResource(R.string.library_folder_general) else null
    LisdataDocumentRow(
        name = document.displayName,
        meta = folder?.let { stringResource(R.string.library_meta, document.metaText(), it) } ?: document.metaText(),
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

private const val SKELETON_ROWS = 4

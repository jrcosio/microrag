package com.lisdatasolutions.microrag.feature.chat.scope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomSheet
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCheckRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataExpandableRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataRadioRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSheetContent
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSkeletonRow
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.DocumentRef

/** What the «Buscar en» sheet can ask for; a folder id null is «General». */
data class ScopeSheetActions(
    val onWholeLibrary: () -> Unit = {},
    val onToggleFolder: (Long?) -> Unit = {},
    val onToggleDocument: (Long?, DocumentRef) -> Unit = { _, _ -> },
    val onExpand: (Long?) -> Unit = {},
    val onLoadMore: (Long?) -> Unit = {},
    val onApply: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

fun ScopeSelectorViewModel.sheetActions(onApply: () -> Unit, onDismiss: () -> Unit) = ScopeSheetActions(
    onWholeLibrary = ::onWholeLibrary,
    onToggleFolder = ::onToggleFolder,
    onToggleDocument = ::onToggleDocument,
    onExpand = ::onExpand,
    onLoadMore = ::onLoadMore,
    onApply = onApply,
    onDismiss = onDismiss,
)

/** K8 (wireframe §6): the whole library, or whole folders and loose documents. */
@Composable
fun ScopeSheet(state: ScopeSelectorUiState, actions: ScopeSheetActions) {
    LisdataBottomSheet(onDismiss = actions.onDismiss) {
        ScopeSheetContent(state, actions)
    }
}

/** The sheet's content, separate so previews and tests show it without a window. */
@Composable
fun ScopeSheetContent(state: ScopeSelectorUiState, actions: ScopeSheetActions, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    LisdataSheetContent(modifier = modifier) {
        Text(
            stringResource(R.string.chat_scope_sheet_title),
            style = LisdataTheme.typography.material.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        LisdataCardGroup {
            LisdataRadioRow(title = stringResource(R.string.chat_scope_whole), selected = state.whole, onSelect = actions.onWholeLibrary)
        }
        LisdataSectionHeader(text = stringResource(R.string.chat_scope_folders), modifier = Modifier.semantics { heading() })
        val folders = state.folders
        if (folders == null) {
            LisdataSkeletonRow()
        } else if (folders.isNotEmpty()) {
            LisdataCardGroup {
                folders.forEachIndexed { i, folder ->
                    if (i > 0) LisdataDivider()
                    FolderRows(folder, actions)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space3)) {
            LisdataOutlinedButton(text = stringResource(R.string.action_cancel), onClick = actions.onDismiss, modifier = Modifier.weight(1f))
            LisdataGradientButton(
                text = stringResource(R.string.chat_scope_apply),
                onClick = actions.onApply,
                enabled = state.canApply,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** K13: «Motores, 5 documentos, algunos documentos»; its documents under it while open. */
@Composable
private fun FolderRows(folder: ScopeFolderRow, actions: ScopeSheetActions) {
    LisdataExpandableRow(
        title = folder.name ?: stringResource(R.string.library_folder_general),
        subtitle = pluralStringResource(R.plurals.library_folder_documents, folder.count, folder.count),
        checked = folder.check,
        onToggle = { actions.onToggleFolder(folder.id) },
        expanded = folder.expanded,
        onExpand = { actions.onExpand(folder.id) },
        expandLabel = stringResource(R.string.chat_scope_show_documents),
        expandedText = stringResource(R.string.chat_scope_expanded),
        collapsedText = stringResource(R.string.chat_scope_collapsed),
        enabled = folder.selectable,
        stateText = stringResource(R.string.chat_scope_some).takeIf { folder.check == ToggleableState.Indeterminate },
    )
    if (!folder.expanded) return
    val documents = folder.documents
    if (documents == null) {
        LisdataSkeletonRow()
        return
    }
    documents.forEach { document ->
        LisdataCheckRow(
            title = document.name,
            checked = if (document.checked) ToggleableState.On else ToggleableState.Off,
            onToggle = { actions.onToggleDocument(folder.id, document.ref) },
            subtitle = when (document.note) {
                null -> null
                ScopeNote.NOT_INCLUDED -> stringResource(R.string.chat_scope_not_included)
                ScopeNote.PENDING -> stringResource(R.string.chat_scope_pending)
            },
            enabled = document.selectable,
        )
    }
    // In blocks, as the Library: the next one when the last row shows.
    if (folder.hasMore) LaunchedEffect(documents.size) { actions.onLoadMore(folder.id) }
}

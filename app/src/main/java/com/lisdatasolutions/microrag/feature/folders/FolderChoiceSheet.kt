package com.lisdatasolutions.microrag.feature.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomSheet
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataRadioRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSettingsRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSheetContent
import com.lisdatasolutions.microrag.core.designsystem.component.SettingsTrailing
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.FolderList

/** What a folder sheet can ask for; [onSelect] and [onConfirm] get null for «General». */
data class FolderChoiceActions(
    val onSelect: (Long?) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onNewFolder: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * K5 and K6 (contract UI §2): the folders and «General» as options, with «Nueva carpeta…». [actionText] is
 * «Mover» in the detail and «Elegir» in the Ingest (D-072).
 */
@Composable
fun FolderChoiceSheet(
    folders: FolderList,
    selected: Long?,
    title: String,
    actionText: String,
    actions: FolderChoiceActions,
) {
    LisdataBottomSheet(onDismiss = actions.onDismiss) {
        FolderChoiceContent(folders, selected, title, actionText, actions)
    }
}

/** The sheet's content, separate so previews and tests show it without a window. */
@Composable
fun FolderChoiceContent(
    folders: FolderList,
    selected: Long?,
    title: String,
    actionText: String,
    actions: FolderChoiceActions,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    LisdataSheetContent(modifier = modifier) {
        Text(title, style = LisdataTheme.typography.material.titleLarge, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        LisdataCardGroup {
            folders.folders.forEach { folder ->
                LisdataRadioRow(title = folder.name, selected = selected == folder.id, onSelect = { actions.onSelect(folder.id) })
                LisdataDivider()
            }
            LisdataRadioRow(
                title = stringResource(R.string.library_folder_general),
                selected = selected == null,
                onSelect = { actions.onSelect(null) },
            )
            LisdataDivider()
            LisdataSettingsRow(
                icon = LisdataIcons.Plus,
                title = stringResource(R.string.folder_sheet_new),
                trailing = SettingsTrailing.None,
                onClick = actions.onNewFolder,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space3)) {
            LisdataOutlinedButton(text = stringResource(R.string.action_cancel), onClick = actions.onDismiss, modifier = Modifier.weight(1f))
            LisdataGradientButton(text = actionText, onClick = actions.onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

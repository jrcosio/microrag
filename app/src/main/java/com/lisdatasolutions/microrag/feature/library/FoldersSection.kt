package com.lisdatasolutions.microrag.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSettingsRow
import com.lisdatasolutions.microrag.core.designsystem.component.SettingsTrailing
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.FolderList

/**
 * K1 (wireframe §1): the folders by name with their documents, «General» last and only with documents,
 * and «Nueva carpeta». [onOpen] gets null for «General».
 */
@Composable
fun FoldersSection(
    folders: FolderList,
    onOpen: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
        LisdataSectionHeader(
            text = stringResource(R.string.library_folders_header),
            modifier = Modifier.semantics { heading() },
        )
        LisdataCardGroup {
            folders.folders.forEach { folder ->
                FolderRow(name = folder.name, documents = folder.documentCount, onClick = { onOpen(folder.id) })
                LisdataDivider()
            }
            if (folders.unfiledCount > 0) {
                FolderRow(name = stringResource(R.string.library_folder_general), documents = folders.unfiledCount, onClick = { onOpen(null) })
                LisdataDivider()
            }
            LisdataSettingsRow(
                icon = LisdataIcons.Plus,
                title = stringResource(R.string.library_folder_new),
                trailing = SettingsTrailing.None,
                onClick = onNewFolder,
            )
        }
    }
}

/** U2: «Legal, 12 documentos», with «Abrir carpeta». */
@Composable
private fun FolderRow(name: String, documents: Int, onClick: () -> Unit) {
    LisdataSettingsRow(
        icon = LisdataIcons.Folder,
        title = name,
        subtitle = pluralStringResource(R.plurals.library_folder_documents, documents, documents),
        onClickLabel = stringResource(R.string.library_folder_open),
        onClick = onClick,
    )
}

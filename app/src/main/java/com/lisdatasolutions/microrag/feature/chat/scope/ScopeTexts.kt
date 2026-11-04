package com.lisdatasolutions.microrag.feature.chat.scope

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R

/** The chip's summary (FR-014): the folders by name, «General» last, and how many loose documents. */
data class ScopeSummary(
    val whole: Boolean,
    /** Null is «General». */
    val folderNames: List<String?> = emptyList(),
    val looseDocuments: Int = 0,
)

/** «Toda la biblioteca», «Legal», «Legal y Motores», «Legal + 2 documentos», «3 documentos». */
@Composable
fun ScopeSummary.text(): String {
    if (whole) return stringResource(R.string.chat_scope_whole)
    val general = stringResource(R.string.library_folder_general)
    val names = folderNames.map { it ?: general }
    val folders = when (names.size) {
        0 -> null
        1 -> names.single()
        else -> stringResource(R.string.chat_scope_two, names.dropLast(1).joinToString(", "), names.last())
    }
    return when {
        folders == null -> pluralStringResource(R.plurals.chat_scope_documents, looseDocuments, looseDocuments)
        looseDocuments == 0 -> folders
        else -> pluralStringResource(R.plurals.chat_scope_plus_documents, looseDocuments, folders, looseDocuments)
    }
}

package com.lisdatasolutions.microrag.feature.library

import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryFilter

/** P06 state (F08 contracts/library-ui.md): the query as typed and what it found. */
data class LibraryUiState(
    val text: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val content: LibraryContent = LibraryContent.Loading,
) {
    /** B2 shows no search or filters: there is nothing to search yet. */
    val showsQuery: Boolean get() = content != LibraryContent.Empty
}

sealed interface LibraryContent {
    /** B1. */
    data object Loading : LibraryContent

    /** B2: nothing imported yet. */
    data object Empty : LibraryContent

    /** B3: documents exist, but not with this search or filter. */
    data object NoResults : LibraryContent

    /** [documents] are the first blocks of the [total] matches; [hasMore] asks for the next one near the end. */
    data class Documents(val documents: List<LibraryDocument>, val total: Int, val hasMore: Boolean) : LibraryContent

    /** B4: reading failed; «Reintentar» observes again. */
    data object Error : LibraryContent

    /** B4: the index is incompatible or unreadable (F06 P5): nothing to retry. */
    data object Unavailable : LibraryContent
}

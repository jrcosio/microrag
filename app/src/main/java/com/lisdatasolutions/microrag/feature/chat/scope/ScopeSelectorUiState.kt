package com.lisdatasolutions.microrag.feature.chat.scope

import androidx.compose.ui.state.ToggleableState
import com.lisdatasolutions.microrag.domain.rag.DocumentRef

/** The «Buscar en» sheet (K8, data-model §4). */
data class ScopeSelectorUiState(
    /** Null while the folders load. */
    val folders: List<ScopeFolderRow>? = null,
    val whole: Boolean = true,
    val canApply: Boolean = true,
)

/** A folder that has documents, or «General» with [id] null; empty folders are not offered (wireframe §6). */
data class ScopeFolderRow(
    val id: Long?,
    /** Null is «General». */
    val name: String?,
    val count: Int,
    val check: ToggleableState,
    /** A folder whose documents are all excluded cannot be checked (D-072). */
    val selectable: Boolean,
    val expanded: Boolean,
    /** Its documents while expanded, in blocks; null while they load. */
    val documents: List<ScopeDocumentRow>?,
    val hasMore: Boolean,
)

data class ScopeDocumentRow(
    val ref: DocumentRef,
    val name: String,
    val checked: Boolean,
    /** Excluded from answers: grey and never checked (K8, clarify Q4). */
    val selectable: Boolean,
    val note: ScopeNote?,
)

enum class ScopeNote { NOT_INCLUDED, PENDING }

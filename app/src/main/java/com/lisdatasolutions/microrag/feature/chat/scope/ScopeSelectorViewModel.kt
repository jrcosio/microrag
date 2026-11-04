package com.lisdatasolutions.microrag.feature.chat.scope

import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.library.FolderDocumentRefsUseCase
import com.lisdatasolutions.microrag.domain.library.FoldersResult
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryPage
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.LibraryResult
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveLibraryUseCase
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.domain.rag.FolderRef
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * F17 K8 (contract UI §3): the «Buscar en» sheet. What is checked and which folders are open live in the
 * [SavedStateHandle], so rotating or a process death keep them. A whole folder is checked by its id, so it
 * takes what arrives later (FR-016); unchecking one of its documents turns the rest into loose documents (M6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScopeSelectorViewModel(
    private val savedState: SavedStateHandle,
    observeFolders: ObserveFoldersUseCase,
    private val observeLibrary: ObserveLibraryUseCase,
    private val folderDocuments: FolderDocumentRefsUseCase,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val selection = savedState.getStateFlow<String?>(KEY_SELECTION, null)

    /** Open folders and how many of their documents are loaded: «g:50,3:100». */
    private val expanded = savedState.getStateFlow(KEY_EXPANDED, "")

    private val folders: StateFlow<FolderList?> = observeFolders()
        .map { (it as? FoldersResult.Loaded)?.folders }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(null)
        }
        .flowOn(backgroundDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Every document of each folder, so a closed folder still knows it is partly checked. */
    private val members: StateFlow<Map<Long?, List<DocumentRef>>> = folders.filterNotNull()
        .mapLatest { list ->
            buildMap<Long?, List<DocumentRef>> {
                list.folders.filter { it.documentCount > 0 }.forEach { put(it.id, folderDocuments(it.id).orEmpty()) }
                if (list.unfiledCount > 0) put(null, folderDocuments(null).orEmpty())
            }
        }
        .flowOn(backgroundDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val pages: Flow<Map<Long?, LibraryPage?>> = expanded.map(::decodeExpanded).flatMapLatest { open ->
        if (open.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                open.map { (key, limit) ->
                    observeLibrary(LibraryQuery(limit = limit, folder = key?.let { LibraryFolderFilter.Folder(it) } ?: LibraryFolderFilter.Unfiled))
                        .map { key to (it as? LibraryResult.Loaded)?.page }
                        .catch { error ->
                            if (error is CancellationException) throw error
                            emit(key to null)
                        }
                },
            ) { it.toMap() }
        }
    }.flowOn(backgroundDispatcher)

    val state: StateFlow<ScopeSelectorUiState> = combine(folders, selection, expanded, pages, members) { list, saved, open, pages, members ->
        val chosen = ScopeSelection.decode(saved) ?: ScopeSelection(whole = true)
        ScopeSelectorUiState(
            folders = list?.let { rows(it, chosen, decodeExpanded(open), pages, members) },
            whole = chosen.whole,
            canApply = chosen.toScope() != null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScopeSelectorUiState())

    /** Opening the sheet starts from the chat's scope, with every folder closed. */
    fun start(scope: ChatScope) {
        savedState[KEY_SELECTION] = ScopeSelection.of(scope).encode()
        savedState[KEY_EXPANDED] = ""
    }

    /** Wireframe §6: «Toda la biblioteca» unchecks everything else. */
    fun onWholeLibrary() = save(ScopeSelection(whole = true))

    /** [folderId] null is «General». */
    fun onToggleFolder(folderId: Long?) {
        val row = state.value.folders?.firstOrNull { it.id == folderId } ?: return
        if (!row.selectable) return
        val current = current().withoutWhole()
        val inside = members.value[folderId].orEmpty().toSet()
        val loose = current.documents - inside
        save(
            if (row.check == ToggleableState.On) {
                current.copy(folders = current.folders.filterNot { it.id == folderId }.toSet(), general = current.general && folderId != null, documents = loose)
            } else if (folderId == null) {
                current.copy(general = true, documents = loose)
            } else {
                val ref = folders.value?.folders?.firstOrNull { it.id == folderId }?.let { FolderRef(it.id, it.createdAtMs) } ?: return
                current.copy(folders = current.folders + ref, documents = loose)
            },
        )
    }

    /** M6 (D-072): a document of a whole folder unchecked leaves the rest checked, as loose documents. */
    fun onToggleDocument(folderId: Long?, document: DocumentRef) {
        val current = current().withoutWhole()
        val whole = if (folderId == null) current.general else current.folders.any { it.id == folderId }
        save(
            when {
                whole -> current.copy(
                    folders = current.folders.filterNot { it.id == folderId }.toSet(),
                    general = current.general && folderId != null,
                    documents = current.documents + (members.value[folderId].orEmpty().toSet() - document),
                )
                document in current.documents -> current.copy(documents = current.documents - document)
                else -> current.copy(documents = current.documents + document)
            },
        )
    }

    fun onExpand(folderId: Long?) {
        val open = decodeExpanded(expanded.value)
        savedState[KEY_EXPANDED] = encodeExpanded(if (folderId in open) open - folderId else open + (folderId to LibraryQuery.PAGE))
    }

    /** Near the end of an open folder: one more block (as the Library, research R2). */
    fun onLoadMore(folderId: Long?) {
        val open = decodeExpanded(expanded.value)
        val limit = open[folderId] ?: return
        savedState[KEY_EXPANDED] = encodeExpanded(open + (folderId to limit + LibraryQuery.PAGE))
    }

    /** What «Aplicar» gives the chat; null while nothing is checked. */
    fun result(): ChatScope? = current().toScope()

    private fun current(): ScopeSelection = ScopeSelection.decode(selection.value) ?: ScopeSelection(whole = true)

    private fun ScopeSelection.withoutWhole() = if (whole) ScopeSelection(whole = false) else this

    private fun save(selection: ScopeSelection) {
        savedState[KEY_SELECTION] = selection.encode()
    }

    private fun rows(
        list: FolderList,
        chosen: ScopeSelection,
        open: Map<Long?, Int>,
        pages: Map<Long?, LibraryPage?>,
        members: Map<Long?, List<DocumentRef>>,
    ): List<ScopeFolderRow> {
        val named = list.folders.filter { it.documentCount > 0 }.map { Triple<Long?, String?, Pair<Int, Int>>(it.id, it.name, it.documentCount to it.includedCount) }
        val general = if (list.unfiledCount > 0) listOf(Triple<Long?, String?, Pair<Int, Int>>(null, null, list.unfiledCount to list.unfiledIncludedCount)) else emptyList()
        return (named + general).map { (id, name, counts) ->
            val whole = if (id == null) chosen.general else chosen.folders.any { it.id == id }
            val inside = members[id].orEmpty()
            val check = when {
                chosen.whole -> ToggleableState.Off
                whole -> ToggleableState.On
                inside.any { it in chosen.documents } -> ToggleableState.Indeterminate
                else -> ToggleableState.Off
            }
            val page = pages[id]
            ScopeFolderRow(
                id = id,
                name = name,
                count = counts.first,
                check = check,
                selectable = counts.second > 0,
                expanded = id in open,
                documents = if (id in open) page?.documents?.map { it.row(whole && !chosen.whole, chosen) } else null,
                hasMore = id in open && page?.hasMore == true,
            )
        }
    }

    private fun LibraryDocument.row(folderChecked: Boolean, chosen: ScopeSelection): ScopeDocumentRow {
        val ref = DocumentRef(id, createdAtMs)
        return ScopeDocumentRow(
            ref = ref,
            name = displayName,
            checked = included && (folderChecked || ref in chosen.documents),
            selectable = included,
            note = when {
                !included -> ScopeNote.NOT_INCLUDED
                state !is LibraryDocumentState.Indexed -> ScopeNote.PENDING
                else -> null
            },
        )
    }

    private fun decodeExpanded(text: String): Map<Long?, Int> = text.split(",").filter { it.isNotEmpty() }.mapNotNull { item ->
        val (key, limit) = item.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        val id: Long? = if (key == GENERAL) null else key.toLongOrNull() ?: return@mapNotNull null
        id to (limit.toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

    private fun encodeExpanded(open: Map<Long?, Int>): String = open.entries.joinToString(",") { (id, limit) -> "${id ?: GENERAL}:$limit" }

    private companion object {
        const val KEY_SELECTION = "scope_selection"
        const val KEY_EXPANDED = "scope_expanded"
        const val GENERAL = "g"
    }
}

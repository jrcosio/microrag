package com.lisdatasolutions.microrag.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.LibraryResult
import com.lisdatasolutions.microrag.domain.library.ObserveLibraryUseCase
import com.lisdatasolutions.microrag.domain.library.SetIncludedUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * P06 Biblioteca (F08). The query (text, filter and how many blocks are loaded) lives in the
 * [SavedStateHandle], so going to the detail and back, rotating or a process death keep it (research R9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val savedState: SavedStateHandle,
    private val observeLibrary: ObserveLibraryUseCase,
    private val setIncluded: SetIncludedUseCase,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val text = savedState.getStateFlow(KEY_TEXT, "")
    private val filter = savedState.getStateFlow(KEY_FILTER, LibraryFilter.ALL.name)
    private val limit = savedState.getStateFlow(KEY_LIMIT, LibraryQuery.PAGE)

    /** Bumped by «Reintentar»: observes again, showing the skeleton meanwhile. */
    private val retries = MutableStateFlow(0)
    private var shownRetry = 0

    private val query: Flow<LibraryQuery> = combine(text, filter, limit) { text, filter, limit ->
        LibraryQuery(text, LibraryFilter.valueOf(filter), limit)
    }

    private val content: Flow<LibraryContent> = combine(query, retries) { query, retry -> query to retry }
        .flatMapLatest { (query, retry) ->
            val retried = retry != shownRetry
            shownRetry = retry
            observeLibrary(query)
                .map { result -> result.toContent(query) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(LibraryContent.Error)
                }
                .let { flow -> if (retried) flow.onStart { emit(LibraryContent.Loading) } else flow }
        }

    val state: StateFlow<LibraryUiState> = combine(text, filter, content) { text, filter, content ->
        LibraryUiState(text, LibraryFilter.valueOf(filter), content)
    }.flowOn(backgroundDispatcher).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        LibraryUiState(text.value, LibraryFilter.valueOf(filter.value)),
    )

    fun onTextChange(value: String) {
        savedState[KEY_TEXT] = value
        savedState[KEY_LIMIT] = LibraryQuery.PAGE
    }

    fun onFilterChange(value: LibraryFilter) {
        savedState[KEY_FILTER] = value.name
        savedState[KEY_LIMIT] = LibraryQuery.PAGE
    }

    /** B3: back to every document. */
    fun onClearFilters() {
        savedState[KEY_TEXT] = ""
        savedState[KEY_FILTER] = LibraryFilter.ALL.name
        savedState[KEY_LIMIT] = LibraryQuery.PAGE
    }

    /** Near the end of the list: one more block, once the current one has arrived (research R2). */
    fun onLoadMore() {
        val shown = state.value.content as? LibraryContent.Documents ?: return
        if (!shown.hasMore || shown.documents.size < limit.value) return
        savedState[KEY_LIMIT] = limit.value + LibraryQuery.PAGE
    }

    fun onRetry() {
        retries.value += 1
    }

    private fun LibraryResult.toContent(query: LibraryQuery): LibraryContent = when (this) {
        LibraryResult.Unavailable -> LibraryContent.Unavailable
        is LibraryResult.Loaded -> when {
            page.total > 0 -> LibraryContent.Documents(page.documents, page.total, page.hasMore)
            query.text.isBlank() && query.filter == LibraryFilter.ALL -> LibraryContent.Empty
            else -> LibraryContent.NoResults
        }
    }

    private companion object {
        const val KEY_TEXT = "library_text"
        const val KEY_FILTER = "library_filter"
        const val KEY_LIMIT = "library_limit"
    }
}

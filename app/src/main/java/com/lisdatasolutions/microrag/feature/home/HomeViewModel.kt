package com.lisdatasolutions.microrag.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveLibrarySummaryUseCase
import com.lisdatasolutions.microrag.domain.library.SummaryResult
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** P03 (contracts/home-api.md): three independent sources, each shown as soon as it arrives (FR-025). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    observeLocalAi: ObserveLocalAiUseCase,
    private val refreshLocalAi: RefreshLocalAiUseCase,
    private val observeSummary: ObserveLibrarySummaryUseCase,
    private val logger: AppLogger,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {

    private val openedAt = clock()
    private var aiLogged = false

    /** Rule H1: null until the first check ends; later checks keep the last status until a new one (FR-009). */
    private val ai: StateFlow<LocalAiStatus?> = observeLocalAi()
        .onEach { status ->
            if (!aiLogged) {
                aiLogged = true
                logger.i(TAG) { "home ai=$status in ${clock() - openedAt} ms" }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Bumped by «Reintentar» (rule H7): observes the summary again, showing the skeleton meanwhile. */
    private val summaryRetries = MutableStateFlow(0)
    private var summaryLogged = false

    private val summary: StateFlow<SummaryState> = summaryRetries
        .flatMapLatest { retry ->
            observeSummary()
                .map { result ->
                    when (result) {
                        is SummaryResult.Loaded -> result.summary.toState()
                        SummaryResult.Unavailable -> SummaryState.Error(unavailable = true)
                    }
                }
                .onStart { if (retry > 0) emit(SummaryState.Loading) }
                .catch { error ->
                    logger.w(TAG, null) { "home summary failed: ${error::class.simpleName}" }
                    emit(SummaryState.Error(unavailable = false))
                }
        }
        .onEach { summary ->
            if (!summaryLogged && (summary is SummaryState.Loaded || summary == SummaryState.Empty)) {
                summaryLogged = true
                val (documents, chunks) = (summary as? SummaryState.Loaded)?.let { it.documents to it.chunks } ?: (0 to 0)
                logger.i(TAG) { "home summary documents=$documents chunks=$chunks in ${clock() - openedAt} ms" }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SummaryState.Loading)

    val state: StateFlow<HomeUiState> = combine(ai, summary) { ai, summary -> HomeUiState(ai = ai, summary = summary) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun onRetrySummary() {
        summaryRetries.value++
    }

    /** Research R5: back to the foreground, or back from another screen. */
    fun onResume() {
        viewModelScope.launch { refreshLocalAi() }
    }

    /** FR-015: 0 documents is the empty state, never «0 documentos». */
    private fun LibrarySummary.toState(): SummaryState =
        if (documents == 0) SummaryState.Empty else SummaryState.Loaded(documents = documents, chunks = indexedChunks)

    private companion object {
        const val TAG = "MicroRAG"
    }
}

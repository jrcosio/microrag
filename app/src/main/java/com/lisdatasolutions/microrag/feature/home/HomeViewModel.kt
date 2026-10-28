package com.lisdatasolutions.microrag.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** P03 (contracts/home-api.md): three independent sources, each shown as soon as it arrives (FR-025). */
class HomeViewModel(
    observeLocalAi: ObserveLocalAiUseCase,
    private val refreshLocalAi: RefreshLocalAiUseCase,
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

    val state: StateFlow<HomeUiState> = ai
        .map { HomeUiState(ai = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    /** Research R5: back to the foreground, or back from another screen. */
    fun onResume() {
        viewModelScope.launch { refreshLocalAi() }
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}

package com.lisdatasolutions.microrag.debug.retrieval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.debug.retrieval.RetrievalDiagnosticUiState.Status
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs the app's retrieval on the phone's real library (rule V2). It only launches the search, which
 * runs on the database and embeddings threads (V4), and logs nothing: the text stays on screen (V3).
 * F20 (research R17): with the multimodal mode on, [withPages] makes it search the pages too, as the chat does.
 */
class RetrievalDiagnosticViewModel(
    private val retrieveContext: RetrieveContextUseCase,
    private val withPages: suspend () -> Boolean = { false },
) : ViewModel() {

    private val _state = MutableStateFlow(RetrievalDiagnosticUiState())
    val state: StateFlow<RetrievalDiagnosticUiState> = _state.asStateFlow()

    fun onQuestionChange(text: String) = _state.update { it.copy(question = text) }

    fun onPreviousChange(text: String) = _state.update { it.copy(previousQuestion = text) }

    /** Ignored while a search runs. */
    fun onSearch() {
        val current = _state.value
        if (!current.canSearch) return
        _state.update { it.copy(status = Status.Searching) }
        viewModelScope.launch {
            val result = retrieveContext(current.question, current.previousQuestion.ifBlank { null }, withPages = withPages())
            _state.update { it.copy(status = Status.Done(result)) }
        }
    }
}

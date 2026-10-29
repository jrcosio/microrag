package com.lisdatasolutions.microrag.debug.retrieval

import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult

/** The retrieval diagnostic (F10 contracts/retrieval-diagnostic.md): two fields and the last result. */
data class RetrievalDiagnosticUiState(
    val question: String = "",
    val previousQuestion: String = "",
    val status: Status = Status.Idle,
) {
    sealed interface Status {
        data object Idle : Status

        data object Searching : Status

        data class Done(val result: RetrievalResult) : Status
    }

    val canSearch: Boolean get() = status != Status.Searching
}

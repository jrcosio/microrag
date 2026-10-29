package com.lisdatasolutions.microrag.domain.retrieval

/** Hybrid retrieval of the context of a question (F10 contracts/retrieval-api.md). */
interface ContextRetriever {
    /** Never throws but CancellationException: every failure is an outcome (FR-016). */
    suspend fun retrieve(request: RetrievalRequest): RetrievalResult
}

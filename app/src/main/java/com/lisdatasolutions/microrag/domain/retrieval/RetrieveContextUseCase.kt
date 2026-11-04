package com.lisdatasolutions.microrag.domain.retrieval

import com.lisdatasolutions.microrag.domain.rag.SearchScope

/** The context of a question for the chat (F11) and the debug diagnostic (F10), within [SearchScope] (F17). */
class RetrieveContextUseCase(private val retriever: ContextRetriever) {
    suspend operator fun invoke(question: String, previousQuestion: String? = null, scope: SearchScope = SearchScope.All): RetrievalResult =
        retriever.retrieve(RetrievalRequest(question, previousQuestion, scope))
}

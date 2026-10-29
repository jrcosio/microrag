package com.lisdatasolutions.microrag.domain.retrieval

/** The context of a question for the chat (F11) and the debug diagnostic (F10). */
class RetrieveContextUseCase(private val retriever: ContextRetriever) {
    suspend operator fun invoke(question: String, previousQuestion: String? = null): RetrievalResult =
        retriever.retrieve(RetrievalRequest(question, previousQuestion))
}

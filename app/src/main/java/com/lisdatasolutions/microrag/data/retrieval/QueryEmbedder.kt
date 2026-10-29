package com.lisdatasolutions.microrag.data.retrieval

import com.lisdatasolutions.microrag.rag.runtime.QueryEmbedding

/** The query side of the embedding model (F10 research R4); an interface so instrumented tests can use a deterministic one. */
interface QueryEmbedder {
    /** The model is verified and prepared (F03 rule), after one scan of the models folder without hashing when its path is unknown. */
    suspend fun ready(): Boolean

    /** Embeds [question] after [previous] (null: alone), cutting it when too long. Throws on any engine failure. */
    suspend fun embed(question: String, previous: String?): QueryEmbedding

    /** Whether the query engine is loaded now, for RetrievalTimings.coldEngine. */
    val loaded: Boolean
}

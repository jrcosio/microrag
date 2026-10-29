package com.lisdatasolutions.microrag.domain.retrieval

import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility

// The context of one question (F10 data-model §2–§3): what the chat (F11) puts in the prompt and the
// debug diagnostic shows. Ids and numbers only, but for the blocks' text.

data class RetrievalRequest(
    val question: String,
    /** Given by the caller (F11); the retriever keeps no conversation (FR-009). */
    val previousQuestion: String? = null,
)

data class RetrievalResult(
    /** What the lexical search received: the question, or the previous one and the question for a follow-up. */
    val searchText: String,
    /** Whether the follow-up rule joined the previous question to this one. */
    val followUp: Boolean,
    /** Cuts applied to the embedded text when the model rejected it as too long (F10 R4); 0 when it fitted. */
    val embeddingCuts: Int,
    val timings: RetrievalTimings,
    val outcome: RetrievalOutcome,
)

sealed interface RetrievalOutcome {
    /** At most MAX_BLOCKS blocks, ordered by document and ordinal; [candidates] are the first FUSED_LIMIT of the fusion. */
    data class Blocks(val blocks: List<ContextBlock>, val candidates: List<CandidateScore>) : RetrievalOutcome

    /** Neither search found a searchable chunk. */
    data object NoResults : RetrievalOutcome

    data object EmptyQuestion : RetrievalOutcome

    /** The index cannot be searched (F04 A2); nothing was embedded. */
    data class IndexUnavailable(val state: IndexCompatibility) : RetrievalOutcome

    /** The embedding model is not verified and prepared (F03 rule); nothing was loaded. */
    data object ModelsNotReady : RetrievalOutcome

    /** Loading the query engine or computing the query vector failed. */
    data object EmbeddingFailed : RetrievalOutcome
}

/** Consecutive chunks of one document, merged without the chunker overlap (constitution III). */
data class ContextBlock(
    val documentId: Long,
    val documentName: String,
    /** Of the first chunk. */
    val headingPath: String?,
    /** Of the first chunk. */
    val page: Int?,
    val firstOrdinal: Int,
    val lastOrdinal: Int,
    /** The chunks it covers, in ordinal order, so the answer cites real chunks. */
    val chunkIds: List<Long>,
    /** Stored content without the contextual header. */
    val text: String,
    /** Sum of the chunks' token estimates. */
    val tokens: Int,
)

/** One fused candidate and where it came from, for the diagnostic. */
data class CandidateScore(
    val chunkId: Long,
    /** 1-based, in fusion order. */
    val fusedRank: Int,
    val rrfScore: Double,
    /** 1-based; null when the vector search did not find it. */
    val vectorRank: Int?,
    val vectorDistance: Double?,
    /** 1-based; null when the lexical search did not find it. */
    val textRank: Int?,
    /** Whether its chunk is in a block. */
    val selected: Boolean,
)

/** Milliseconds; steps that did not run count 0. */
data class RetrievalTimings(
    /** Includes loading the engine when [coldEngine]. */
    val embeddingMs: Double = 0.0,
    val vectorMs: Double = 0.0,
    val textMs: Double = 0.0,
    /** Fusion, loading the candidates and selection. */
    val selectionMs: Double = 0.0,
    /** Wall clock: less than the sum, the lexical search overlaps the embedding. */
    val totalMs: Double = 0.0,
    /** The query engine was not loaded when the retrieval started. */
    val coldEngine: Boolean = false,
)

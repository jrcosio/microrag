package com.lisdatasolutions.microrag.domain.indexing

import com.lisdatasolutions.microrag.domain.ingest.IngestError

/** What the Index screen shows (F07 data-model §3, contracts/index-ui.md). */
sealed interface IndexingState {
    data object Loading : IndexingState

    /** The local library is incompatible or unreadable (F04): nothing can be indexed. */
    data object IndexUnavailable : IndexingState

    /** Nothing to index; [failed] lists the documents in error, if any. */
    data class Idle(val failed: List<FailedDocument>) : IndexingState

    data class Active(
        val progress: RunProgress,
        /** Null between documents or before the first one. */
        val current: CurrentDocument?,
        /** Documents of the run still to start, not counting the current one. */
        val queuedDocuments: Int,
        /** Null while running. */
        val pause: PauseReason?,
        val failed: List<FailedDocument>,
    ) : IndexingState
}

/**
 * Stored chunks over the run's total (F07 research R8). [total] is null while some document of the run
 * is still to be chunked; [remainingSeconds] only comes from a steady rate measured on this phone.
 */
data class RunProgress(val done: Int, val total: Int?, val remainingSeconds: Long?)

data class CurrentDocument(val id: Long, val displayName: String, val stage: Stage) {
    /** The PNG 08 stages map to these (data-model §3, G29). */
    enum class Stage { EXTRACTING, CHUNKING, EMBEDDING, CLOSING }
}

/** Why the run is not advancing (data-model §7). */
enum class PauseReason { USER, THERMAL, NO_SPACE, MODEL_MISSING, SYSTEM_LIMIT }

data class FailedDocument(val id: Long, val displayName: String, val error: IngestError) {
    val canRetry: Boolean get() = error.retryable
}

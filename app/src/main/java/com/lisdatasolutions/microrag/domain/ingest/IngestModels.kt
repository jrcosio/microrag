package com.lisdatasolutions.microrag.domain.ingest

import com.lisdatasolutions.microrag.domain.model.ComputeBackend

/** Formats of the MVP (constitution, technical constraints). [extension] names the private copy. */
enum class DocumentFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    TXT("text/plain", "txt"),
    MARKDOWN("text/markdown", "md"),
    ;

    companion object {
        fun fromMime(mimeType: String?): DocumentFormat? = entries.firstOrNull { it.mimeType == mimeType }
    }
}

/**
 * Why a file did not become a document, or why its processing failed (F06 data-model §3). [code] is
 * what `documents.error` stores; the UI maps each value to its text.
 */
enum class IngestError(val code: String, val retryable: Boolean) {
    UNSUPPORTED_FORMAT("unsupported_format", false),
    DAMAGED("damaged", false),
    ENCRYPTED("encrypted", false),
    TOO_LARGE("too_large", false),
    NO_SPACE("no_space", true),
    ACCESS_LOST("access_lost", true),
    READ_FAILED("read_failed", true),
    DUPLICATE("duplicate", false),
    PDF_UNAVAILABLE("pdf_unavailable", false),
    NO_TEXT("no_text", false),
    EXTRACTION_FAILED("extraction_failed", true),
    INDEX_UNAVAILABLE("index_unavailable", true),

    /** The engine failed twice, also on CPU, or a text did not fit after the D-036 cuts (F07 research R4, R6). */
    EMBEDDING_FAILED("embedding_failed", true),

    /** At closing, the stored chunks did not match the chunking (F07 research R9). */
    INCOMPLETE("incomplete", true),
    ;

    companion object {
        fun fromCode(code: String?): IngestError? = entries.firstOrNull { it.code == code }
    }
}

/** Limits decided in the F06 clarify (D-037). */
object ImportLimits {
    /** Decimal megabytes, as the system shows sizes ("100 MB"); exactly this size is accepted. */
    const val MAX_FILE_BYTES = 100L * 1_000_000
    const val MAX_PDF_PAGES = 2_000

    /** Free space kept on top of the copy, as F03 does for the database (research R5). */
    const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024
}

/** Stable identity of a queue row: an import attempt held in memory, or a registered document. */
sealed interface QueueKey {
    data class Attempt(val id: String) : QueueKey
    data class Document(val id: Long) : QueueKey
}

sealed interface QueueItemState {
    /** Copying; [totalBytes] is null when the provider does not report the size. */
    data class Importing(val bytesCopied: Long, val totalBytes: Long?) : QueueItemState
    data class ImportFailed(val error: IngestError) : QueueItemState

    /** Registered and waiting for «Iniciar indexación». */
    data object Ready : QueueItemState

    /** Registered and in an active processing request. */
    data object Queued : QueueItemState

    /** Being extracted; [page] and [pages] are known for PDF only. */
    data class Extracting(val page: Int?, val pages: Int?) : QueueItemState
    data object Chunking : QueueItemState
    data class Prepared(val chunks: Int, val skippedPages: Int?) : QueueItemState

    /** Calculating embeddings: [done] stored chunks of [total] (F07 index-ui §Ingesta). */
    data class Indexing(val done: Int, val total: Int) : QueueItemState

    /** In a paused run; the counts are known once the document is chunked. */
    data class Paused(val done: Int?, val total: Int?) : QueueItemState
    data class Failed(val error: IngestError) : QueueItemState
}

data class QueueItem(
    val key: QueueKey,
    val displayName: String,
    val sizeBytes: Long?,
    val format: DocumentFormat?,
    val state: QueueItemState,
) {
    val canRetry: Boolean
        get() = when (state) {
            is QueueItemState.ImportFailed -> state.error.retryable
            is QueueItemState.Failed -> state.error.retryable
            else -> false
        }

    /** Always: an import in progress is cancelled, anything else is removed (contracts/ingest-api.md I7). */
    val canRemove: Boolean get() = true

    /** Removing a registered document deletes its private copy, so the UI confirms it (constitution V). */
    val removalNeedsConfirmation: Boolean get() = key is QueueKey.Document
}

data class ImportQueue(
    /** Attempts first, then documents oldest first. */
    val items: List<QueueItem>,
    val readyCount: Int,
    val pdfSupported: Boolean,
    val indexAvailable: Boolean,
    /** «Iniciar indexación» has something to do: Ready documents, or unfinished ones without work (F07). */
    val canStart: Boolean = readyCount > 0,
) {
    companion object {
        val EMPTY = ImportQueue(emptyList(), readyCount = 0, pdfSupported = true, indexAvailable = true)
    }
}

/**
 * Progress of the processing work for one document (WorkInfo progress, F06 contracts/ingest-api.md and
 * F07 data-model §5). [page] and [pages] belong to extraction; the chunk counts, [chunksPerSecond] and
 * [backend] to the embeddings. [waiting] is apart from the stage, so the real stage is still shown.
 */
data class IngestProgress(
    val documentId: Long,
    val stage: Stage,
    val page: Int? = null,
    val pages: Int? = null,
    val chunksDone: Int? = null,
    val chunksTotal: Int? = null,
    /** Chunks per second of the last two batches, only while it is steady (F07 research R8). */
    val chunksPerSecond: Double? = null,
    val backend: ComputeBackend? = null,
    val waiting: WaitReason? = null,
) {
    enum class Stage { EXTRACTING, CHUNKING, EMBEDDING, CLOSING }

    enum class WaitReason { THERMAL }
}

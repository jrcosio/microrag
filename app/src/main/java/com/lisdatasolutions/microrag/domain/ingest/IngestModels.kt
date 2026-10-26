package com.lisdatasolutions.microrag.domain.ingest

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
) {
    companion object {
        val EMPTY = ImportQueue(emptyList(), readyCount = 0, pdfSupported = true, indexAvailable = true)
    }
}

/** Progress of the processing work for one document (WorkInfo progress, contracts/ingest-api.md). */
data class IngestProgress(val documentId: Long, val stage: Stage, val page: Int?, val pages: Int?) {
    enum class Stage { EXTRACTING, CHUNKING }
}

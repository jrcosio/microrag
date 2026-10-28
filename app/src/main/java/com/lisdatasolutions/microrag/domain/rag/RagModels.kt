package com.lisdatasolutions.microrag.domain.rag

/** Ingestion state of a document; [sqlValue] is what `documents.status` stores (F04 data-model §1). */
enum class DocumentStatus(val sqlValue: String) {
    PENDING("pending"),
    PARSED("parsed"),
    EMBEDDING("embedding"),
    DONE("done"),
    ERROR("error"),
    PAUSED("paused"),

    /**
     * «Fuera del índice» (F08, D-043): the user removed its chunks and kept its private copy, so it can
     * be reindexed. Stored as `cancelled`, a value the schema always allowed and nothing used (D-030),
     * so the schema keeps its CHECK. The Ingest queue and the runs leave it out, as they only take
     * pending, parsed, embedding and error documents (research R4).
     */
    OUT_OF_INDEX("cancelled"),
    ;

    companion object {
        fun fromSql(value: String): DocumentStatus = entries.first { it.sqlValue == value }
    }
}

/** A document to register; it starts as [DocumentStatus.PENDING] and included in searches. */
data class NewDocument(
    val displayName: String,
    val contentHash: String,
    val mimeType: String? = null,
    val uri: String? = null,
    val sizeBytes: Long? = null,
    val pageCount: Int? = null,
    val localPath: String? = null,
)

data class StoredDocument(
    val id: Long,
    val displayName: String,
    val contentHash: String,
    val mimeType: String?,
    val uri: String?,
    val status: DocumentStatus,
    val chunkCount: Int?,
    val error: String?,
    val included: Boolean,
    val sizeBytes: Long?,
    val pageCount: Int?,
    val localPath: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    /** PDF pages without text that the extraction skipped (F06, D-038); null for TXT and Markdown. */
    val skippedPages: Int? = null,
    /** Only on a reindex shadow: the document whose chunks it replaces when it closes (F08 research R5). */
    val replacesId: Long? = null,
)

/** A Library row (F08 research R2): a document and the status of its reindex shadow, if it has one. */
data class LibraryEntry(val document: StoredDocument, val shadowStatus: DocumentStatus?)

/**
 * A chunk to store. [content] is the original text, without the contextual header that only goes
 * into the embedded text; [embedding] has 256 components and unit L2 norm.
 */
class ChunkInput(
    val ordinal: Int,
    val headingPath: String?,
    val page: Int?,
    val content: String,
    val tokenEstimate: Int,
    val embedding: FloatArray,
)

data class ChunkView(
    val id: Long,
    val documentId: Long,
    val ordinal: Int,
    val headingPath: String?,
    val page: Int?,
    val content: String,
    val tokenEstimate: Int,
    val documentName: String,
)

/** A vector search hit; SQLite-Vector cosine distance, lower is more similar. */
data class ScoredChunk(val id: Long, val distance: Double)

/** [ftsRows] counts what the FTS5 index really holds (`chunks_fts_docsize`). */
data class IndexCounts(val documents: Int, val chunks: Int, val ftsRows: Int)

/** Whether the index can be used (F04 research R7). Only [Compatible] allows reads and writes. */
sealed interface IndexCompatibility {
    /** Not opened yet, or closed. */
    data object Closed : IndexCompatibility

    data object Compatible : IndexCompatibility

    /** The file describes another vector space or schema; [found] is null when the key is missing. */
    data class Incompatible(val field: String, val expected: String, val found: String?) : IndexCompatibility

    /** Not a database, or the vector extension could not be loaded or initialized. */
    data class Unreadable(val reason: String) : IndexCompatibility
}

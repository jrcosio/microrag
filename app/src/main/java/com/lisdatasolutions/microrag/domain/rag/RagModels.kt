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
/** How a document is indexed (F19 research R9, D-078): text chunks, page images, or both. Images are always [VISUAL]. */
enum class IndexMode(val sqlValue: String) {
    TEXT("text"),
    VISUAL("visual"),
    BOTH("both"),
    ;

    val hasText: Boolean get() = this != VISUAL
    val hasPages: Boolean get() = this != TEXT

    companion object {
        fun fromSql(value: String?): IndexMode = entries.firstOrNull { it.sqlValue == value } ?: TEXT
    }
}

/** A page ready to store: its image file under `pages/`, its size and its 256D unit vector (F19 data-model §4). */
class PageInput(
    val page: Int,
    val fileName: String,
    val widthPx: Int,
    val heightPx: Int,
    val sizeBytes: Long,
    val embedding: FloatArray,
)

data class NewDocument(
    val displayName: String,
    val contentHash: String,
    val mimeType: String? = null,
    val uri: String? = null,
    val sizeBytes: Long? = null,
    val pageCount: Int? = null,
    val localPath: String? = null,
    /** F17 (contract F6): null is «General»; a folder deleted meanwhile is «General» too (M3). */
    val folderId: Long? = null,
    /** F19 (contract P7): TEXT unless the multimodal mode is on; an image is always VISUAL. */
    val indexMode: IndexMode = IndexMode.TEXT,
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
    /** F17: null is «General»; a shadow never has one. */
    val folderId: Long? = null,
    val folderName: String? = null,
    /** F19: Text, Visual or Both; [pagesStored] counts its rows in `pages`. */
    val indexMode: IndexMode = IndexMode.TEXT,
    val pagesStored: Int = 0,
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

/** The Home summary (F09 research R2): every document but reindex shadows, and the chunks of the done ones. */
data class LibrarySummary(val documents: Int, val indexedChunks: Int)

/** The library as the chat sees it (F11 research R11): shadows left out. */
data class ChatLibraryState(
    val documents: Int,
    /** Done and included, with chunks: what the search can use. */
    val searchable: Int,
    /** Included and pending, parsed, embedding or paused: what a running or paused batch will add. */
    val pendingIncluded: Int,
    /** F19 research R13: done and included with pages and no chunks; the chat does not use them until F20. */
    val visualOnly: Int = 0,
)

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

/**
 * Which documents a search may return (F17 research R3); excluded and pending ones stay out anyway. It
 * lives with the index, so `domain/retrieval` carries it without depending on `domain/chat` (analyze M1).
 */
sealed interface SearchScope {
    /** The whole library, with the F10 SQL as it is (SC-003). */
    data object All : SearchScope

    /** Folders by id, «General» as the documents with no folder, and loose documents, as they are at the question (FR-016). */
    data class Only(val folderIds: Set<Long>, val general: Boolean, val documentIds: Set<Long>) : SearchScope
}

// F17 folders (data-model §3): one level, one folder per document; a document with no folder is in «General».

/**
 * [documentCount] leaves out the reindex shadows, as every Library count (F08); [includedCount] counts the
 * ones included in answers, so the scope selector knows a folder it cannot check (K8).
 */
data class Folder(val id: Long, val name: String, val createdAtMs: Long, val documentCount: Int, val includedCount: Int = documentCount)

/** The Library root: the folders by folded name, and how many documents are in «General». */
data class FolderList(val folders: List<Folder>, val unfiledCount: Int, val unfiledIncludedCount: Int = unfiledCount)

/** Which documents a Library list shows (research R5). */
sealed interface LibraryFolderFilter {
    /** Every folder: the search of the root. */
    data object All : LibraryFolderFilter

    /** «General»: the documents with no folder. */
    data object Unfiled : LibraryFolderFilter

    data class Folder(val id: Long) : LibraryFolderFilter
}

/** A document as other files keep it: with when it was created, so a reused id never matches (D-065). */
data class DocumentRef(val id: Long, val createdAtMs: Long)

/** A folder as a scope keeps it; its ids are never reused, but `rag.db` could be made again (analyze L9). */
data class FolderRef(val id: Long, val createdAtMs: Long)

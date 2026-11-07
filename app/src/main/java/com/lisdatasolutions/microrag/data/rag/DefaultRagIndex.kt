package com.lisdatasolutions.microrag.data.rag

import androidx.sqlite.SQLiteStatement
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.ChatLibraryState
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.CompatibilityDecision
import com.lisdatasolutions.microrag.domain.rag.DocumentNames
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.DocumentTitle
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DuplicateDocumentException
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibilityChecker
import com.lisdatasolutions.microrag.domain.rag.IndexCounts
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary
import com.lisdatasolutions.microrag.domain.rag.IndexStorageFullException
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.LibraryEntry
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.domain.rag.PageKey
import com.lisdatasolutions.microrag.domain.rag.PageSpace
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.domain.rag.QuantizedState
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.rag.ScoredPage
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.domain.rag.StoredDocument
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.text.SpanishText
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [RagIndex] on one bundled SQLite connection with SQLite-Vector (F04 contracts/rag-index-api.md).
 * Everything runs on [dispatcher], a single thread (constitution IV), so operations never overlap.
 * Logs carry ids, counts and times only, never document text, queries or names (rule A10).
 */
class DefaultRagIndex(
    private val dbFile: File,
    private val schema: () -> List<String>,
    private val extensionPath: String,
    private val config: RagIndexConfig,
    private val dispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
    private val migrations: List<RagMigration> = RagMigrations.all,
    private val clock: () -> Long = System::currentTimeMillis,
    /** F19 research R2 (analyze H3): what [textEmbedder] says before the file is open, from the mode's mirror. */
    initialTextEmbedder: ModelId = ModelId.EG2_270M,
    /**
     * The text embedder a new, empty file is created with: the mode's mirror, so the 440M once the 270M is gone.
     * Read before the file is touched, so opening never suspends halfway.
     */
    private val newIndexEmbedder: suspend () -> ModelId = { initialTextEmbedder },
    private val pageSpace: PageSpace = PageSpace.CURRENT,
    /** F14 (research R3): how the INT8 copy is built and loaded; tests swap it to make either step fail. */
    private val quantization: QuantizationSql = QuantizationSql(),
    /** F14 (research R3): the chunks the INT8 copy preselects for the exact reorder; the bench measures other counts. */
    private val int8Candidates: Int = RagParameters.INT8_CANDIDATES,
) : RagIndex {

    /** F14 (research R3 and R10, contract I2): the SQL that builds and preloads the INT8 copies of the vectors. */
    data class QuantizationSql(
        val quantize: String =
            "SELECT vector_quantize('chunks', 'embedding', 'qtype=INT8,max_memory=${RagParameters.INT8_MAX_MEMORY_MB}MB')",
        val preload: String = "SELECT vector_quantize_preload('chunks', 'embedding')",
        val quantizePages: String =
            "SELECT vector_quantize('pages', 'embedding', 'qtype=INT8,max_memory=${RagParameters.INT8_MAX_MEMORY_MB}MB')",
        val preloadPages: String = "SELECT vector_quantize_preload('pages', 'embedding')",
    )

    private val embedder = MutableStateFlow(initialTextEmbedder)
    override val textEmbedder: StateFlow<ModelId> = embedder.asStateFlow()

    private val pagesMatch = MutableStateFlow(true)
    override val pagesUsable: StateFlow<Boolean> = pagesMatch.asStateFlow()

    private val state = MutableStateFlow<IndexCompatibility>(IndexCompatibility.Closed)
    override val compatibility: StateFlow<IndexCompatibility> = state.asStateFlow()

    private val changes = MutableStateFlow(0L)
    override val documentChanges: StateFlow<Long> = changes.asStateFlow()

    /** Open only while the index is compatible; touched on [dispatcher] only. */
    private var connection: RagConnection? = null

    /**
     * F14 (research R3): the INT8 copy is current and preloaded on [connection]. Set only by a whole refresh, cleared by
     * every change of the chunks and with the connection, so the search never reads a stale or partial copy. Touched on
     * [dispatcher] only.
     */
    private var int8Preloaded = false

    /** F14 (research R10): [int8Preloaded] for the page vectors, cleared by every change of the pages. */
    private var pagesInt8Preloaded = false

    override suspend fun open(): IndexCompatibility = withContext(dispatcher) { ensureOpen() }

    override suspend fun close() {
        withContext(dispatcher) {
            connection?.close()
            connection = null
            int8Preloaded = false
            pagesInt8Preloaded = false
            state.value = IndexCompatibility.Closed
        }
    }

    override suspend fun meta(): Map<String, String> = withIndex { readMeta(it) }

    override suspend fun addDocument(document: NewDocument): Long = withIndex { db ->
        db.queryOne("SELECT id FROM documents WHERE content_hash = ?", document.contentHash) { it.getLong(0) }
            ?.let { throw DuplicateDocumentException(it) }
        val now = clock()
        db.exec(
            """
            INSERT INTO documents(source, uri, display_name, mime_type, content_hash, size_bytes, page_count,
                local_path, created_at_ms, updated_at_ms, name_norm, folder_id, index_mode)
            VALUES ('user', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT f.id FROM folders f WHERE f.id = ?), ?)
            """.trimIndent(),
            document.uri, document.displayName, document.mimeType, document.contentHash, document.sizeBytes,
            document.pageCount, document.localPath, now, now, DocumentNames.fold(document.displayName), document.folderId,
            document.indexMode.sqlValue,
        )
        val id = checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
        logger.i(TAG) { "rag document add id=$id" }
        notifyChanged()
        id
    }

    override suspend fun document(id: Long): StoredDocument? = withIndex { db ->
        db.queryOne("SELECT $DOCUMENT_COLUMNS FROM documents WHERE id = ?", id, row = ::readDocument)
    }

    override suspend fun documents(statuses: Set<DocumentStatus>): List<StoredDocument> {
        if (statuses.isEmpty()) return emptyList()
        return withIndex { db ->
            db.query(
                "SELECT $DOCUMENT_COLUMNS FROM documents WHERE status IN (${statuses.joinToString(",") { "?" }}) " +
                    "ORDER BY created_at_ms, id",
                *statuses.map { it.sqlValue }.toTypedArray(),
                row = ::readDocument,
            )
        }
    }

    override suspend fun libraryPage(
        text: String,
        statuses: Set<DocumentStatus>,
        limit: Int,
        folder: LibraryFolderFilter,
    ): List<LibraryEntry> {
        if (statuses.isEmpty() || limit <= 0) return emptyList()
        val (where, args) = libraryFilter(text, statuses, folder)
        return withIndex { db ->
            db.query(
                "SELECT $DOCUMENT_COLUMNS, (SELECT s.status FROM documents s WHERE s.replaces_id = documents.id LIMIT 1) " +
                    "FROM documents WHERE $where ORDER BY created_at_ms DESC, id DESC LIMIT ?",
                *args, limit,
            ) { row -> LibraryEntry(readDocument(row), row.textOrNull(SHADOW_STATUS_COLUMN)?.let(DocumentStatus::fromSql)) }
        }
    }

    override suspend fun libraryCount(text: String, statuses: Set<DocumentStatus>, folder: LibraryFolderFilter): Int {
        if (statuses.isEmpty()) return 0
        val (where, args) = libraryFilter(text, statuses, folder)
        return withIndex { db -> count(db, "SELECT count(*) FROM documents WHERE $where", *args) }
    }

    override suspend fun folders(): FolderList = withIndex { db ->
        val folders = db.query(
            "SELECT f.id, f.name, f.created_at_ms, count(d.id), COALESCE(SUM(d.included), 0) FROM folders f " +
                "LEFT JOIN documents d ON d.folder_id = f.id AND d.replaces_id IS NULL " +
                "GROUP BY f.id ORDER BY f.name_norm, f.id",
        ) {
            Folder(
                id = it.getLong(0), name = it.getText(1), createdAtMs = it.getLong(2),
                documentCount = it.getLong(3).toInt(), includedCount = it.getLong(4).toInt(),
            )
        }
        val unfiled = db.queryOne(
            "SELECT count(*), COALESCE(SUM(included), 0) FROM documents WHERE folder_id IS NULL AND replaces_id IS NULL",
        ) { it.getLong(0).toInt() to it.getLong(1).toInt() } ?: (0 to 0)
        FolderList(folders, unfiled.first, unfiled.second)
    }

    // F17 (contract F2): the index runs on one thread, so looking for the folded name before writing is
    // enough; UNIQUE(name_norm) still guards the file.
    override suspend fun createFolder(name: String, norm: String): Long? = withIndex { db ->
        if (db.queryOne("SELECT 1 FROM folders WHERE name_norm = ?", norm) { true } == true) return@withIndex null
        val now = clock()
        db.exec("INSERT INTO folders(name, name_norm, created_at_ms, updated_at_ms) VALUES (?, ?, ?, ?)", name, norm, now, now)
        val id = checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
        logger.i(TAG) { "rag folder add id=$id" }
        notifyChanged()
        id
    }

    override suspend fun renameFolder(id: Long, name: String, norm: String): Boolean? = withIndex { db ->
        if (db.queryOne("SELECT 1 FROM folders WHERE id = ?", id) { true } != true) return@withIndex false
        if (db.queryOne("SELECT 1 FROM folders WHERE name_norm = ? AND id <> ?", norm, id) { true } == true) return@withIndex null
        db.exec("UPDATE folders SET name = ?, name_norm = ?, updated_at_ms = ? WHERE id = ?", name, norm, clock(), id)
        logger.i(TAG) { "rag folder rename id=$id" }
        notifyChanged()
        true
    }

    override suspend fun deleteFolder(id: Long, moveDocuments: Boolean): Boolean = withIndex { db ->
        val deleted = db.transaction {
            if (moveDocuments) {
                db.exec("UPDATE documents SET folder_id = NULL, updated_at_ms = ? WHERE folder_id = ?", clock(), id)
            } else if (count(db, "SELECT count(*) FROM documents WHERE folder_id = ?", id) > 0) {
                return@transaction false
            }
            db.exec("DELETE FROM folders WHERE id = ?", id)
            db.changes() > 0
        }
        logger.i(TAG) { "rag folder delete id=$id moved=$moveDocuments deleted=$deleted" }
        if (deleted) notifyChanged()
        deleted
    }

    override suspend fun moveDocument(documentId: Long, folderId: Long?): Boolean = withIndex { db ->
        if (folderId != null && db.queryOne("SELECT 1 FROM folders WHERE id = ?", folderId) { true } != true) return@withIndex false
        db.exec(
            "UPDATE documents SET folder_id = ?, updated_at_ms = ? WHERE id = ? AND replaces_id IS NULL",
            folderId, clock(), documentId,
        )
        val moved = db.changes() > 0
        logger.i(TAG) { "rag document move id=$documentId folder=${folderId ?: 0} moved=$moved" }
        if (moved) notifyChanged()
        moved
    }

    override suspend fun folderDocumentRefs(folderId: Long?): List<DocumentRef> = withIndex { db ->
        val folder = if (folderId == null) "folder_id IS NULL" else "folder_id = ?"
        db.query(
            "SELECT id, created_at_ms FROM documents WHERE $folder AND replaces_id IS NULL ORDER BY created_at_ms DESC, id DESC",
            *listOfNotNull(folderId).toTypedArray(),
        ) { DocumentRef(it.getLong(0), it.getLong(1)) }
    }

    override suspend fun documentIdByHash(contentHash: String): Long? = withIndex { db ->
        db.queryOne("SELECT id FROM documents WHERE content_hash = ?", contentHash) { it.getLong(0) }
    }

    override suspend fun updateStatus(id: Long, status: DocumentStatus, error: String?, chunkCount: Int?, skippedPages: Int?) =
        withIndex { db ->
            db.exec(
                "UPDATE documents SET status = ?, error = ?, chunk_count = COALESCE(?, chunk_count), " +
                    "skipped_pages = COALESCE(?, skipped_pages), updated_at_ms = ? WHERE id = ?",
                status.sqlValue, error, chunkCount, skippedPages, clock(), id,
            )
            logger.i(TAG) { "rag document status id=$id ${status.sqlValue}" }
            notifyChanged()
        }

    override suspend fun setIncluded(id: Long, included: Boolean) = withIndex { db ->
        db.exec("UPDATE documents SET included = ?, updated_at_ms = ? WHERE id = ?", included, clock(), id)
        notifyChanged()
    }

    override suspend fun deleteDocument(id: Long) = withIndex { db ->
        val started = System.nanoTime()
        db.transaction {
            // ON DELETE CASCADE removes the shadow and the chunks; their chunks_ad trigger cleans the FTS index.
            db.exec("DELETE FROM documents WHERE id = ?", id)
            markQuantDirty(db)
            markPagesQuantDirty(db)
        }
        logger.i(TAG) { "rag document delete id=$id in ${elapsedMs(started)} ms" }
        notifyChanged()
    }

    override suspend fun addReindexShadow(originalId: Long): Long = withIndex { db ->
        val shadow = db.transaction {
            val original = db.queryOne("SELECT $DOCUMENT_COLUMNS FROM documents WHERE id = ?", originalId, row = ::readDocument)
            check(original != null && original.status == DocumentStatus.DONE && original.replacesId == null) {
                "Document $originalId is not a done document"
            }
            check(count(db, "SELECT count(*) FROM documents WHERE replaces_id = ?", originalId) == 0) {
                "Document $originalId is already being reindexed"
            }
            val now = clock()
            db.exec(
                """
                INSERT INTO documents(source, uri, display_name, mime_type, content_hash, size_bytes, page_count,
                    local_path, created_at_ms, updated_at_ms, name_norm, replaces_id, index_mode)
                SELECT source, uri, display_name, mime_type, content_hash || ?, size_bytes, page_count,
                    local_path, ?, ?, name_norm, id, index_mode
                FROM documents WHERE id = ?
                """.trimIndent(),
                SHADOW_HASH_SUFFIX, now, now, originalId,
            )
            checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
        }
        logger.i(TAG) { "rag reindex shadow id=$originalId shadow=$shadow" }
        notifyChanged()
        shadow
    }

    override suspend fun shadowOf(originalId: Long): StoredDocument? = withIndex { db ->
        db.queryOne("SELECT $DOCUMENT_COLUMNS FROM documents WHERE replaces_id = ? LIMIT 1", originalId, row = ::readDocument)
    }

    override suspend fun publishReindex(shadowId: Long): Int? = withIndex { db ->
        val started = System.nanoTime()
        var originalId = 0L
        val published = db.transaction {
            val shadow = db.queryOne("SELECT $DOCUMENT_COLUMNS FROM documents WHERE id = ?", shadowId, row = ::readDocument)
                ?: return@transaction null
            originalId = shadow.replacesId ?: return@transaction null
            val original = db.queryOne("SELECT status FROM documents WHERE id = ?", originalId) { it.getText(0) }
            if (original != DocumentStatus.DONE.sqlValue) return@transaction null
            // The chunks keep their ids, so FTS rows and vectors stay; chunks_au only fires on the text columns.
            db.exec("DELETE FROM chunks WHERE document_id = ?", originalId)
            db.exec("UPDATE chunks SET document_id = ? WHERE document_id = ?", originalId, shadowId)
            val moved = db.changes()
            // F19 contract P5: the shadow's pages and mode replace the original's; the caller deletes the old files.
            db.exec("DELETE FROM pages WHERE document_id = ?", originalId)
            db.exec("UPDATE pages SET document_id = ? WHERE document_id = ?", originalId, shadowId)
            db.exec(
                "UPDATE documents SET chunk_count = ?, skipped_pages = ?, page_count = ?, index_mode = ?, error = NULL, " +
                    "updated_at_ms = ? WHERE id = ?",
                moved, shadow.skippedPages, shadow.pageCount, shadow.indexMode.sqlValue, clock(), originalId,
            )
            db.exec("DELETE FROM documents WHERE id = ?", shadowId)
            markQuantDirty(db)
            markPagesQuantDirty(db)
            moved
        }
        if (published != null) {
            logger.i(TAG) { "rag reindex published id=$originalId chunks=$published in ${elapsedMs(started)} ms" }
            notifyChanged()
        }
        published
    }

    override suspend fun removeFromIndex(id: Long): Int? = withIndex { db ->
        val started = System.nanoTime()
        val removed = db.transaction {
            val status = db.queryOne("SELECT status FROM documents WHERE id = ? AND replaces_id IS NULL", id) { it.getText(0) }
            if (status != DocumentStatus.DONE.sqlValue) return@transaction null
            db.exec("DELETE FROM documents WHERE replaces_id = ?", id)
            db.exec("DELETE FROM chunks WHERE document_id = ?", id)
            val chunks = db.changes()
            db.exec("DELETE FROM pages WHERE document_id = ?", id)
            db.exec(
                "UPDATE documents SET status = ?, chunk_count = NULL, error = NULL, updated_at_ms = ? WHERE id = ?",
                DocumentStatus.OUT_OF_INDEX.sqlValue, clock(), id,
            )
            markQuantDirty(db)
            markPagesQuantDirty(db)
            chunks
        }
        if (removed != null) {
            logger.i(TAG) { "rag out-of-index id=$id chunks=$removed in ${elapsedMs(started)} ms" }
            notifyChanged()
        }
        removed
    }

    override suspend fun storedChunkCounts(ids: Collection<Long>): Map<Long, Int> = withIndex { db ->
        val counts = ids.distinct().chunked(LOAD_BLOCK).flatMap { block ->
            db.query(
                "SELECT document_id, count(*) FROM chunks WHERE document_id IN (${block.joinToString(",") { "?" }}) GROUP BY document_id",
                *block.toTypedArray(),
            ) { it.getLong(0) to it.getLong(1).toInt() }
        }.toMap()
        ids.associateWith { counts[it] ?: 0 }
    }

    override suspend fun storedChunkContents(documentId: Long): Map<Int, String> = withIndex { db ->
        db.query("SELECT ordinal, content FROM chunks WHERE document_id = ? ORDER BY ordinal", documentId) {
            it.getLong(0).toInt() to it.getText(1)
        }.toMap(LinkedHashMap())
    }

    override suspend fun deleteChunks(documentId: Long) = withIndex { db ->
        // The chunks_ad trigger removes their FTS rows.
        val deleted = db.transaction {
            val before = chunkCount(db, documentId)
            db.exec("DELETE FROM chunks WHERE document_id = ?", documentId)
            markQuantDirty(db)
            before
        }
        logger.i(TAG) { "rag chunks doc=$documentId deleted=$deleted" }
        notifyChanged()
    }

    override suspend fun switchTextEmbedder(to: ModelId): Boolean = withIndex { db ->
        val sha = ModelCatalog.byId(to).sha256
        require(sha in config.acceptedEmbeddingModels) { "$to is not an accepted text embedder" }
        val switched = db.transaction {
            val current = db.queryOne("SELECT value FROM rag_meta WHERE key = ?", RagMetaKeys.EMBEDDING_MODEL_SHA256) { it.getText(0) }
            check(current in config.acceptedEmbeddingModels) { "The index records an unknown text embedder" }
            if (current == sha) return@transaction false
            db.exec("UPDATE rag_meta SET value = ? WHERE key = ?", sha, RagMetaKeys.EMBEDDING_MODEL_SHA256)
            true
        }
        embedder.value = to
        logger.i(TAG) { "rag text embedder ${if (switched) "switched to" else "already"} $to" }
        switched
    }

    override suspend fun storedPages(documentId: Long): Set<Int> = withIndex { db ->
        db.query("SELECT page FROM pages WHERE document_id = ?", documentId) { it.getLong(0).toInt() }.toSet()
    }

    override suspend fun insertPage(documentId: Long, page: PageInput): Boolean {
        requireUnitVector(page.embedding)
        return withIndex { db ->
            requireNotNull(db.queryOne("SELECT id FROM documents WHERE id = ?", documentId) { it.getLong(0) }) {
                "Unknown document $documentId"
            }
            val inserted = storageFullAware {
                db.transaction {
                    if (count(db, "SELECT count(*) FROM pages") == 0) {
                        RagMetaKeys.PAGE_SPACE.forEach { db.exec("DELETE FROM rag_meta WHERE key = ?", it) }
                        pageSpace.toMeta().forEach { (key, value) -> db.exec("INSERT INTO rag_meta(key, value) VALUES (?, ?)", key, value) }
                    } else {
                        // Constitution III: vectors of two page spaces never share the table.
                        check(pageSpace.matches(readMeta(db))) { "The stored pages belong to another page space" }
                    }
                    db.exec(
                        """
                        INSERT OR IGNORE INTO pages(document_id, page, file_name, width_px, height_px, size_bytes, embedding)
                        VALUES (?, ?, ?, ?, ?, ?, vector_as_f32(?))
                        """.trimIndent(),
                        documentId, page.page, page.fileName, page.widthPx, page.heightPx, page.sizeBytes, page.embedding.toLeBlob(),
                    )
                    (db.changes() > 0).also { if (it) markPagesQuantDirty(db) }
                }
            }
            pagesMatch.value = pageSpace.matches(readMeta(db))
            if (inserted) notifyChanged()
            inserted
        }
    }

    override suspend fun pageFiles(documentId: Long): List<String> = withIndex { db ->
        db.query(
            "SELECT p.file_name FROM pages p JOIN documents d ON d.id = p.document_id WHERE d.id = ? OR d.replaces_id = ?",
            documentId, documentId,
        ) { it.getText(0) }
    }

    override suspend fun pageSearch(query: FloatArray, k: Int, scope: SearchScope): List<ScoredPage> {
        requireUnitVector(query)
        require(k > 0) { "k must be positive" }
        if (!pagesUsable.value) return emptyList()
        return withIndex { db ->
            val (condition, args) = (scope as? SearchScope.Only)?.let(::scopeCondition) ?: ("1" to emptyArray())
            if (pagesInt8Preloaded) {
                quantizedPageSearch(db, query, k, condition, args)
            } else {
                db.query(pageSearchSql(condition), query.toLeBlob(), *args, k) {
                    ScoredPage(
                        documentId = it.getLong(0), page = it.getLong(1).toInt(), distance = it.getDouble(2),
                        documentName = it.getText(3), fileName = it.getText(4), image = it.getLong(5) == 1L,
                    )
                }
            }
        }
    }

    /**
     * F14 (research R10, FR-023): [quantizedSearch] for the pages. The INT8 copy preselects [int8Candidates] pages
     * with the filters of [pageSearchSql], and their stored F32 vectors order them by the exact cosine distance.
     */
    private fun quantizedPageSearch(db: RagConnection, query: FloatArray, k: Int, condition: String, args: Array<Any?>): List<ScoredPage> {
        val candidates = db.query(quantizedPageSearchSql(condition), query.toLeBlob(), *args, maxOf(k, int8Candidates)) { it.getLong(0) }
        return candidates.chunked(MAX_KEYS_PER_QUERY).flatMap { block ->
            db.query(PAGE_ROWS_SQL + "WHERE p.id IN (${block.joinToString(",") { "?" }})", *block.toTypedArray()) {
                it.getLong(0) to ScoredPage(
                    documentId = it.getLong(1), page = it.getLong(2).toInt(),
                    distance = EXACT_COSINE_ONE - EmbeddingMath.dot(query, it.getBlob(6).toFloatArrayLe()),
                    documentName = it.getText(3), fileName = it.getText(4), image = it.getLong(5) == 1L,
                )
            }
        }.sortedWith(compareBy<Pair<Long, ScoredPage>>({ it.second.distance }, { it.first })).take(k).map { it.second }
    }

    override suspend fun existingPages(keys: Collection<PageKey>): Set<PageKey> {
        if (keys.isEmpty()) return emptySet()
        return withIndex { db ->
            keys.distinct().chunked(MAX_KEYS_PER_QUERY).flatMap { batch ->
                db.query(
                    "SELECT document_id, page FROM pages WHERE " + batch.joinToString(" OR ") { "(document_id = ? AND page = ?)" },
                    *batch.flatMap { listOf<Any?>(it.documentId, it.page.toLong()) }.toTypedArray(),
                ) { PageKey(it.getLong(0), it.getLong(1).toInt()) }
            }.toSet()
        }
    }

    override suspend fun pageFile(documentId: Long, page: Int): String? = withIndex { db ->
        db.queryOne("SELECT file_name FROM pages WHERE document_id = ? AND page = ?", documentId, page) { it.getText(0) }
    }

    override suspend fun allPageFiles(): Set<String> = withIndex { db ->
        db.query("SELECT file_name FROM pages") { it.getText(0) }.toSet()
    }

    override suspend fun setIndexMode(id: Long, mode: IndexMode) = withIndex { db ->
        db.exec("UPDATE documents SET index_mode = ?, updated_at_ms = ? WHERE id = ?", mode.sqlValue, clock(), id)
        notifyChanged()
    }

    override suspend fun insertChunks(documentId: Long, chunks: List<ChunkInput>): Int {
        require(chunks.map { it.ordinal }.toSet().size == chunks.size) { "Repeated ordinals in the batch" }
        chunks.forEach { requireUnitVector(it.embedding) }
        return withIndex { db ->
            val name = db.queryOne("SELECT display_name FROM documents WHERE id = ?", documentId) { it.getText(0) }
            requireNotNull(name) { "Unknown document $documentId" }
            if (chunks.isEmpty()) return@withIndex 0
            val title = DocumentTitle.of(name)
            val started = System.nanoTime()
            val inserted = storageFullAware {
                db.transaction {
                    val before = chunkCount(db, documentId)
                    chunks.forEach { chunk ->
                        val header = listOf(title, chunk.headingPath).filter { !it.isNullOrBlank() }.joinToString(" › ")
                        db.exec(
                            """
                            INSERT OR IGNORE INTO chunks(document_id, ordinal, heading_path, page, content, title_norm,
                                body_norm, token_estimate, embedding)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, vector_as_f32(?))
                            """.trimIndent(),
                            documentId, chunk.ordinal, chunk.headingPath, chunk.page, chunk.content,
                            SpanishText.normalize(header), SpanishText.normalize(chunk.content), chunk.tokenEstimate,
                            chunk.embedding.toLeBlob(),
                        )
                    }
                    markQuantDirty(db)
                    chunkCount(db, documentId) - before
                }
            }
            logger.i(TAG) { "rag chunks doc=$documentId inserted=$inserted of ${chunks.size} in ${elapsedMs(started)} ms" }
            if (inserted > 0) notifyChanged()
            inserted
        }
    }

    override suspend fun vectorSearch(query: FloatArray, k: Int, scope: SearchScope): List<ScoredChunk> {
        requireUnitVector(query)
        require(k > 0) { "k must be positive" }
        return withIndex { db -> if (int8Preloaded) quantizedSearch(db, query, k, scope) else exactSearch(db, query, k, scope) }
    }

    private fun exactSearch(db: RagConnection, query: FloatArray, k: Int, scope: SearchScope): List<ScoredChunk> = when (scope) {
        SearchScope.All ->
            db.query(VECTOR_SEARCH_SQL, query.toLeBlob(), k + hiddenChunks(db), k) { ScoredChunk(it.getLong(0), it.getDouble(1)) }
        is SearchScope.Only -> {
            val (condition, args) = scopeCondition(scope)
            db.query(scopedVectorSearchSql(condition), query.toLeBlob(), *args, k) { ScoredChunk(it.getLong(0), it.getDouble(1)) }
        }
    }

    /**
     * F14 (research R3, contract I2): the INT8 copy preselects [int8Candidates] searchable chunks with the same
     * filters as [exactSearch], and their stored F32 vectors order them by the exact cosine distance.
     *
     * Without a scope it first reads the head of the copy, [QUANTIZED_HEAD_FACTOR] times the candidates: when the
     * hidden chunks leave that many searchable ones there, they are the same as with every hidden chunk on top, and
     * joining a few rows instead of thousands is most of the search on the phone (T049). Otherwise it widens to k + m.
     */
    private fun quantizedSearch(db: RagConnection, query: FloatArray, k: Int, scope: SearchScope): List<ScoredChunk> {
        val wanted = maxOf(k, int8Candidates)
        val candidates = when (scope) {
            SearchScope.All -> {
                val head = db.query(QUANTIZED_SEARCH_SQL, query.toLeBlob(), wanted * QUANTIZED_HEAD_FACTOR, wanted) { it.getLong(0) }
                if (head.size >= wanted) head
                else db.query(QUANTIZED_SEARCH_SQL, query.toLeBlob(), wanted + hiddenChunks(db), wanted) { it.getLong(0) }
            }
            is SearchScope.Only -> {
                val (condition, args) = scopeCondition(scope)
                db.query(scopedQuantizedSearchSql(condition), query.toLeBlob(), *args, wanted) { it.getLong(0) }
            }
        }
        return candidates.chunked(MAX_KEYS_PER_QUERY).flatMap { block ->
            db.query("SELECT id, embedding FROM chunks WHERE id IN (${block.joinToString(",") { "?" }})", *block.toTypedArray()) {
                ScoredChunk(it.getLong(0), EXACT_COSINE_ONE - EmbeddingMath.dot(query, it.getBlob(1).toFloatArrayLe()))
            }
        }.sortedWith(compareBy<ScoredChunk>({ it.distance }, { it.id })).take(k)
    }

    override suspend fun quantizedState(): QuantizedState = withIndex { db -> quantizedState(db, searchableChunks(db)) }

    private fun quantizedState(db: RagConnection, searchable: Int) = QuantizedState(
        current = quantCurrent(db, RagMetaKeys.QUANT_DIRTY), preloaded = int8Preloaded, searchableChunks = searchable,
        pagesCurrent = quantCurrent(db, RagMetaKeys.PAGES_QUANT_DIRTY), pagesPreloaded = pagesInt8Preloaded,
        pages = count(db, "SELECT count(*) FROM pages"),
    )

    override suspend fun refreshQuantized(minChunks: Int, minPages: Int): QuantizedState = withIndex { db ->
        int8Preloaded = false
        pagesInt8Preloaded = false
        val searchable = searchableChunks(db)
        if (searchable > 0 && searchable >= minChunks) {
            int8Preloaded = refreshCopy(db, "chunks", searchable, RagMetaKeys.QUANT_DIRTY, quantization.quantize, quantization.preload)
        }
        val pages = count(db, "SELECT count(*) FROM pages")
        if (pages > 0 && pages >= minPages && pagesUsable.value) {
            pagesInt8Preloaded = refreshCopy(db, "pages", pages, RagMetaKeys.PAGES_QUANT_DIRTY, quantization.quantizePages, quantization.preloadPages)
        }
        quantizedState(db, searchable)
    }

    /** Rebuilds the INT8 copy of [table] when [dirtyKey] says it is stale, then preloads it; false if either fails. */
    private fun refreshCopy(db: RagConnection, table: String, rows: Int, dirtyKey: String, quantize: String, preload: String): Boolean =
        try {
            if (!quantCurrent(db, dirtyKey)) {
                val started = System.nanoTime()
                db.queryOne(quantize) { }
                db.exec("INSERT OR REPLACE INTO rag_meta(key, value) VALUES (?, '0')", dirtyKey)
                logger.i(TAG) { "rag int8 rebuilt $table=$rows in ${elapsedMs(started)} ms" }
            }
            val started = System.nanoTime()
            db.queryOne(preload) { }
            logger.i(TAG) { "rag int8 preloaded $table in ${elapsedMs(started)} ms" }
            true
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: RuntimeException) {
            logger.w(TAG, e) { "rag int8 refresh of $table failed; the search stays exact" }
            false
        }

    /** A missing key reads as stale: files from before F14 never had `pages_quant_dirty`. */
    private fun quantCurrent(db: RagConnection, dirtyKey: String): Boolean =
        db.queryOne("SELECT value FROM rag_meta WHERE key = ?", dirtyKey) { it.getText(0) } == "0"

    private fun searchableChunks(db: RagConnection): Int =
        count(db, "SELECT count(*) FROM chunks c JOIN documents d ON d.id = c.document_id WHERE " + SEARCHABLE)

    override suspend fun textSearch(userQuery: String, k: Int, scope: SearchScope): List<Long> {
        require(k > 0) { "k must be positive" }
        val ftsQuery = SpanishText.ftsQuery(userQuery)
        return withIndex { db ->
            when {
                ftsQuery.isEmpty() -> emptyList()
                scope is SearchScope.Only -> {
                    val (condition, args) = scopeCondition(scope)
                    db.query(scopedTextSearchSql(condition), ftsQuery, *args, k) { it.getLong(0) }
                }
                else -> db.query(TEXT_SEARCH_SQL, ftsQuery, k + hiddenChunks(db), k) { it.getLong(0) }
            }
        }
    }

    override suspend fun loadChunks(ids: Collection<Long>, searchableOnly: Boolean, scope: SearchScope): Map<Long, ChunkView> =
        withIndex { db ->
            ids.distinct().chunked(LOAD_BLOCK).flatMap { block -> loadBlock(db, block, searchableOnly, scope) }.associateBy { it.id }
        }

    override suspend fun neighbor(documentId: Long, ordinal: Int, searchableOnly: Boolean, scope: SearchScope): ChunkView? =
        withIndex { db ->
            db.queryOne("SELECT id FROM chunks WHERE document_id = ? AND ordinal = ?", documentId, ordinal) { it.getLong(0) }
                ?.let { loadBlock(db, listOf(it), searchableOnly, scope).singleOrNull() }
        }

    override suspend fun counts(): IndexCounts = withIndex { db ->
        IndexCounts(
            documents = count(db, "SELECT count(*) FROM documents"),
            chunks = count(db, "SELECT count(*) FROM chunks"),
            // With external content, count(*) on chunks_fts would read chunks; docsize is the index itself.
            ftsRows = count(db, "SELECT count(*) FROM chunks_fts_docsize"),
        )
    }

    // Rule N1 of the runner keeps chunk_count of a done document equal to its stored chunks, and
    // publishReindex sets it to the chunks it moves, so chunks are never read (F09 rule H5).
    override suspend fun librarySummary(): LibrarySummary = withIndex { db ->
        db.queryOne(
            "SELECT count(*), COALESCE(SUM(CASE WHEN status = ? THEN chunk_count END), 0) FROM documents WHERE replaces_id IS NULL",
            DocumentStatus.DONE.sqlValue,
        ) { LibrarySummary(documents = it.getLong(0).toInt(), indexedChunks = it.getLong(1).toInt()) } ?: LibrarySummary(0, 0)
    }

    override suspend fun chatLibraryState(scope: SearchScope): ChatLibraryState = withIndex { db ->
        val pending = listOf(DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.EMBEDDING, DocumentStatus.PAUSED)
        val (condition, scopeArgs) = (scope as? SearchScope.Only)?.let(::scopeCondition)?.let { (sql, args) -> " AND $sql" to args }
            ?: ("" to emptyArray())
        // F19 research R13 (contract P9): a done document with pages and no chunks counts apart; F20 (R5) also
        // counts the searchable ones with any page, for the warm-up.
        db.queryOne(
            "SELECT count(*), " +
                "COALESCE(SUM(CASE WHEN d.status = ? AND d.included = 1 AND NOT $PAGES_ONLY$condition THEN 1 ELSE 0 END), 0), " +
                "COALESCE(SUM(CASE WHEN d.status IN (${pending.joinToString { "?" }}) AND d.included = 1$condition THEN 1 ELSE 0 END), 0), " +
                "COALESCE(SUM(CASE WHEN d.status = ? AND d.included = 1 AND $PAGES_ONLY$condition THEN 1 ELSE 0 END), 0), " +
                "COALESCE(SUM(CASE WHEN d.status = ? AND d.included = 1 AND $HAS_PAGES$condition THEN 1 ELSE 0 END), 0) " +
                "FROM documents d WHERE d.replaces_id IS NULL",
            DocumentStatus.DONE.sqlValue, *scopeArgs, *pending.map { it.sqlValue }.toTypedArray(), *scopeArgs,
            DocumentStatus.DONE.sqlValue, *scopeArgs, DocumentStatus.DONE.sqlValue, *scopeArgs,
        ) {
            ChatLibraryState(it.getLong(0).toInt(), it.getLong(1).toInt(), it.getLong(2).toInt(), it.getLong(3).toInt(), it.getLong(4).toInt())
        }
            ?: ChatLibraryState(0, 0, 0)
    }

    /** The INT8 copy no longer matches the chunks (D-030): the search is exact until the next refresh (F14 R3). */
    private fun markQuantDirty(db: RagConnection) {
        db.exec("UPDATE rag_meta SET value = '1' WHERE key = ?", RagMetaKeys.QUANT_DIRTY)
        int8Preloaded = false
    }

    /**
     * [markQuantDirty] for the page vectors (F14 research R10, contract I3). The key is removed, since a missing key
     * reads as stale: only a rebuild writes it, so a file with pages and no copy keeps the keys it had.
     */
    private fun markPagesQuantDirty(db: RagConnection) {
        db.exec("DELETE FROM rag_meta WHERE key = ?", RagMetaKeys.PAGES_QUANT_DIRTY)
        pagesInt8Preloaded = false
    }

    /** WHERE clause and arguments shared by [libraryPage] and [libraryCount]: no shadows, [statuses], the folder, the name. */
    private fun libraryFilter(text: String, statuses: Set<DocumentStatus>, folder: LibraryFolderFilter): Pair<String, Array<Any?>> {
        val clauses = mutableListOf("replaces_id IS NULL", "status IN (${statuses.joinToString(",") { "?" }})")
        val args = statuses.map<DocumentStatus, Any?> { it.sqlValue }.toMutableList()
        when (folder) {
            LibraryFolderFilter.All -> Unit
            LibraryFolderFilter.Unfiled -> clauses += "folder_id IS NULL"
            is LibraryFolderFilter.Folder -> {
                clauses += "folder_id = ?"
                args += folder.id
            }
        }
        if (text.isNotBlank()) {
            clauses += "name_norm LIKE ? ESCAPE '\\'"
            args += DocumentNames.likePattern(text)
        }
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    /** Runs [block] on the open, compatible connection; otherwise throws without touching the file (rule A2). */
    private suspend fun <T> withIndex(block: (RagConnection) -> T): T = withContext(dispatcher) {
        val current = ensureOpen()
        val db = connection
        if (current != IndexCompatibility.Compatible || db == null) throw IndexUnavailableException(current)
        block(db)
    }

    private suspend fun ensureOpen(): IndexCompatibility {
        if (state.value != IndexCompatibility.Closed) return state.value
        val newEmbedder = newIndexEmbedder()
        // The database dispatcher is single-threaded: from here on nothing suspends, so one open runs at a time.
        if (state.value != IndexCompatibility.Closed) return state.value
        val started = System.nanoTime()
        val result = try {
            openConnection(newEmbedder)
        } catch (e: RagOpenException) {
            logger.w(TAG, e) { "rag index unreadable: ${e.reason}" }
            IndexCompatibility.Unreadable(e.reason)
        }
        state.value = result
        logger.i(TAG) { "rag index open: $result in ${elapsedMs(started)} ms" }
        return result
    }

    private fun openConnection(newEmbedder: ModelId): IndexCompatibility {
        dbFile.parentFile?.mkdirs()
        val db = RagConnection.open(dbFile, extensionPath)
        val result = try {
            prepare(db, newEmbedder)
        } catch (e: Exception) {
            db.close()
            throw e as? RagOpenException ?: RagOpenException("prepare: ${e.message}", e)
        }
        if (result == IndexCompatibility.Compatible) connection = db else db.close()
        return result
    }

    /**
     * Pragmas, `rag_meta` validation, schema and `vector_init`. The metadata is checked before anything
     * that may write (journal mode, schema), so an incompatible file is left exactly as it was.
     */
    private fun prepare(db: RagConnection, newEmbedder: ModelId): IndexCompatibility {
        val tables = db.tableNames()
        if (tables.isEmpty()) {
            createIndex(db, newEmbedder)
        } else {
            val meta = if (META_TABLE in tables) readMeta(db) else emptyMap()
            when (val decision = IndexCompatibilityChecker.check(config, meta, migrations.map { it.from }.toSet())) {
                CompatibilityDecision.Compatible -> applySchema(db)
                is CompatibilityDecision.NeedsMigration -> migrate(db, decision.from)?.let { return it }
                is CompatibilityDecision.Incompatible -> return decision.toState()
            }
            fillNameNorm(db)
        }
        try {
            db.initVector(config.dims)
        } catch (e: Exception) {
            throw RagOpenException("vector_init: ${e.message}", e)
        }
        // F19 research R2 and R8: what the open file records.
        val meta = readMeta(db)
        RagIndexConfig.textEmbedderOf(meta[RagMetaKeys.EMBEDDING_MODEL_SHA256])?.let { embedder.value = it }
        pagesMatch.value = pageSpace.matches(meta)
        return IndexCompatibility.Compatible
    }

    /** A file without tables becomes a new index: schema and `rag_meta` in one transaction. */
    private fun createIndex(db: RagConnection, newEmbedder: ModelId) {
        val statements = loadSchema()
        db.enableWal()
        db.transaction {
            statements.forEach { db.exec(it) }
            config.copy(embeddingModelSha256 = ModelCatalog.byId(newEmbedder).sha256).toMeta()
                .forEach { (key, value) -> db.exec("INSERT INTO rag_meta(key, value) VALUES (?, ?)", key, value) }
        }
    }

    /** `IF NOT EXISTS` statements: a no-op on a complete file, as the report runs them on every open. */
    private fun applySchema(db: RagConnection) {
        val statements = loadSchema()
        db.enableWal()
        statements.forEach { db.exec(it) }
    }

    /** Returns null once migrated and compatible, or the state to publish when the file stays as it was. */
    private fun migrate(db: RagConnection, from: Int): IndexCompatibility? {
        val steps = migrations.filter { it.from >= from }.sortedBy { it.from }
        val failed = runCatching {
            db.transaction {
                steps.forEach { step -> step.statements.forEach { db.exec(it) } }
                db.exec(
                    "UPDATE rag_meta SET value = ? WHERE key = ?",
                    config.schemaVersion.toString(), RagMetaKeys.SCHEMA_VERSION,
                )
            }
        }.exceptionOrNull()
        if (failed != null) {
            logger.w(TAG, failed) { "rag migration from schema $from failed" }
            return IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, config.schemaVersion.toString(), from.toString())
        }
        logger.i(TAG) { "rag migrated schema $from -> ${config.schemaVersion}" }
        val decision = IndexCompatibilityChecker.check(config, readMeta(db), emptySet())
        if (decision != CompatibilityDecision.Compatible) {
            return (decision as? CompatibilityDecision.Incompatible)?.toState()
                ?: IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, config.schemaVersion.toString(), from.toString())
        }
        applySchema(db)
        return null
    }

    /**
     * Folds the names of rows without `name_norm`: those migrated from v2 and those the offline tools
     * write (F08 research R1). Idempotent, in one transaction; migrations can only run SQL, and SQLite
     * cannot strip accents.
     */
    private fun fillNameNorm(db: RagConnection) {
        val missing = db.query("SELECT id, display_name FROM documents WHERE name_norm IS NULL") { it.getLong(0) to it.getText(1) }
        if (missing.isEmpty()) return
        db.transaction {
            missing.forEach { (id, name) -> db.exec("UPDATE documents SET name_norm = ? WHERE id = ?", DocumentNames.fold(name), id) }
        }
        logger.i(TAG) { "rag name_norm filled=${missing.size}" }
    }

    private fun loadSchema(): List<String> = try {
        schema()
    } catch (e: Exception) {
        throw RagOpenException("schema: ${e.message}", e)
    }

    /** Called on [dispatcher] after a committed write that changes documents or chunks. */
    private fun notifyChanged() {
        changes.value += 1
    }

    private fun readDocument(s: SQLiteStatement) = StoredDocument(
        id = s.getLong(0),
        displayName = s.getText(1),
        contentHash = s.getText(2),
        mimeType = s.textOrNull(3),
        uri = s.textOrNull(4),
        status = DocumentStatus.fromSql(s.getText(5)),
        chunkCount = s.longOrNull(6)?.toInt(),
        error = s.textOrNull(7),
        included = s.getLong(8) == 1L,
        sizeBytes = s.longOrNull(9),
        pageCount = s.longOrNull(10)?.toInt(),
        localPath = s.textOrNull(11),
        createdAtMs = s.getLong(12),
        updatedAtMs = s.getLong(13),
        skippedPages = s.longOrNull(14)?.toInt(),
        replacesId = s.longOrNull(15),
        folderId = s.longOrNull(16),
        folderName = s.textOrNull(17),
        indexMode = IndexMode.fromSql(s.textOrNull(18)),
        pagesStored = s.getLong(19).toInt(),
    )

    private fun readMeta(db: RagConnection): Map<String, String> =
        db.query("SELECT key, value FROM rag_meta") { it.getText(0) to it.getText(1) }.toMap()

    private fun loadBlock(db: RagConnection, ids: List<Long>, searchableOnly: Boolean, scope: SearchScope): List<ChunkView> {
        val (condition, scopeArgs) = (scope as? SearchScope.Only)?.let(::scopeCondition) ?: (null to emptyArray())
        return db.query(
            """
            SELECT c.id, c.document_id, c.ordinal, c.heading_path, c.page, c.content, c.token_estimate, d.display_name
            FROM chunks c JOIN documents d ON d.id = c.document_id
            WHERE c.id IN (${ids.joinToString(",") { "?" }})${if (searchableOnly) " AND $SEARCHABLE" else ""}${condition?.let { " AND $it" } ?: ""}
            """.trimIndent(),
            *ids.toTypedArray(), *scopeArgs,
        ) { s ->
            ChunkView(
                id = s.getLong(0),
                documentId = s.getLong(1),
                ordinal = s.getLong(2).toInt(),
                headingPath = s.textOrNull(3),
                page = s.longOrNull(4)?.toInt(),
                content = s.getText(5),
                tokenEstimate = s.getLong(6).toInt(),
                documentName = s.getText(7),
            )
        }
    }

    /**
     * *m* of research R7: stored chunks that searches must not return, of documents not done or excluded.
     * The k + m best candidates always hold the k best searchable ones.
     */
    private fun hiddenChunks(db: RagConnection): Int = count(
        db,
        "SELECT COALESCE(SUM((SELECT count(*) FROM chunks c WHERE c.document_id = d.id)), 0) FROM documents d " +
            "WHERE d.status <> 'done' OR d.included = 0",
    )

    /** Runs a write and turns SQLite's «database or disk is full» into [IndexStorageFullException]. */
    private inline fun <T> storageFullAware(block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: RuntimeException) {
        if (e.message?.let(SQLITE_FULL::containsMatchIn) == true) throw IndexStorageFullException(e) else throw e
    }

    private fun chunkCount(db: RagConnection, documentId: Long): Int =
        count(db, "SELECT count(*) FROM chunks WHERE document_id = ?", documentId)

    private fun count(db: RagConnection, sql: String, vararg args: Any?): Int =
        db.queryOne(sql, *args) { it.getLong(0).toInt() } ?: 0

    /** `vector_init` declares `normalized=1`, so distances are only right for unit vectors. */
    private fun requireUnitVector(vector: FloatArray) {
        require(vector.size == config.dims) { "Vector has ${vector.size} components, expected ${config.dims}" }
        val norm = EmbeddingMath.l2Norm(vector)
        require(abs(norm - 1.0) <= UNIT_NORM_TOLERANCE) { "Vector is not unit length (norm $norm)" }
    }

    private fun CompatibilityDecision.Incompatible.toState() = IndexCompatibility.Incompatible(field, expected, found)

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000

    internal companion object {
        /** Documents whose chunks searches may return (F07 rule N7); `d` is the documents table. */
        private const val SEARCHABLE = "d.status = 'done' AND d.included = 1"

        /**
         * The scan runs once, materialized, then is joined and filtered (research R7). Arguments: the
         * query blob, k + m candidates and k.
         */
        const val VECTOR_SEARCH_SQL = "WITH v AS MATERIALIZED (" +
            "SELECT rowid, distance FROM vector_full_scan('chunks', 'embedding', ?, ?)) " +
            "SELECT v.rowid, v.distance FROM v JOIN chunks c ON c.id = v.rowid JOIN documents d ON d.id = c.document_id " +
            "WHERE " + SEARCHABLE + " ORDER BY v.distance LIMIT ?"

        /** F14 (research R3): [VECTOR_SEARCH_SQL] on the INT8 copy; its distances are approximate. */
        const val QUANTIZED_SEARCH_SQL = "WITH v AS MATERIALIZED (" +
            "SELECT rowid, distance FROM vector_quantize_scan('chunks', 'embedding', ?, ?)) " +
            "SELECT v.rowid, v.distance FROM v JOIN chunks c ON c.id = v.rowid JOIN documents d ON d.id = c.document_id " +
            "WHERE " + SEARCHABLE + " ORDER BY v.distance LIMIT ?"

        /** Arguments: the FTS query, k + m candidates and k. */
        const val TEXT_SEARCH_SQL = "WITH f AS MATERIALIZED (" +
            "SELECT rowid, bm25(chunks_fts, 3.0, 1.0) AS score FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY score LIMIT ?) " +
            "SELECT f.rowid FROM f JOIN chunks c ON c.id = f.rowid JOIN documents d ON d.id = c.document_id " +
            "WHERE " + SEARCHABLE + " ORDER BY f.score LIMIT ?"

        /**
         * F17 (research R3): the streaming scan, without k, so the scope filters every row and the top k is
         * exact. Materialized, so it runs once (analyze H1). Arguments: the query blob, the scope's and k.
         */
        fun scopedVectorSearchSql(scopeCondition: String): String = "WITH v AS MATERIALIZED (" +
            "SELECT rowid, distance FROM vector_full_scan('chunks', 'embedding', ?)) " +
            "SELECT v.rowid, v.distance FROM v JOIN chunks c ON c.id = v.rowid JOIN documents d ON d.id = c.document_id " +
            "WHERE " + SEARCHABLE + " AND " + scopeCondition + " ORDER BY v.distance LIMIT ?"

        /** F14 (research R3): [scopedVectorSearchSql] on the INT8 copy, streaming, with the scope after scoring every row. */
        fun scopedQuantizedSearchSql(scopeCondition: String): String = "WITH v AS MATERIALIZED (" +
            "SELECT rowid, distance FROM vector_quantize_scan('chunks', 'embedding', ?)) " +
            "SELECT v.rowid, v.distance FROM v JOIN chunks c ON c.id = v.rowid JOIN documents d ON d.id = c.document_id " +
            "WHERE " + SEARCHABLE + " AND " + scopeCondition + " ORDER BY v.distance LIMIT ?"

        /**
         * F20 (research R2): the streaming scan of the pages, as [scopedVectorSearchSql], with what the chat shows of
         * each one; [scopeCondition] is "1" for the whole library. Arguments: the query blob, the scope's and k.
         */
        fun pageSearchSql(scopeCondition: String): String = "WITH v AS MATERIALIZED (" +
            "SELECT rowid, distance FROM vector_full_scan('pages', 'embedding', ?)) " +
            "SELECT p.document_id, p.page, v.distance, d.display_name, p.file_name, " +
            "CASE WHEN d.mime_type IN ('image/jpeg', 'image/png') THEN 1 ELSE 0 END " +
            "FROM v JOIN pages p ON p.id = v.rowid JOIN documents d ON d.id = p.document_id " +
            "WHERE " + SEARCHABLE + " AND " + scopeCondition + " ORDER BY v.distance LIMIT ?"

        /** F14 (research R10): [pageSearchSql] on the INT8 copy, for the candidates [PAGE_ROWS_SQL] reorders. */
        fun quantizedPageSearchSql(scopeCondition: String): String = "WITH v AS MATERIALIZED (" +
            "SELECT rowid, distance FROM vector_quantize_scan('pages', 'embedding', ?)) " +
            "SELECT p.id FROM v JOIN pages p ON p.id = v.rowid JOIN documents d ON d.id = p.document_id " +
            "WHERE " + SEARCHABLE + " AND " + scopeCondition + " ORDER BY v.distance LIMIT ?"

        /** What [pageSearchSql] gives of each page, with its stored vector, for the exact reorder. */
        private const val PAGE_ROWS_SQL = "SELECT p.id, p.document_id, p.page, d.display_name, p.file_name, " +
            "CASE WHEN d.mime_type IN ('image/jpeg', 'image/png') THEN 1 ELSE 0 END, p.embedding " +
            "FROM pages p JOIN documents d ON d.id = p.document_id "

        /** Every match, then the scope and the top k. Arguments: the FTS query, the scope's and k. */
        fun scopedTextSearchSql(scopeCondition: String): String = "WITH f AS MATERIALIZED (" +
            "SELECT rowid, bm25(chunks_fts, 3.0, 1.0) AS score FROM chunks_fts WHERE chunks_fts MATCH ?) " +
            "SELECT f.rowid FROM f JOIN chunks c ON c.id = f.rowid JOIN documents d ON d.id = c.document_id " +
            "WHERE " + SEARCHABLE + " AND " + scopeCondition + " ORDER BY f.score LIMIT ?"

        /** The documents of [scope] as a condition on `d`, with its arguments; «General» is no folder (research R3). */
        fun scopeCondition(scope: SearchScope.Only): Pair<String, Array<Any?>> {
            val parts = mutableListOf<String>()
            val args = mutableListOf<Any?>()
            if (scope.folderIds.isNotEmpty()) {
                parts += "d.folder_id IN (${scope.folderIds.joinToString(",") { "?" }})"
                args.addAll(scope.folderIds)
            }
            if (scope.general) parts += "d.folder_id IS NULL"
            if (scope.documentIds.isNotEmpty()) {
                parts += "d.id IN (${scope.documentIds.joinToString(",") { "?" }})"
                args.addAll(scope.documentIds)
            }
            return (if (parts.isEmpty()) "0" else parts.joinToString(" OR ", prefix = "(", postfix = ")")) to args.toTypedArray()
        }

        /** androidx.sqlite reports «Error code: 13, message: database or disk is full». */
        private val SQLITE_FULL = Regex("""Error code: 13\b""")

        private const val TAG = "MicroRAG"
        private const val META_TABLE = "rag_meta"

        /** A shadow's hash: unique while it lives, and never equal to a file's, so F06 duplicates ignore it. */
        private const val SHADOW_HASH_SUFFIX = "#reindex"

        /** F19 research R13: a document indexed with pages and without chunks (Visual, or Both with no text). */
        private const val PAGES_ONLY = "(d.index_mode <> 'text' AND COALESCE(d.chunk_count, 0) = 0)"

        /** F20 research R5: the document has at least one stored page. */
        private const val HAS_PAGES = "EXISTS (SELECT 1 FROM pages p WHERE p.document_id = d.id)"

        /** Below SQLite's default limit of variables, two per key. */
        private const val MAX_KEYS_PER_QUERY = 400

        /** F14 (T049): the head of the INT8 copy [quantizedSearch] reads first, in times the candidates. */
        private const val QUANTIZED_HEAD_FACTOR = 2

        /** [libraryPage] reads the shadow's status after the [DOCUMENT_COLUMNS]. */
        private const val SHADOW_STATUS_COLUMN = 20
        private const val LOAD_BLOCK = 500
        private const val UNIT_NORM_TOLERANCE = 1e-3

        /** The cosine distance of unit vectors is 1 − their dot product, as SQLite-Vector computes it. */
        private const val EXACT_COSINE_ONE = 1.0
        private const val DOCUMENT_COLUMNS = "id, display_name, content_hash, mime_type, uri, status, chunk_count, error, " +
            "included, size_bytes, page_count, local_path, created_at_ms, updated_at_ms, skipped_pages, replaces_id, " +
            "folder_id, (SELECT f.name FROM folders f WHERE f.id = documents.folder_id), index_mode, " +
            "(SELECT count(*) FROM pages p WHERE p.document_id = documents.id)"
    }
}

private fun androidx.sqlite.SQLiteStatement.textOrNull(index: Int): String? = if (isNull(index)) null else getText(index)

private fun androidx.sqlite.SQLiteStatement.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

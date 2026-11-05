package com.lisdatasolutions.microrag.data.library

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.ingest.PageFileStore
import com.lisdatasolutions.microrag.domain.library.PageImageRepository
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The page images F19 stored (F20 contract I1, I2): the row in `pages` names the file, read on [io]. A missing row
 * or file is null, and the log only counts it, never with a name or a path.
 */
class DefaultPageImageRepository(
    private val index: RagIndex,
    private val files: PageFileStore,
    private val io: CoroutineDispatcher,
    private val logger: AppLogger,
) : PageImageRepository {

    override suspend fun image(documentId: Long, page: Int): ByteArray? {
        val name = try {
            index.pageFile(documentId, page)
        } catch (e: IndexUnavailableException) {
            null
        } ?: return missing("row")
        return withContext(io) {
            try {
                files.file(name).takeIf { it.isFile }?.readBytes()
            } catch (e: IOException) {
                null
            }
        } ?: missing("file")
    }

    private fun missing(what: String): ByteArray? {
        logger.i(TAG) { "page image missing=$what" }
        return null
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}

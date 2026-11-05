package com.lisdatasolutions.microrag.domain.library

import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.PageKey
import com.lisdatasolutions.microrag.domain.rag.RagIndex

/** The page images F19 stored (F20 research R14): what the chat and the document detail show. */
interface PageImageRepository {
    /** The JPEG of [page] of [documentId] as F19 stored it, or null if its row or its file is gone. */
    suspend fun image(documentId: Long, page: Int): ByteArray?
}

/** F20 (research R10, R15): whether a cited page is still stored, for the document detail; false when it cannot be read. */
class PageExistsUseCase(private val index: RagIndex) {
    suspend operator fun invoke(documentId: Long, page: Int): Boolean = try {
        index.existingPages(listOf(PageKey(documentId, page))).isNotEmpty()
    } catch (e: IndexUnavailableException) {
        false
    }
}

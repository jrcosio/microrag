package com.lisdatasolutions.microrag.domain.library

/** The page images F19 stored (F20 research R14): what the chat and the document detail show. */
interface PageImageRepository {
    /** The JPEG of [page] of [documentId] as F19 stored it, or null if its row or its file is gone. */
    suspend fun image(documentId: Long, page: Int): ByteArray?
}

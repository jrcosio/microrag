package com.lisdatasolutions.microrag.feature.document

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lisdatasolutions.microrag.domain.library.PageImageRepository
import com.lisdatasolutions.microrag.domain.rag.PageKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes the page images F19 stored, off the main thread (F20 research R14): thumbnails at a quarter of the
 * 768 px JPEG, kept in a small cache so a scroll through a conversation decodes each once; the full page for the
 * detail and the viewer, never cached. Shared by the chat and the document detail.
 */
class PageImageLoader(
    private val images: PageImageRepository,
    private val decode: CoroutineDispatcher = Dispatchers.Default,
) {
    private val thumbnails = LruCache<PageKey, ImageBitmap>(THUMBNAILS)

    suspend fun thumbnail(documentId: Long, page: Int): ImageBitmap? {
        val key = PageKey(documentId, page)
        thumbnails.get(key)?.let { return it }
        return load(documentId, page, THUMBNAIL_SAMPLE)?.also { thumbnails.put(key, it) }
    }

    suspend fun page(documentId: Long, page: Int): ImageBitmap? = load(documentId, page, sample = 1)

    private suspend fun load(documentId: Long, page: Int, sample: Int): ImageBitmap? {
        val bytes = images.image(documentId, page) ?: return null
        return withContext(decode) {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }
    }

    private companion object {
        const val THUMBNAILS = 24

        /** 768 px over 4 is 192 px: sharp at 56 dp up to xxxhdpi. */
        const val THUMBNAIL_SAMPLE = 4
    }
}

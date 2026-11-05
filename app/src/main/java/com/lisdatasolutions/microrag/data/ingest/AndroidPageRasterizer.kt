package com.lisdatasolutions.microrag.data.ingest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.lisdatasolutions.microrag.rag.ingest.EncodedImage
import com.lisdatasolutions.microrag.rag.ingest.ImageFormat
import com.lisdatasolutions.microrag.rag.ingest.PageRasterizer
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pages and images as the visual index stores them (F19 research R7, F18 research R6): PdfRenderer, which works
 * on API 31 without the S extension 13 of the text extraction, and ImageDecoder, which applies the EXIF
 * orientation. Always on white. The only place PdfRenderer and ImageDecoder are used; blocking, call it on io.
 */
class AndroidPageRasterizer : PageRasterizer {

    override fun pageCount(pdf: File): Int = open(pdf) { renderer -> renderer.pageCount }

    override fun renderPage(pdf: File, page: Int, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage =
        open(pdf) { renderer ->
            require(page in 1..renderer.pageCount) { "page $page of ${renderer.pageCount}" }
            renderer.openPage(page - 1).use { pdfPage ->
                val scale = longSidePx.toFloat() / max(pdfPage.width, pdfPage.height)
                val width = (pdfPage.width * scale).roundToInt().coerceAtLeast(1)
                val height = (pdfPage.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = createBitmap(width, height)
                try {
                    // PdfRenderer draws only the page's own content: the white paper comes first.
                    bitmap.eraseColor(Color.WHITE)
                    pdfPage.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    EncodedImage(encode(bitmap, format, quality), width, height)
                } finally {
                    bitmap.recycle()
                }
            }
        }

    override fun scaleImage(image: File, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(image)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > longSidePx) {
                val factor = longSidePx.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * factor).roundToInt().coerceAtLeast(1),
                    (info.size.height * factor).roundToInt().coerceAtLeast(1),
                )
            }
        }
        val onWhite = createBitmap(decoded.width, decoded.height)
        try {
            Canvas(onWhite).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, 0f, 0f, null)
            }
            return EncodedImage(encode(onWhite, format, quality), onWhite.width, onWhite.height)
        } finally {
            decoded.recycle()
            onWhite.recycle()
        }
    }

    private fun encode(bitmap: Bitmap, format: ImageFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val compressFormat = when (format) {
            ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        check(bitmap.compress(compressFormat, quality, out)) { "could not encode the image as $format" }
        return out.toByteArray()
    }

    private fun <T> open(pdf: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(block)
        }
}

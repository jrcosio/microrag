package com.lisdatasolutions.microrag.rag.ingest

import java.io.File

enum class ImageFormat { WEBP, JPEG, PNG }

class EncodedImage(val bytes: ByteArray, val widthPx: Int, val heightPx: Int)

/**
 * Pages of a PDF and loose images as the models receive them (F19 research R7, F18 research R6).
 * The only implementation lives in data/ingest; pages are 1-based, as on the desktop. Blocking: call it on io.
 */
interface PageRasterizer {
    fun pageCount(pdf: File): Int

    /** Renders [page] on white, scaled so its long side is [longSidePx], encoded as [format] at [quality]. */
    fun renderPage(pdf: File, page: Int, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage

    /** Decodes a JPG or PNG upright (EXIF), on white, scaled down so its long side is at most [longSidePx]. */
    fun scaleImage(image: File, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage
}

package com.lisdatasolutions.microrag.data.ingest

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.rag.ingest.ImageFormat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F18 T045 (research R6): PdfRenderer and ImageDecoder on the device, without models. Uses the F06 PDF
 * fixtures and the images of `offline/gen_image_fixtures.py`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPageRasterizerTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val rasterizer = AndroidPageRasterizer()

    private fun asset(name: String): File {
        val target = File(instrumentation.targetContext.cacheDir, "raster-test/${name.substringAfterLast('/')}")
        target.parentFile!!.mkdirs()
        instrumentation.context.assets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    private fun decode(bytes: ByteArray) = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

    @Test
    fun aPageIsRenderedOnWhiteAtItsLongSide() {
        val pdf = asset("pdf/text_3_pages.pdf")

        assertThat(rasterizer.pageCount(pdf)).isEqualTo(3)
        listOf(ImageFormat.WEBP, ImageFormat.JPEG, ImageFormat.PNG).forEach { format ->
            val image = rasterizer.renderPage(pdf, 1, 768, format, 85)
            val bitmap = decode(image.bytes)
            assertThat(maxOf(bitmap.width, bitmap.height)).isEqualTo(768)
            assertThat(bitmap.width to bitmap.height).isEqualTo(image.widthPx to image.heightPx)
            val corner = bitmap.getPixel(1, 1)
            assertThat(Color.red(corner)).isAtLeast(WHITE_ENOUGH)
            assertThat(Color.green(corner)).isAtLeast(WHITE_ENOUGH)
            assertThat(Color.blue(corner)).isAtLeast(WHITE_ENOUGH)
        }
    }

    @Test
    fun pagesAreOneBased() {
        val pdf = asset("pdf/text_3_pages.pdf")

        rasterizer.renderPage(pdf, 3, 512, ImageFormat.PNG, 85)
        val outside = runCatching { rasterizer.renderPage(pdf, 0, 512, ImageFormat.PNG, 85) }
        assertThat(outside.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun aScannedPdfRendersToo() {
        val pdf = asset("pdf/scanned.pdf")

        val image = rasterizer.renderPage(pdf, 1, 1024, ImageFormat.WEBP, 85)
        assertThat(maxOf(image.widthPx, image.heightPx)).isEqualTo(1024)
    }

    /** R8: AEMET's PDFs only carry an owner password; whether PdfRenderer opens them is recorded, not assumed. */
    @Test
    fun anOwnerPasswordPdfOpensOrIsRecorded() {
        val pdf = asset("pdf/restricted.pdf")

        val outcome = runCatching { rasterizer.renderPage(pdf, 1, 768, ImageFormat.WEBP, 85) }
        Log.i(TAG, "raster restricted.pdf opened=${outcome.isSuccess} ${outcome.exceptionOrNull()?.let { it::class.simpleName }.orEmpty()}")
        outcome.exceptionOrNull()?.let { assertThat(it).isInstanceOf(SecurityException::class.java) }
    }

    @Test
    fun anExifRotatedJpegComesOutUpright() {
        val image = rasterizer.scaleImage(asset("image/exif_rotated.jpg"), 300, ImageFormat.PNG, 85)

        val bitmap = decode(image.bytes)
        assertThat(bitmap.width to bitmap.height).isEqualTo(150 to 300)
        // Turned 90° clockwise, the stored left half (black) ends at the top.
        assertThat(Color.red(bitmap.getPixel(75, 20))).isLessThan(DARK_ENOUGH)
        assertThat(Color.red(bitmap.getPixel(75, 280))).isAtLeast(WHITE_ENOUGH)
    }

    @Test
    fun transparencyGoesOnWhite() {
        val bitmap = decode(rasterizer.scaleImage(asset("image/alpha.png"), 100, ImageFormat.PNG, 85).bytes)

        assertThat(bitmap.getPixel(5, 5)).isEqualTo(Color.WHITE)
        assertThat(Color.red(bitmap.getPixel(50, 25))).isLessThan(DARK_ENOUGH)
    }

    /** CMYK JPEGs are recorded, not assumed: the IMAGE_DECODE block reports them as failed if they do not decode. */
    @Test
    fun aCmykJpegDecodesOrIsRecorded() {
        val outcome = runCatching { rasterizer.scaleImage(asset("image/cmyk.jpg"), 80, ImageFormat.WEBP, 85) }
        Log.i(TAG, "raster cmyk.jpg decoded=${outcome.isSuccess} ${outcome.exceptionOrNull()?.let { it::class.simpleName }.orEmpty()}")
        outcome.getOrNull()?.let { assertThat(it.widthPx).isEqualTo(80) }
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val WHITE_ENOUGH = 245
        const val DARK_ENOUGH = 60
    }
}

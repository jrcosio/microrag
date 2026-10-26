package com.lisdatasolutions.microrag.data.ingest

import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.annotation.RequiresExtension
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Early check of the system PDF API on each device (F06 T004, research R2/R3): extension level,
 * how PDFium separates lines, and the exceptions for encrypted and damaged files.
 */
@RunWith(AndroidJUnit4::class)
class PdfPlatformProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    @Before
    fun logExtension() {
        Log.i(TAG, "sdk=${Build.VERSION.SDK_INT} s-extension=${SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S)}")
    }

    @Test
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    fun textPagesKeepTheirLines() {
        assumeSupported()
        val pages = pageTexts("text_3_pages.pdf")

        pages.forEachIndexed { index, text -> Log.i(TAG, "page ${index + 1}: ${escape(text)}") }
        assertThat(pages).hasSize(3)
        assertThat(pages[0]).contains("La configura-")
        assertThat(pages[0]).contains("ción del equipo")
        assertThat(pages[1]).contains("¿Funciona? ¡Sí!")
    }

    @Test
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    fun scannedPagesHaveNoText() {
        assumeSupported()
        assertThat(pageTexts("scanned.pdf").map { it.trim() }).containsExactly("", "")
        val partial = pageTexts("partially_scanned.pdf")
        Log.i(TAG, "partially scanned: ${partial.map { it.length }}")
        assertThat(partial[1].trim()).isEmpty()
    }

    @Test
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    fun encryptedNeedsAPasswordAndRestrictedOpens() {
        assumeSupported()
        val error = runCatching { pageTexts("encrypted.pdf") }.exceptionOrNull()
        Log.i(TAG, "encrypted: $error")
        assertThat(error).isInstanceOf(SecurityException::class.java)
        assertThat(pageTexts("restricted.pdf")[0]).contains("Manual de prueba")
    }

    @Test
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    fun damagedFilesFailToOpen() {
        assumeSupported()
        for (name in listOf("truncated.pdf", "fake.pdf")) {
            val error = runCatching { pageTexts(name) }.exceptionOrNull()
            Log.i(TAG, "$name: $error")
            assertThat(error).isInstanceOf(IOException::class.java)
        }
    }

    private fun assumeSupported() =
        assumeTrue("S extension < 13", SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13)

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun pageTexts(asset: String): List<String> {
        val file = File(context.cacheDir, asset).apply { testAssets.open("pdf/$asset").use { input -> outputStream().use(input::copyTo) } }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRendererPreV(fd).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        // No lambda over PdfPageTextContent: its synthetic method would break reflection on
                        // devices without the extension (research R3).
                        buildString { for (content in page.textContents) append(content.text) }
                    }
                }
            }
        }
    }

    private fun escape(text: String) = text.replace("\r", "\\r").replace("\n", "\\n")

    private companion object {
        const val TAG = "PdfProbe"
    }
}

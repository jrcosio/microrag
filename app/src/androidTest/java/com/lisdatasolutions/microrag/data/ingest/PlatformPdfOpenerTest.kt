package com.lisdatasolutions.microrag.data.ingest

import android.os.Build
import android.os.ext.SdkExtensions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.rag.ingest.PdfDamagedException
import com.lisdatasolutions.microrag.rag.ingest.PdfEncryptedException
import com.lisdatasolutions.microrag.rag.ingest.PdfUnavailableException
import java.io.File
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** F06 T010: the system PDF reader on the fixtures of offline/gen_pdf_fixtures.py (research R2, R3). */
@RunWith(AndroidJUnit4::class)
class PlatformPdfOpenerTest {

    private val opener = PlatformPdfOpener()

    private fun fixture(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return File(instrumentation.targetContext.cacheDir, name).apply {
            instrumentation.context.assets.open("pdf/$name").use { input -> outputStream().use(input::copyTo) }
        }
    }

    @Test
    fun availabilityFollowsTheExtension() {
        assertThat(opener.isAvailable).isEqualTo(SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13)
    }

    @Test
    fun readsPagesAndTheirText() {
        assumeTrue(opener.isAvailable)
        opener.open(fixture("text_3_pages.pdf")).use { pages ->
            assertThat(pages.pageCount).isEqualTo(3)
            assertThat(pages.pageText(0)).contains("La configura-\r\nción del equipo")
            assertThat(pages.pageText(2)).contains("1.250 euros")
        }
        opener.open(fixture("scanned.pdf")).use { pages ->
            assertThat((0 until pages.pageCount).map { pages.pageText(it).isBlank() }).containsExactly(true, true)
        }
        opener.open(fixture("restricted.pdf")).use { pages -> assertThat(pages.pageText(0)).contains("Manual de prueba") }
    }

    @Test
    fun encryptedAndDamagedFilesFail() {
        assumeTrue(opener.isAvailable)
        assertThat(runCatching { opener.open(fixture("encrypted.pdf")) }.exceptionOrNull())
            .isInstanceOf(PdfEncryptedException::class.java)
        for (name in listOf("truncated.pdf", "fake.pdf")) {
            assertThat(runCatching { opener.open(fixture(name)) }.exceptionOrNull()).isInstanceOf(PdfDamagedException::class.java)
        }
    }

    @Test
    fun withoutTheExtensionOpeningIsRefused() {
        assumeFalse(opener.isAvailable)
        assertThat(runCatching { opener.open(fixture("text_3_pages.pdf")) }.exceptionOrNull())
            .isInstanceOf(PdfUnavailableException::class.java)
    }
}

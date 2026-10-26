package com.lisdatasolutions.microrag.data.ingest

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F06 T022: metadata and stream of a picked file, with the fallbacks of research R4. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SafDocumentSourceTest {

    private val source = SafDocumentSource(ApplicationProvider.getApplicationContext<Application>().contentResolver)

    @Test
    fun fileUrisGiveNameSizeAndContent() {
        val file = Files.createTempFile("Notas de reunión", ".md").toFile().apply { writeText("# Acta") }

        val info = source.describe(Uri.fromFile(file).toString())

        assertThat(info.displayName).isEqualTo(file.name)
        assertThat(info.sizeBytes).isEqualTo(file.length())
        assertThat(source.open(Uri.fromFile(file).toString()).use { it.readBytes().decodeToString() }).isEqualTo("# Acta")
        file.delete()
    }

    @Test
    fun aMissingFileIsReportedAsNotFound() {
        val missing = Uri.fromFile(File("/nonexistent/doc.pdf")).toString()

        assertThat(runCatching { source.describe(missing) }.exceptionOrNull()).isInstanceOf(FileNotFoundException::class.java)
    }

    @Test
    fun anUnknownProviderFallsBackToTheLastSegment() {
        val info = source.describe("content://com.example.missing/document/primary%3ADownload%2Finforme.pdf")

        assertThat(info.displayName).isEqualTo("informe.pdf")
        assertThat(info.sizeBytes).isNull()
    }
}

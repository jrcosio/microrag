package com.lisdatasolutions.microrag.data.ingest

import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-004 and SC-005 (F06 T067, research R12): measured on every device and logged with
 * `ingest-benchmark`; the thresholds apply on the reference phone only (D-022).
 */
@RunWith(AndroidJUnit4::class)
class IngestBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "ingest-bench-${UUID.randomUUID()}").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val logger = CapturingLogger()
    private val files = DocumentFileStore(File(root, "documents"))
    private val pdf = PlatformPdfOpener()
    private val source = SafDocumentSource(context.contentResolver)
    private val isReferencePhone = Build.MODEL == REFERENCE_MODEL

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    @Test
    fun aTenMegabytePdfIsReadyInUnderThreeSeconds() = runBlocking<Unit> {
        assumeTrue(pdf.isAvailable)
        val file = tenMegabytePdf()
        val uri = Uri.fromFile(file).toString()
        val importer = DocumentImporter(source, files, pdf, temp.index, { Long.MAX_VALUE / 2 }, logger)

        val started = SystemClock.elapsedRealtime()
        val result = importer.import(uri, source.describe(uri))
        val elapsed = SystemClock.elapsedRealtime() - started

        Log.i(TAG, "ingest-benchmark SC-004 model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} bytes=${file.length()} import_ms=$elapsed")
        assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
        if (isReferencePhone) assertThat(elapsed).isLessThan(3_000L)
    }

    @Test
    fun twoHundredPagesAreExtractedAndChunkedInUnderThirtySeconds() = runBlocking<Unit> {
        assumeTrue(pdf.isAvailable)
        val file = File(root, "text_200_pages.pdf").apply {
            instrumentation.context.assets.open("pdf/text_200_pages.pdf").use { input -> outputStream().use(input::copyTo) }
        }
        val importer = DocumentImporter(source, files, pdf, temp.index, { Long.MAX_VALUE / 2 }, logger)
        val uri = Uri.fromFile(file).toString()
        val id = (importer.import(uri, source.describe(uri)) as ImportResult.Imported).documentId
        val runner = IngestRunner(temp.index, DocumentExtractor(pdf), FakeEmbeddingSessions.withoutModel(), InMemoryIngestRunStore(), FakeThermalStatusSource(), { Long.MAX_VALUE }, Dispatchers.IO, logger)

        Runtime.getRuntime().gc()
        val baselineKb = Debug.getPss()
        var peakKb = baselineKb
        val sampler = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    peakKb = maxOf(peakKb, Debug.getPss())
                    Thread.sleep(SAMPLE_MS)
                }
            } catch (_: InterruptedException) {
                // Done.
            }
        }.apply { start() }
        val started = SystemClock.elapsedRealtime()
        runner.process(listOf(id)) { }
        val elapsed = SystemClock.elapsedRealtime() - started
        sampler.interrupt()
        sampler.join()
        val growthMb = (peakKb - baselineKb) / 1024.0

        val document = temp.index.document(id)!!
        Log.i(
            TAG,
            "ingest-benchmark SC-005 model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} pages=${document.pageCount} " +
                "chunks=${document.chunkCount} process_ms=$elapsed pss_growth_mb=${"%.1f".format(growthMb)}",
        )
        assertThat(document.status).isEqualTo(DocumentStatus.PARSED)
        if (isReferencePhone) {
            assertThat(elapsed).isLessThan(30_000L)
            assertThat(growthMb).isAtMost(100.0)
        }
    }

    /**
     * A valid PDF of 10 MB written by hand: a few text pages plus one unreferenced stream of random
     * bytes. Android's PdfDocument recompresses images, so it cannot reach a given size.
     */
    private fun tenMegabytePdf(): File {
        val objects = mutableListOf<ByteArray>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>".toByteArray()
        objects += ByteArray(0) // the page tree, once the pages are known
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>".toByteArray()
        val kids = mutableListOf<Int>()
        repeat(5) { page ->
            val content = (0 until 40).joinToString("\n", "BT /F1 11 Tf 72 750 Td 14 TL\n", "\nET") { line ->
                "(Pagina ${page + 1}, linea $line: texto de prueba del documento grande.) Tj T*"
            }.toByteArray(Charsets.ISO_8859_1)
            objects += "<< /Length ${content.size} >>\nstream\n".toByteArray() + content + "\nendstream".toByteArray()
            objects += ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> " +
                "/Contents ${objects.size} 0 R >>").toByteArray()
            kids += objects.size
        }
        objects[1] = "<< /Type /Pages /Kids [${kids.joinToString(" ") { "$it 0 R" }}] /Count ${kids.size} >>".toByteArray()
        val padding = Random(20261026).nextBytes(TEN_MB.toInt())
        objects += "<< /Length ${padding.size} >>\nstream\n".toByteArray() + padding + "\nendstream".toByteArray()

        val file = File(root, "diez-megas.pdf")
        file.outputStream().buffered().use { out ->
            var offset = 0L
            fun write(bytes: ByteArray) {
                out.write(bytes)
                offset += bytes.size
            }
            write("%PDF-1.4\n".toByteArray())
            val offsets = objects.mapIndexed { index, body ->
                val start = offset
                write("${index + 1} 0 obj\n".toByteArray())
                write(body)
                write("\nendobj\n".toByteArray())
                start
            }
            val xref = offset
            write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n".toByteArray())
            offsets.forEach { write("%010d 00000 n \n".format(it).toByteArray()) }
            write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray())
        }
        return file
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val REFERENCE_MODEL = "A063"
        const val TEN_MB = 10_000_000L
        const val SAMPLE_MS = 100L
    }
}

package com.lisdatasolutions.microrag.data.ingest

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.device.PowerManagerThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.PageEmbeddingSession
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F19 T043 (SC-003, FR-014, research R9 and R14): a 200-page PDF in Visual with the real 440M on the reference
 * phone. The first pass is stopped halfway and the second one resumes from the stored pages; the pages per minute
 * and the bytes per page go to `files/reports` as figures to report, not as a gate (D-078). Generated pages only:
 * no document of the user is opened.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class VisualIngestBenchmarkTest {

    private val context = TempRagIndex.context

    /** [count] A4 pages of generated text, so the renderer has real work and nothing personal is read. */
    private fun generatedPdf(file: File, count: Int): File {
        val document = PdfDocument()
        val paint = Paint().apply { textSize = 11f }
        try {
            repeat(count) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                repeat(LINES) { line ->
                    page.canvas.drawText("Página ${index + 1}, línea ${line + 1}: revisión del motor, presión de 2,4 bar y filtro F-${index * LINES + line}.", 40f, 60f + line * 18f, paint)
                }
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return file
    }

    @Test
    fun twoHundredPages_inVisual_resumeHalfway_andReportTheirRate() = runBlocking<Unit> {
        val models = InstalledModels(context)
        assumeTrue("EmbeddingGemma 2 440M is not installed", models.isReady(ModelId.EG2_440M))
        val root = File(context.cacheDir, "visual-benchmark-${UUID.randomUUID()}").apply { mkdirs() }
        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(CapturingLogger())
        TempRagIndex().use { temp ->
            try {
                val pdf = generatedPdf(File(root, "informe.pdf"), PAGES)
                temp.index.open()
                val id = temp.index.addDocument(
                    NewDocument(
                        displayName = "informe.pdf", contentHash = "benchmark", mimeType = "application/pdf", pageCount = PAGES,
                        localPath = pdf.path, indexMode = IndexMode.VISUAL,
                    ),
                )
                val pages = PageFileStore(File(root, "pages"), dispatchers.io)
                val runStore = InMemoryIngestRunStore()
                fun runner() = IngestRunner(
                    temp.index, DocumentExtractor(PlatformPdfOpener()), FakeEmbeddingSessions(), runStore, PowerManagerThermalStatusSource(context),
                    { File(context.cacheDir.path).usableSpace }, dispatchers.io, temp.logger,
                    pages = IngestRunner.PagePhase(
                        sessions = PageEmbeddingSession(coordinator, LiteRtEngineFactory(context, dispatchers), { models.pathOf(ModelId.EG2_440M) }, temp.logger),
                        rasterizer = AndroidPageRasterizer(),
                        files = pages,
                        enabled = { true },
                    ),
                )

                // First pass, stopped once half the pages are stored, as a killed app would be.
                val firstStarted = System.nanoTime()
                val first = launch { runner().process(listOf(id)) { } }
                while (temp.index.storedPages(id).size < PAGES / 2 && first.isActive) delay(POLL_MS)
                first.cancel()
                first.join()
                val firstPages = temp.index.storedPages(id).size
                val firstSeconds = (System.nanoTime() - firstStarted) / 1e9

                val secondStarted = System.nanoTime()
                runner().process(listOf(id)) { }
                val secondSeconds = (System.nanoTime() - secondStarted) / 1e9

                val stored = temp.index.storedPages(id)
                assertThat(stored).containsExactlyElementsIn(1..PAGES)
                assertThat(temp.index.document(id)!!.status).isEqualTo(DocumentStatus.DONE)
                val bytes = File(root, "pages").listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
                val report = buildJsonObject {
                    put("pages", PAGES)
                    put("first_pass_pages", firstPages)
                    put("first_pass_s", firstSeconds)
                    put("second_pass_pages", PAGES - firstPages)
                    put("second_pass_s", secondSeconds)
                    put("pages_per_minute", (PAGES - firstPages) * 60.0 / secondSeconds)
                    put("bytes_per_page", bytes / PAGES)
                }
                val out = File(context.filesDir, "reports").apply { mkdirs() }.resolve("f19-visual-ingest-${System.currentTimeMillis()}.json")
                out.writeText(Json.encodeToString(JsonObject.serializer(), report))
                Log.i(TAG, "visual ingest $report")
            } finally {
                coordinator.releaseAll()
                dispatchers.close()
                root.deleteRecursively()
            }
        }
    }

    private companion object {
        const val TAG = "MicroRAG-F19"
        const val PAGES = 200
        const val LINES = 40
        const val POLL_MS = 500L
    }
}

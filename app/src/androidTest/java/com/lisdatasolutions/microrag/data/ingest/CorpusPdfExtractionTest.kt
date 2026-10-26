package com.lisdatasolutions.microrag.data.ingest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.rag.ingest.PdfExtraction
import java.io.File
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F06 T068 (research R12): the page blocks the app extracts from the F05 corpus PDFs, written to
 * `device-extraction.jsonl` for `run_eval.py device-extraction`. Skipped unless the PDFs were copied
 * with adb to the app's external files, folder `corpus` (quickstart §5). Informative, no threshold.
 */
@RunWith(AndroidJUnit4::class)
class CorpusPdfExtractionTest {

    @Test
    fun writesThePageBlocksOfTheCorpusPdfs() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("corpus")
        val pdfs = dir?.listFiles { file -> file.extension == "pdf" }?.sortedBy { it.name }.orEmpty()
        assumeTrue("no corpus PDFs in $dir", pdfs.isNotEmpty())
        val opener = PlatformPdfOpener()
        assumeTrue(opener.isAvailable)

        File(dir, "device-extraction.jsonl").bufferedWriter().use { out ->
            for (pdf in pdfs) {
                opener.open(pdf).use { pages ->
                    for (index in 0 until pages.pageCount) {
                        val block = PdfExtraction.pageBlock(pages.pageText(index), index + 1)
                        val row = buildJsonObject {
                            put("file", JsonPrimitive(pdf.name))
                            put("page", JsonPrimitive(index + 1))
                            put("block", block?.let { JsonPrimitive(it.text) } ?: JsonNull)
                        }
                        out.write(row.toString())
                        out.write("\n")
                    }
                }
            }
        }
    }
}

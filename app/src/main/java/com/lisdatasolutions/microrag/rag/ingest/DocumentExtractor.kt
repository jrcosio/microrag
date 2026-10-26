package com.lisdatasolutions.microrag.rag.ingest

import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import java.io.File

/**
 * Reads a private copy into blocks, in order and in streaming (F06 research R2, R8, R10). Blocking:
 * call it on the IO dispatcher. [onPage] and [onBlock] may throw to stop early (cancellation); the
 * PDF is closed either way.
 */
class DocumentExtractor(
    private val pdf: PdfOpener,
    private val maxBlockChars: Int = ExtractionLimits.MAX_BLOCK_CHARS,
) {

    /** Throws [PdfUnavailableException], [PdfEncryptedException], [PdfDamagedException] or an I/O error. */
    fun extract(
        file: File,
        format: DocumentFormat,
        onPage: (done: Int, total: Int) -> Unit = { _, _ -> },
        onBlock: (RawBlock) -> Unit,
    ): ExtractionSummary = when (format) {
        DocumentFormat.PDF -> pdf.open(file).use { pages ->
            var blocks = 0
            var skipped = 0
            val total = pages.pageCount
            for (index in 0 until total) {
                val block = PdfExtraction.pageBlock(pages.pageText(index), page = index + 1)
                if (block == null) {
                    skipped++
                } else {
                    blocks++
                    onBlock(block)
                }
                onPage(index + 1, total)
            }
            ExtractionSummary(blocks, pages = total, skippedPages = skipped)
        }
        DocumentFormat.TXT -> extractText(file, onBlock) { lines, emit -> PlainTextExtractor(maxBlockChars).extract(lines, emit) }
        DocumentFormat.MARKDOWN -> extractText(file, onBlock) { lines, emit -> MarkdownExtractor(maxBlockChars).extract(lines, emit) }
    }

    private fun extractText(
        file: File,
        onBlock: (RawBlock) -> Unit,
        extractor: (Sequence<String>, (RawBlock) -> Unit) -> Unit,
    ): ExtractionSummary {
        var blocks = 0
        TextDecoding.reader(file).use { reader ->
            extractor(TextDecoding.lines(reader)) { block ->
                blocks++
                onBlock(block)
            }
        }
        return ExtractionSummary(blocks, pages = null, skippedPages = null)
    }
}

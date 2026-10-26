package com.lisdatasolutions.microrag.rag.ingest

import java.io.Closeable
import java.io.File

/** A run of text with its heading path and page, as the report's parsers produce it (§Parsing y chunking). */
data class RawBlock(val headingPath: List<String>, val text: String, val page: Int?)

/** A chunk of a document; [headingPath] joins the path with " › " (report's Chunker). */
data class Chunk(val ordinal: Int, val headingPath: String, val content: String, val page: Int?)

/** What the extraction saw: [pages] and [skippedPages] are known for PDF only. */
data class ExtractionSummary(val blocks: Int, val pages: Int?, val skippedPages: Int?)

object ExtractionLimits {
    /**
     * Longer sections are emitted as several blocks with the same path, split at a paragraph, so
     * memory stays bounded; the chunks are the same as with one block (F06 research R10).
     */
    const val MAX_BLOCK_CHARS = 256 * 1024
}

/** The pages of an open PDF; [pageText] takes a 0-based index. */
interface PdfPages : Closeable {
    val pageCount: Int

    fun pageText(index: Int): String
}

/** Opens PDF files with the system reader when the device has it (F06 research R2, R3). */
interface PdfOpener {
    val isAvailable: Boolean

    /** Throws [PdfUnavailableException], [PdfEncryptedException] or [PdfDamagedException]. */
    fun open(file: File): PdfPages
}

class PdfUnavailableException : Exception("This device cannot read PDF files")

class PdfEncryptedException(cause: Throwable? = null) : Exception("The PDF needs a password", cause)

class PdfDamagedException(cause: Throwable? = null) : Exception("The PDF cannot be read", cause)

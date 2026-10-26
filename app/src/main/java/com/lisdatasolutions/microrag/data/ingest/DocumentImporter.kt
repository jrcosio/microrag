package com.lisdatasolutions.microrag.data.ingest

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportLimits
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.rag.DuplicateDocumentException
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.rag.ingest.PdfDamagedException
import com.lisdatasolutions.microrag.rag.ingest.PdfEncryptedException
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import com.lisdatasolutions.microrag.rag.ingest.PdfUnavailableException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

sealed interface ImportResult {
    data class Imported(val documentId: Long) : ImportResult

    data class Rejected(val error: IngestError) : ImportResult
}

/**
 * Turns one picked file into a pending document with its private copy (F06 research R5, contracts
 * I1–I3, I10–I12). Blocking file work: call it on the IO dispatcher; it checks for cancellation
 * between copy blocks. Logs carry codes and sizes only, never names, URIs or paths.
 */
class DocumentImporter(
    private val source: DocumentSource,
    private val files: DocumentFileStore,
    private val pdf: PdfOpener,
    private val index: RagIndex,
    private val freeBytes: () -> Long,
    private val logger: AppLogger,
) {

    /** Extension first; only a name without extension falls back to the provider's MIME type (research R4). */
    fun detectFormat(displayName: String, mimeType: String?): DocumentFormat? {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val hasExtension = extension.length in 1..MAX_EXTENSION_CHARS && extension.all { it.isLetterOrDigit() }
        return if (hasExtension) EXTENSIONS[extension] else MIME_TYPES[mimeType?.lowercase()]
    }

    suspend fun import(uri: String, info: SourceInfo, onProgress: (copied: Long, total: Long?) -> Unit = { _, _ -> }): ImportResult {
        val result = try {
            importChecked(uri, info, onProgress)
        } catch (e: Rejection) {
            ImportResult.Rejected(e.error)
        } catch (e: IndexUnavailableException) {
            ImportResult.Rejected(IngestError.INDEX_UNAVAILABLE)
        }
        when (result) {
            is ImportResult.Imported -> logger.i(TAG) { "ingest import ok id=${result.documentId}" }
            is ImportResult.Rejected -> logger.i(TAG) { "ingest import rejected ${result.error.code}" }
        }
        return result
    }

    private suspend fun importChecked(uri: String, info: SourceInfo, onProgress: (Long, Long?) -> Unit): ImportResult {
        val format = detectFormat(info.displayName, info.mimeType) ?: reject(IngestError.UNSUPPORTED_FORMAT)
        if (index.open() != IndexCompatibility.Compatible) reject(IngestError.INDEX_UNAVAILABLE)
        info.sizeBytes?.let { size ->
            if (size > ImportLimits.MAX_FILE_BYTES) reject(IngestError.TOO_LARGE)
            if (freeBytes() < size + ImportLimits.SPACE_MARGIN_BYTES) reject(IngestError.NO_SPACE)
        }
        if (format == DocumentFormat.PDF && !pdf.isAvailable) reject(IngestError.PDF_UNAVAILABLE)

        val temp = files.newTempFile()
        try {
            val (sha256, size) = copy(uri, temp, info.sizeBytes, onProgress)
            if (size == 0L) reject(IngestError.NO_TEXT)
            val pages = validate(temp, format)
            // Before renaming: the copy of an existing document has the same name (contracts I2).
            if (index.documentIdByHash(sha256) != null) reject(IngestError.DUPLICATE)
            val final = files.promote(temp, sha256, format)
            return try {
                ImportResult.Imported(
                    index.addDocument(
                        NewDocument(
                            displayName = info.displayName,
                            contentHash = sha256,
                            mimeType = format.mimeType,
                            uri = uri,
                            sizeBytes = size,
                            pageCount = pages,
                            localPath = final.absolutePath,
                        ),
                    ),
                )
            } catch (e: DuplicateDocumentException) {
                // Only a race could get here (imports are sequential); the file is the existing document's.
                ImportResult.Rejected(IngestError.DUPLICATE)
            }
        } finally {
            temp.delete()
        }
    }

    /** Copies with SHA-256 and a byte count; returns the hex digest and the size. */
    private suspend fun copy(uri: String, temp: File, declaredSize: Long?, onProgress: (Long, Long?) -> Unit): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = try {
            source.open(uri)
        } catch (e: SecurityException) {
            reject(IngestError.ACCESS_LOST)
        } catch (e: FileNotFoundException) {
            reject(IngestError.ACCESS_LOST)
        }
        var copied = 0L
        input.use { stream ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = readBlock(stream, buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > ImportLimits.MAX_FILE_BYTES) reject(IngestError.TOO_LARGE)
                    digest.update(buffer, 0, read)
                    writeBlock(output, buffer, read)
                    onProgress(copied, declaredSize)
                }
            }
        }
        onProgress(copied, declaredSize ?: copied)
        return digest.digest().joinToString("") { "%02x".format(it) } to copied
    }

    private fun readBlock(stream: InputStream, buffer: ByteArray): Int = try {
        stream.read(buffer)
    } catch (e: SecurityException) {
        reject(IngestError.ACCESS_LOST)
    } catch (e: FileNotFoundException) {
        reject(IngestError.ACCESS_LOST)
    } catch (e: IOException) {
        reject(IngestError.READ_FAILED)
    }

    private fun writeBlock(output: OutputStream, buffer: ByteArray, length: Int) {
        try {
            output.write(buffer, 0, length)
        } catch (e: IOException) {
            // A failed write of the private copy is almost always a full disk (ENOSPC).
            reject(IngestError.NO_SPACE)
        }
    }

    /** Content checks (research R4, R5); returns the page count of a PDF. */
    private fun validate(file: File, format: DocumentFormat): Int? = when (format) {
        DocumentFormat.PDF -> {
            if (!file.startsWithPdfHeader()) reject(IngestError.DAMAGED)
            val pages = try {
                pdf.open(file).use { it.pageCount }
            } catch (e: PdfEncryptedException) {
                reject(IngestError.ENCRYPTED)
            } catch (e: PdfDamagedException) {
                reject(IngestError.DAMAGED)
            } catch (e: PdfUnavailableException) {
                reject(IngestError.PDF_UNAVAILABLE)
            }
            when {
                pages == 0 -> reject(IngestError.DAMAGED)
                pages > ImportLimits.MAX_PDF_PAGES -> reject(IngestError.TOO_LARGE)
                else -> pages
            }
        }
        DocumentFormat.TXT, DocumentFormat.MARKDOWN -> {
            if (file.hasNulByte()) reject(IngestError.UNSUPPORTED_FORMAT)
            null
        }
    }

    private fun File.startsWithPdfHeader(): Boolean = String(head(PDF_HEADER_WINDOW), Charsets.ISO_8859_1).contains("%PDF-")

    private fun File.hasNulByte(): Boolean = head(TEXT_SNIFF_BYTES).any { it == 0.toByte() }

    /** First [count] bytes or fewer (InputStream.readNBytes(int) needs API 33). */
    private fun File.head(count: Int): ByteArray = inputStream().use { stream ->
        val buffer = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = stream.read(buffer, filled, count - filled)
            if (read < 0) break
            filled += read
        }
        buffer.copyOf(filled)
    }

    private class Rejection(val error: IngestError) : Exception(error.code)

    private fun reject(error: IngestError): Nothing = throw Rejection(error)

    private companion object {
        const val TAG = "MicroRAG"
        const val BUFFER_BYTES = 64 * 1024
        const val PDF_HEADER_WINDOW = 1024
        const val TEXT_SNIFF_BYTES = 8 * 1024
        const val MAX_EXTENSION_CHARS = 10
        val EXTENSIONS = mapOf(
            "pdf" to DocumentFormat.PDF,
            "txt" to DocumentFormat.TXT,
            "md" to DocumentFormat.MARKDOWN,
            "markdown" to DocumentFormat.MARKDOWN,
        )
        val MIME_TYPES = mapOf(
            "application/pdf" to DocumentFormat.PDF,
            "text/plain" to DocumentFormat.TXT,
            "text/markdown" to DocumentFormat.MARKDOWN,
            "text/x-markdown" to DocumentFormat.MARKDOWN,
        )
    }
}

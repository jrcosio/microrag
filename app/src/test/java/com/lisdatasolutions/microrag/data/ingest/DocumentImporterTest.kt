package com.lisdatasolutions.microrag.data.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportLimits
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.testing.FakeDocumentSource
import com.lisdatasolutions.microrag.testing.FakeFile
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** F06 US1 (T019): a picked file becomes a pending document with its private copy (research R5). */
class DocumentImporterTest {

    private val dir: File = Files.createTempDirectory("documents").toFile()
    private val source = FakeDocumentSource()
    private val pdf = FakePdfOpener()
    private val index = FakeRagIndex()
    private val logger = RecordingLogger()
    private var free = 10L * 1024 * 1024 * 1024
    private val importer = DocumentImporter(source, DocumentFileStore(dir), pdf, index, { free }, logger)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private suspend fun import(uri: String, progress: MutableList<Pair<Long, Long?>>? = null): ImportResult =
        importer.import(uri, source.describe(uri)) { copied, total -> progress?.add(copied to total) }

    @Test
    fun pdfTxtAndMarkdownAreCopiedHashedAndRegistered() = runTest {
        val pdfBytes = FakePdfOpener.pdf("Página uno", "Página dos")
        val txtBytes = "Notas de la reunión.\n".toByteArray()
        val mdBytes = "# Título\ncuerpo\n".toByteArray()
        source.add("content://docs/1", "Manual técnico.pdf", pdfBytes, "application/pdf")
        source.add("content://docs/2", "notas.txt", txtBytes, "text/plain")
        source.add("content://docs/3", "Notas de reunión.md", mdBytes, "text/markdown")

        val ids = listOf("content://docs/1", "content://docs/2", "content://docs/3").map { import(it) }
            .map { (it as ImportResult.Imported).documentId }

        val (pdfDoc, txtDoc, mdDoc) = ids.map { index.docs.getValue(it) }
        assertThat(pdfDoc.displayName).isEqualTo("Manual técnico.pdf")
        assertThat(pdfDoc.contentHash).isEqualTo(sha256(pdfBytes))
        assertThat(pdfDoc.mimeType).isEqualTo("application/pdf")
        assertThat(pdfDoc.sizeBytes).isEqualTo(pdfBytes.size.toLong())
        assertThat(pdfDoc.pageCount).isEqualTo(2)
        assertThat(pdfDoc.uri).isEqualTo("content://docs/1")
        assertThat(pdfDoc.status).isEqualTo(DocumentStatus.PENDING)
        assertThat(pdfDoc.localPath).isEqualTo(File(dir, "${sha256(pdfBytes)}.pdf").absolutePath)
        assertThat(File(pdfDoc.localPath!!).readBytes()).isEqualTo(pdfBytes)
        assertThat(txtDoc.mimeType).isEqualTo("text/plain")
        assertThat(txtDoc.pageCount).isNull()
        assertThat(File(txtDoc.localPath!!).name).isEqualTo("${sha256(txtBytes)}.txt")
        assertThat(mdDoc.mimeType).isEqualTo("text/markdown")
        assertThat(File(mdDoc.localPath!!).name).isEqualTo("${sha256(mdBytes)}.md")
        assertThat(dir.list()!!.filter { it.endsWith(".tmp") }).isEmpty()
    }

    @Test
    fun theCopyReportsItsProgress() = runTest {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        source.add("content://docs/big", "grande.txt", bytes, "text/plain")
        val progress = mutableListOf<Pair<Long, Long?>>()

        import("content://docs/big", progress)

        assertThat(progress.first().first).isAtMost(progress.last().first)
        assertThat(progress.last()).isEqualTo(300_000L to 300_000L)
    }

    @Test
    fun theFormatComesFromTheExtensionAndThenTheMimeType() {
        assertThat(importer.detectFormat("Informe.PDF", null)).isEqualTo(DocumentFormat.PDF)
        assertThat(importer.detectFormat("readme.markdown", "application/octet-stream")).isEqualTo(DocumentFormat.MARKDOWN)
        assertThat(importer.detectFormat("notas.md", null)).isEqualTo(DocumentFormat.MARKDOWN)
        assertThat(importer.detectFormat("sin extensión", "text/x-markdown")).isEqualTo(DocumentFormat.MARKDOWN)
        assertThat(importer.detectFormat("sin extensión", "text/plain")).isEqualTo(DocumentFormat.TXT)
        assertThat(importer.detectFormat("sin extensión", "application/pdf")).isEqualTo(DocumentFormat.PDF)
        assertThat(importer.detectFormat("hoja.xlsx", "application/vnd.ms-excel")).isNull()
        // F19 research R10: JPG and PNG are known formats; whether they are accepted depends on the mode.
        assertThat(importer.detectFormat("foto.jpg", "text/plain")).isEqualTo(DocumentFormat.JPEG)
        assertThat(importer.detectFormat("Foto.JPEG", null)).isEqualTo(DocumentFormat.JPEG)
        assertThat(importer.detectFormat("captura.png", null)).isEqualTo(DocumentFormat.PNG)
        assertThat(importer.detectFormat("sin extensión", "image/png")).isEqualTo(DocumentFormat.PNG)
        assertThat(importer.detectFormat("animación.gif", "image/gif")).isNull()
    }

    // F19 T028 (contract I1, analyze M3) and T034 (research R10).

    private var modeOn = false
    private val multimodalImporter = DocumentImporter(source, DocumentFileStore(dir), pdf, index, { free }, logger, multimodalOn = { modeOn })

    private suspend fun importIn(uri: String, mode: IndexMode): ImportResult = multimodalImporter.import(uri, source.describe(uri), indexMode = mode)

    @Test
    fun withTheModeOn_aPdfKeepsTheSelectionsMode_andTextFilesStayText() = runTest {
        modeOn = true
        source.add("content://1", "plano.pdf", FakePdfOpener.pdf("uno", "dos"), "application/pdf")
        source.add("content://2", "manual.pdf", FakePdfOpener.pdf("tres"), "application/pdf")
        source.add("content://3", "notas.txt", "notas".toByteArray(), "text/plain")

        val visual = (importIn("content://1", IndexMode.VISUAL) as ImportResult.Imported).documentId
        val both = (importIn("content://2", IndexMode.BOTH) as ImportResult.Imported).documentId
        val notes = (importIn("content://3", IndexMode.VISUAL) as ImportResult.Imported).documentId

        assertThat(index.docs.getValue(visual).indexMode).isEqualTo(IndexMode.VISUAL)
        assertThat(index.docs.getValue(visual).pageCount).isEqualTo(2)
        assertThat(index.docs.getValue(both).indexMode).isEqualTo(IndexMode.BOTH)
        assertThat(index.docs.getValue(notes).indexMode).isEqualTo(IndexMode.TEXT)
    }

    @Test
    fun withTheModeOff_everythingIsText() = runTest {
        source.add("content://1", "plano.pdf", FakePdfOpener.pdf("uno"), "application/pdf")

        val id = (importIn("content://1", IndexMode.VISUAL) as ImportResult.Imported).documentId

        assertThat(index.docs.getValue(id).indexMode).isEqualTo(IndexMode.TEXT)
        // The importer of F06, without the mode, is the same.
        source.add("content://2", "otro.pdf", FakePdfOpener.pdf("dos"), "application/pdf")
        assertThat(index.docs.getValue((import("content://2") as ImportResult.Imported).documentId).indexMode).isEqualTo(IndexMode.TEXT)
    }

    @Test
    fun withTheModeOn_imagesAreOneVisualPage() = runTest {
        modeOn = true
        source.add("content://1", "foto.jpg", byteArrayOf(1, 2, 3), "image/jpeg")
        source.add("content://2", "captura.png", byteArrayOf(4, 5, 6), "image/png")
        source.add("content://3", "copia.jpeg", byteArrayOf(1, 2, 3), "image/jpeg")

        val jpg = (importIn("content://1", IndexMode.TEXT) as ImportResult.Imported).documentId
        val png = (importIn("content://2", IndexMode.BOTH) as ImportResult.Imported).documentId

        listOf(jpg to "image/jpeg", png to "image/png").forEach { (id, mime) ->
            val document = index.docs.getValue(id)
            assertThat(document.indexMode).isEqualTo(IndexMode.VISUAL)
            assertThat(document.pageCount).isEqualTo(1)
            assertThat(document.mimeType).isEqualTo(mime)
            assertThat(File(document.localPath!!).isFile).isTrue()
        }
        // The same bytes are the same document (F06 rule).
        assertThat(importIn("content://3", IndexMode.VISUAL)).isEqualTo(ImportResult.Rejected(IngestError.DUPLICATE))
    }

    @Test
    fun withTheModeOff_imagesAreRefused_andNothingIsCopied() = runTest {
        source.add("content://1", "foto.jpg", byteArrayOf(1, 2, 3), "image/jpeg")

        assertThat(importIn("content://1", IndexMode.VISUAL)).isEqualTo(ImportResult.Rejected(IngestError.IMAGE_NEEDS_MULTIMODAL))
        assertThat(index.docs).isEmpty()
        assertThat(dir.walk().filter { it.isFile }.toList()).isEmpty()
    }

    @Test
    fun anImageOverTheSizeLimit_isTooLarge() = runTest {
        modeOn = true
        source.add("content://1", "enorme.png", byteArrayOf(1), "image/png", size = ImportLimits.MAX_FILE_BYTES + 1)

        assertThat(importIn("content://1", IndexMode.VISUAL)).isEqualTo(ImportResult.Rejected(IngestError.TOO_LARGE))
    }

    @Test
    fun logsCarryNoNamesPathsOrText() = runTest {
        source.add("content://docs/secret", "Contrato confidencial.txt", "texto secreto".toByteArray(), "text/plain")

        import("content://docs/secret")

        val log = logger.lines.joinToString("\n")
        assertThat(log).doesNotContain("Contrato")
        assertThat(log).doesNotContain("secreto")
        assertThat(log).doesNotContain(dir.path)
        assertThat(log).doesNotContain("content://")
    }

    // F06 US2 (T031): one error per IngestError, never a temporary file left behind (research R5).

    private suspend fun rejected(uri: String): IngestError? = (import(uri) as? ImportResult.Rejected)?.error

    private fun assertNoTempFiles() = assertThat(dir.list().orEmpty().filter { it.endsWith(".tmp") }).isEmpty()

    @Test
    fun unsupportedFormats() = runTest {
        source.add("content://xlsx", "hoja.xlsx", byteArrayOf(1, 2, 3), "application/vnd.ms-excel")
        source.add("content://bin", "notas.txt", byteArrayOf(72, 0, 105), "text/plain")

        assertThat(rejected("content://xlsx")).isEqualTo(IngestError.UNSUPPORTED_FORMAT)
        assertThat(rejected("content://bin")).isEqualTo(IngestError.UNSUPPORTED_FORMAT)
        assertThat(source.opened).containsExactly("content://bin")
        assertNoTempFiles()
    }

    @Test
    fun damagedAndEncryptedPdfs() = runTest {
        source.add("content://fake", "falso.pdf", "esto no es un PDF".toByteArray(), "application/pdf")
        source.add("content://empty", "vacío.pdf", FakePdfOpener.pdf(), "application/pdf")
        source.add("content://broken", "roto.pdf", FakePdfOpener.pdf("x", marker = FakePdfOpener.DAMAGED), "application/pdf")
        source.add("content://locked", "cifrado.pdf", FakePdfOpener.pdf("x", marker = FakePdfOpener.ENCRYPTED), "application/pdf")

        assertThat(rejected("content://fake")).isEqualTo(IngestError.DAMAGED)
        assertThat(rejected("content://empty")).isEqualTo(IngestError.DAMAGED)
        assertThat(rejected("content://broken")).isEqualTo(IngestError.DAMAGED)
        assertThat(rejected("content://locked")).isEqualTo(IngestError.ENCRYPTED)
        assertThat(index.docs).isEmpty()
        assertNoTempFiles()
    }

    @Test
    fun sizeAndPageLimits() = runTest {
        source.files["content://declared-big"] = FakeFile(SourceInfo("grande.txt", ImportLimits.MAX_FILE_BYTES + 1, "text/plain"))
        source.add("content://exact", "exacto.txt", ByteArray(ImportLimits.MAX_FILE_BYTES.toInt()) { 'a'.code.toByte() }, "text/plain")
        source.add("content://unknown-big", "sin tamaño.txt", ByteArray(ImportLimits.MAX_FILE_BYTES.toInt() + 1) { 'b'.code.toByte() }, "text/plain", size = null)
        source.add("content://pages-over", "largo.pdf", FakePdfOpener.pdf("x", marker = "pages=2001"), "application/pdf")
        source.add("content://pages-limit", "límite.pdf", FakePdfOpener.pdf("x", marker = "pages=2000"), "application/pdf")

        assertThat(rejected("content://declared-big")).isEqualTo(IngestError.TOO_LARGE)
        assertThat(source.opened).doesNotContain("content://declared-big")
        assertThat(import("content://exact")).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(rejected("content://unknown-big")).isEqualTo(IngestError.TOO_LARGE)
        assertThat(rejected("content://pages-over")).isEqualTo(IngestError.TOO_LARGE)
        assertThat(import("content://pages-limit")).isInstanceOf(ImportResult.Imported::class.java)
        assertNoTempFiles()
    }

    @Test
    fun notEnoughSpaceBeforeCopying() = runTest {
        source.add("content://1", "notas.txt", ByteArray(1_000) { 'a'.code.toByte() }, "text/plain")
        free = 1_000 + ImportLimits.SPACE_MARGIN_BYTES - 1

        assertThat(rejected("content://1")).isEqualTo(IngestError.NO_SPACE)
        assertThat(source.opened).isEmpty()
    }

    @Test
    fun aFailingWriteIsReportedAsNoSpace() = runTest {
        source.add("content://1", "notas.txt", "texto".toByteArray(), "text/plain")
        dir.setWritable(false)
        try {
            // A folder that refuses the copy is a storage problem, as a full disk is.
            assertThat(rejected("content://1")).isEqualTo(IngestError.NO_SPACE)
        } finally {
            dir.setWritable(true)
        }
        assertNoTempFiles()
    }

    @Test
    fun accessLostAndReadErrors() = runTest {
        source.files["content://revoked"] = FakeFile(SourceInfo("a.txt", 10, "text/plain"), ByteArray(10), readError = null)
        source.files["content://denied"] = FakeFile(SourceInfo("b.txt", 10, "text/plain"), ByteArray(10) { 1 }, readError = java.io.FileNotFoundException("gone"))
        source.files["content://broken"] = FakeFile(SourceInfo("c.txt", 10, "text/plain"), ByteArray(10) { 1 }, readError = java.io.IOException("I/O"), readErrorAfter = 3)
        source.files.remove("content://revoked")

        assertThat(importer.import("content://revoked", SourceInfo("a.txt", 10, "text/plain"))).isEqualTo(ImportResult.Rejected(IngestError.ACCESS_LOST))
        assertThat(rejected("content://denied")).isEqualTo(IngestError.ACCESS_LOST)
        assertThat(rejected("content://broken")).isEqualTo(IngestError.READ_FAILED)
        assertNoTempFiles()
    }

    @Test
    fun duplicatesKeepTheExistingCopyUntouched() = runTest {
        val bytes = "mismo contenido".toByteArray()
        source.add("content://first", "original.txt", bytes, "text/plain")
        source.add("content://again", "copia.txt", bytes, "text/plain")
        val first = (import("content://first") as ImportResult.Imported).documentId
        val copy = File(index.docs.getValue(first).localPath!!)
        copy.setLastModified(1_000_000L)

        assertThat(rejected("content://again")).isEqualTo(IngestError.DUPLICATE)

        assertThat(index.docs).hasSize(1)
        assertThat(copy.readBytes()).isEqualTo(bytes)
        assertThat(copy.lastModified()).isEqualTo(1_000_000L)
        assertNoTempFiles()
    }

    @Test
    fun pdfWithoutTheSystemReader() = runTest {
        pdf.isAvailableValue = false
        source.add("content://1", "manual.pdf", FakePdfOpener.pdf("x"), "application/pdf")
        source.add("content://2", "notas.md", "# Título".toByteArray(), "text/markdown")

        assertThat(rejected("content://1")).isEqualTo(IngestError.PDF_UNAVAILABLE)
        assertThat(source.opened).doesNotContain("content://1")
        assertThat(import("content://2")).isInstanceOf(ImportResult.Imported::class.java)
    }

    @Test
    fun anEmptyFileHasNoText() = runTest {
        source.add("content://1", "vacío.txt", ByteArray(0), "text/plain")

        assertThat(rejected("content://1")).isEqualTo(IngestError.NO_TEXT)
        assertNoTempFiles()
    }

    @Test
    fun anUnavailableIndexRegistersNothing() = runTest {
        index.setCompatibility(IndexCompatibility.Incompatible("embedding_dims", "256", "768"))
        source.add("content://1", "notas.txt", "texto".toByteArray(), "text/plain")

        assertThat(rejected("content://1")).isEqualTo(IngestError.INDEX_UNAVAILABLE)
        assertThat(source.opened).isEmpty()
        assertThat(dir.list().orEmpty()).isEmpty()
    }

    @Test
    fun errorLogsCarryOnlyTheCode() = runTest {
        source.add("content://secret", "Nóminas 2024.pdf", "no es un pdf".toByteArray(), "application/pdf")

        rejected("content://secret")

        assertThat(logger.lines.joinToString()).doesNotContain("Nóminas")
        assertThat(logger.lines.any { "damaged" in it }).isTrue()
    }

    /** F17 T026 (F6): the document is registered in its destination; a duplicate keeps the folder it had. */
    @Test
    fun theDocumentIsRegisteredInItsFolder_andADuplicateKeepsItsOwn() = runTest {
        val legal = checkNotNull(index.createFolder("Legal", "legal"))
        val motors = checkNotNull(index.createFolder("Motores", "motores"))
        source.add("content://1", "a.txt", "uno".toByteArray(), "text/plain")
        source.add("content://2", "copia.txt", "uno".toByteArray(), "text/plain")

        val id = (importer.import("content://1", source.describe("content://1"), legal) as ImportResult.Imported).documentId
        val again = importer.import("content://2", source.describe("content://2"), motors)

        assertThat(index.docs.getValue(id).folderId).isEqualTo(legal)
        assertThat(again).isEqualTo(ImportResult.Rejected(IngestError.DUPLICATE))
        assertThat(index.docs.getValue(id).folderId).isEqualTo(legal)
    }
}

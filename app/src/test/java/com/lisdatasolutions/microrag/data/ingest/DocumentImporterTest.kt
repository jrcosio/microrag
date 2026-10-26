package com.lisdatasolutions.microrag.data.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.testing.FakeDocumentSource
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
        assertThat(importer.detectFormat("foto.jpg", "text/plain")).isNull()
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
}

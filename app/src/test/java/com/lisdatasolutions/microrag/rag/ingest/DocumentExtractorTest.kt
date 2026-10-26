package com.lisdatasolutions.microrag.rag.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import java.io.File
import java.nio.file.Files
import org.junit.Test

/** F06 T044: blocks and summary per format, with fake PDF pages. */
class DocumentExtractorTest {

    private class Pages(private val texts: List<String>) : PdfOpener {
        override val isAvailable = true
        override fun open(file: File) = object : PdfPages {
            override val pageCount = texts.size
            override fun pageText(index: Int) = texts[index]
            override fun close() = Unit
        }
    }

    private fun file(text: String, bytes: ByteArray = text.toByteArray()) =
        Files.createTempFile("doc", ".bin").toFile().apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun pdfPagesBecomeBlocksAndEmptyOnesAreCounted() {
        val extractor = DocumentExtractor(Pages(listOf("Uno.\r\nsigue", "", "12", "Tres.")))
        val blocks = mutableListOf<RawBlock>()
        val pages = mutableListOf<Pair<Int, Int>>()

        val summary = extractor.extract(file("%PDF-"), DocumentFormat.PDF, { done, total -> pages += done to total }, blocks::add)

        assertThat(blocks).containsExactly(RawBlock(emptyList(), "Uno. sigue", 1), RawBlock(emptyList(), "Tres.", 4)).inOrder()
        assertThat(summary).isEqualTo(ExtractionSummary(blocks = 2, pages = 4, skippedPages = 2))
        assertThat(pages).containsExactly(1 to 4, 2 to 4, 3 to 4, 4 to 4).inOrder()
    }

    @Test
    fun aPdfWithoutTextHasNoBlocks() {
        val summary = DocumentExtractor(Pages(listOf("", " "))).extract(file("%PDF-"), DocumentFormat.PDF) { }

        assertThat(summary).isEqualTo(ExtractionSummary(blocks = 0, pages = 2, skippedPages = 2))
    }

    @Test
    fun markdownAndTextInBothEncodings() {
        val markdown = mutableListOf<RawBlock>()
        DocumentExtractor(Pages(emptyList())).extract(file("# Título\r\ncuerpo ñ\n\n## Sub\nmás"), DocumentFormat.MARKDOWN, onBlock = markdown::add)
        assertThat(markdown).containsExactly(
            RawBlock(listOf("Título"), "cuerpo ñ", null),
            RawBlock(listOf("Título", "Sub"), "más", null),
        ).inOrder()

        val latin = mutableListOf<RawBlock>()
        val bytes = byteArrayOf('A'.code.toByte(), 0xF1.toByte(), 'o'.code.toByte(), '\r'.code.toByte(), 'x'.code.toByte())
        val summary = DocumentExtractor(Pages(emptyList())).extract(file("", bytes), DocumentFormat.TXT, onBlock = latin::add)
        assertThat(latin).containsExactly(RawBlock(emptyList(), "Año\nx", null))
        assertThat(summary).isEqualTo(ExtractionSummary(blocks = 1, pages = null, skippedPages = null))
    }

    @Test
    fun onlySpacesIsNoText() {
        val summary = DocumentExtractor(Pages(emptyList())).extract(file(" \n\u00A0\n\t"), DocumentFormat.TXT) { }

        assertThat(summary.blocks).isEqualTo(0)
    }
}

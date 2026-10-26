package com.lisdatasolutions.microrag.rag.ingest

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.StringReader
import java.nio.file.Files
import org.junit.Test

/** F06 T039: UTF-8 with an optional BOM, a Windows-1252 fallback and Python's lines. */
class TextDecodingTest {

    private fun file(bytes: ByteArray): File = Files.createTempFile("text", ".txt").toFile().apply { writeBytes(bytes); deleteOnExit() }

    private fun linesOf(bytes: ByteArray): List<String> = TextDecoding.reader(file(bytes)).use { TextDecoding.lines(it).toList() }

    @Test
    fun utf8WithAndWithoutBom() {
        assertThat(TextDecoding.decode("Año €".toByteArray())).isEqualTo("Año €")
        assertThat(TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Año".toByteArray())).isEqualTo("Año")
        assertThat(linesOf(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "uno\ndos".toByteArray())).containsExactly("uno", "dos").inOrder()
    }

    @Test
    fun invalidUtf8FallsBackToWindows1252() {
        val bytes = byteArrayOf('A'.code.toByte(), 0xF1.toByte(), 'o'.code.toByte(), ' '.code.toByte(), 0x80.toByte(), 0x81.toByte(), 0x9D.toByte())
        assertThat(TextDecoding.decode(bytes)).isEqualTo("Año €��")
        assertThat(linesOf(bytes)).containsExactly("Año €��")
    }

    @Test
    fun strictUtf8RejectsOverlongAndSurrogates() {
        assertThat(TextDecoding.isUtf8(byteArrayOf(0xC0.toByte(), 0xAF.toByte()).inputStream())).isFalse()
        assertThat(TextDecoding.isUtf8(byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()).inputStream())).isFalse()
        assertThat(TextDecoding.isUtf8(byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()).inputStream())).isFalse()
        assertThat(TextDecoding.isUtf8(byteArrayOf(0xE2.toByte(), 0x82.toByte()).inputStream())).isFalse()
        assertThat(TextDecoding.isUtf8("𝐀 ñ €".toByteArray().inputStream())).isTrue()
    }

    @Test
    fun linesTranslateNewlinesThenSplitAsPython() {
        assertThat(TextDecoding.lines(StringReader("a\r\nb\rc\nd\u0085e")).toList()).containsExactly("a", "b", "c", "d", "e").inOrder()
        assertThat(TextDecoding.lines(StringReader("a\r\r\nb\n")).toList()).containsExactly("a", "", "b").inOrder()
        assertThat(TextDecoding.lines(StringReader("")).toList()).isEmpty()
        assertThat(TextDecoding.lines(StringReader("\n")).toList()).containsExactly("")
    }

    @Test
    fun streamingAcrossBufferBoundariesMatchesDecodingAtOnce() {
        val text = buildString { repeat(40_000) { append("línea ñ € 𝐀 $it\r\n") } }
        val bytes = text.toByteArray()
        assertThat(linesOf(bytes)).isEqualTo(PyText.splitLines(TextDecoding.decode(bytes)))
    }
}

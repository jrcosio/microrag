package com.lisdatasolutions.microrag.rag.ingest

import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * How TXT and Markdown are read, as `decode_text` in offline/eval/extract.py (F06 research R8):
 * UTF-8 with an optional BOM, or Windows-1252 with replacement when the bytes are not valid UTF-8;
 * CR LF and CR become LF. Files are read twice in streaming (validate, then decode), so memory does
 * not depend on their size.
 */
object TextDecoding {

    private const val BUFFER_BYTES = 64 * 1024

    /**
     * Windows-1252 bytes 0x80–0x9F as Python's cp1252 decodes them, with the five undefined bytes as
     * U+FFFD (errors="replace"). Our own table: Android's ICU converter maps those bytes differently.
     * Every other byte is its own code point (ISO-8859-1).
     */
    private val CP1252_HIGH = charArrayOf(
        '\u20AC', '\uFFFD', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021',
        '\u02C6', '\u2030', '\u0160', '\u2039', '\u0152', '\uFFFD', '\u017D', '\uFFFD',
        '\uFFFD', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014',
        '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', '\uFFFD', '\u017E', '\u0178',
    )

    private fun cp1252(byte: Int): Char = if (byte in 0x80..0x9F) CP1252_HIGH[byte - 0x80] else byte.toChar()
    private const val BOM = '﻿'

    /** True when the whole stream is well-formed UTF-8, as Python's strict decoder requires (RFC 3629). */
    fun isUtf8(input: InputStream): Boolean {
        val validator = Utf8Validator()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            for (i in 0 until read) if (!validator.feed(buffer[i].toInt() and 0xFF)) return false
        }
        return validator.isComplete
    }

    /** Byte-by-byte check of well-formed UTF-8: no overlong forms, surrogates or code points above U+10FFFF. */
    private class Utf8Validator {
        private var needed = 0
        private var lower = 0x80
        private var upper = 0xBF

        val isComplete: Boolean get() = needed == 0

        fun feed(byte: Int): Boolean {
            if (needed == 0) {
                when (byte) {
                    in 0x00..0x7F -> Unit
                    in 0xC2..0xDF -> needed = 1
                    0xE0 -> { needed = 2; lower = 0xA0 }
                    in 0xE1..0xEC, 0xEE, 0xEF -> needed = 2
                    0xED -> { needed = 2; upper = 0x9F }
                    0xF0 -> { needed = 3; lower = 0x90 }
                    in 0xF1..0xF3 -> needed = 3
                    0xF4 -> { needed = 3; upper = 0x8F }
                    else -> return false
                }
                return true
            }
            if (byte !in lower..upper) return false
            lower = 0x80
            upper = 0xBF
            needed--
            return true
        }
    }

    /** A reader of [file] in its decoding, without a leading UTF-8 BOM; newlines are not translated yet. */
    fun reader(file: File): Reader {
        val utf8 = file.inputStream().use(::isUtf8)
        if (!utf8) return Cp1252Reader(file.inputStream().buffered(BUFFER_BYTES))
        val reader = InputStreamReader(file.inputStream(), Charsets.UTF_8).buffered(BUFFER_BYTES)
        reader.mark(1)
        if (reader.read() != BOM.code) reader.reset()
        return reader
    }

    /** The whole text of [bytes] with newlines translated: `decode_text` for small inputs and tests. */
    fun decode(bytes: ByteArray): String {
        val text = if (bytes.inputStream().use(::isUtf8)) {
            String(bytes, Charsets.UTF_8).removePrefix(BOM.toString())
        } else {
            String(CharArray(bytes.size) { cp1252(bytes[it].toInt() and 0xFF) })
        }
        return text.replace("\r\n", "\n").replace('\r', '\n')
    }

    private class Cp1252Reader(private val input: InputStream) : Reader() {
        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            var count = 0
            while (count < length) {
                val byte = input.read()
                if (byte < 0) break
                buffer[offset + count++] = cp1252(byte)
            }
            return if (count == 0 && length > 0) -1 else count
        }

        override fun close() = input.close()
    }

    /**
     * Lines of [reader] as Python reads them: newlines translated (CR LF and CR become LF), then
     * `str.splitlines()`. Lazy: one line in memory at a time.
     */
    fun lines(reader: Reader): Sequence<String> = sequence {
        val line = StringBuilder()
        var pendingCr = false
        var any = false
        while (true) {
            val code = reader.read()
            if (code < 0) break
            val char = code.toChar()
            if (pendingCr) {
                pendingCr = false
                if (char == '\n') continue
            }
            if (PyText.isLineBreak(char)) {
                yield(line.toString())
                line.setLength(0)
                any = false
                if (char == '\r') pendingCr = true
            } else {
                line.append(char)
                any = true
            }
        }
        if (any) yield(line.toString())
    }
}

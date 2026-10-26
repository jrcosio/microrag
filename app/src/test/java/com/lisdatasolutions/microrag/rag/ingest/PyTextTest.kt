package com.lisdatasolutions.microrag.rag.ingest

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F06 T038: Python string semantics, checked against values Python gives. */
class PyTextTest {

    @Test
    fun spacesAreThoseOfStrIsspace() {
        listOf(' ', '\t', '\n', '\u000B', '\u000C', '\r', '\u001C', '\u001F', '\u0085', ' ', ' ', ' ', '　')
            .forEach { assertThat(PyText.isSpace(it.code)).isTrue() }
        listOf('a', '​', '﻿', '_').forEach { assertThat(PyText.isSpace(it.code)).isFalse() }
    }

    @Test
    fun splitLinesFollowsStrSplitlines() {
        assertThat(PyText.splitLines("")).isEmpty()
        assertThat(PyText.splitLines("\n")).containsExactly("")
        assertThat(PyText.splitLines("a\nb\n")).containsExactly("a", "b").inOrder()
        assertThat(PyText.splitLines("a\r\nb\rc\u000Bd\u000Ce\u001Cf\u0085g h i")).containsExactly("a", "b", "c", "d", "e", "f", "g", "h", "i").inOrder()
        assertThat(PyText.splitLines("a\n\n\nb")).containsExactly("a", "", "", "b").inOrder()
        assertThat(PyText.splitLines("a\r\r\nb")).containsExactly("a", "", "b").inOrder()
    }

    @Test
    fun stripAndCollapseUsePythonSpaces() {
        assertThat(PyText.strip("  hola 　")).isEqualTo("hola")
        assertThat(PyText.rstrip("  hola\t\u001F")).isEqualTo("  hola")
        assertThat(PyText.collapseSpaces("  a   b c  ")).isEqualTo("a b c")
        assertThat(PyText.collapseSpaces(" \t ")).isEmpty()
    }

    @Test
    fun digitsIncludeSuperscriptsButDecimalsDoNot() {
        assertThat(PyText.isDigitString("12")).isTrue()
        assertThat(PyText.isDigitString("²")).isTrue()
        assertThat(PyText.isDigitString("①")).isTrue()
        assertThat(PyText.isDigitString("𝟏𝟐")).isTrue()
        assertThat(PyText.isDigitString("12a")).isFalse()
        assertThat(PyText.isDigitString("")).isFalse()
        assertThat(PyText.isDecimal('²'.code)).isFalse()
        assertThat(PyText.isDecimal("𝟏".codePointAt(0))).isTrue()
        assertThat(PyText.isDecimal('٣'.code)).isTrue()
    }

    @Test
    fun caseByCodePoint() {
        assertThat(PyText.isUpper("𝐀".codePointAt(0))).isTrue()
        assertThat(PyText.isUpper('É'.code)).isTrue()
        assertThat(PyText.isUpper('ǅ'.code)).isFalse()
        assertThat(PyText.isLower('ñ'.code)).isTrue()
        assertThat(PyText.isAlpha('ª'.code)).isTrue()
        assertThat(PyText.isAlpha('1'.code)).isFalse()
    }
}

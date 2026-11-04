package com.lisdatasolutions.microrag.domain.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F17 T012 (FR-003, D-072): the rules of a folder name. */
class FolderNamesTest {

    @Test
    fun aValidNameIsTrimmedAndFolded() {
        assertThat(FolderNames.check("  Légal Ñandú  ")).isEqualTo(FolderNameCheck.Valid("Légal Ñandú", "legal nandu"))
        assertThat(FolderNames.check("LEGAL")).isEqualTo(FolderNameCheck.Valid("LEGAL", "legal"))
    }

    @Test
    fun blankIsEmpty() {
        assertThat(FolderNames.check("")).isEqualTo(FolderNameCheck.Empty)
        assertThat(FolderNames.check("   \t ")).isEqualTo(FolderNameCheck.Empty)
    }

    @Test
    fun fortyCharactersFit_andFortyOneDoNot() {
        assertThat(FolderNames.check("a".repeat(40))).isInstanceOf(FolderNameCheck.Valid::class.java)
        assertThat(FolderNames.check(" " + "a".repeat(40) + " ")).isInstanceOf(FolderNameCheck.Valid::class.java)
        assertThat(FolderNames.check("a".repeat(41))).isEqualTo(FolderNameCheck.TooLong)
        // Characters, not UTF-16 units: forty emoji still fit.
        assertThat(FolderNames.check("📁".repeat(40))).isInstanceOf(FolderNameCheck.Valid::class.java)
    }

    @Test
    fun generalIsReservedInAnyForm() {
        for (name in listOf("General", "general", " GENERAL ", "Généràl")) {
            assertThat(FolderNames.check(name)).isEqualTo(FolderNameCheck.Reserved)
        }
        assertThat(FolderNames.check("General 2")).isInstanceOf(FolderNameCheck.Valid::class.java)
    }
}

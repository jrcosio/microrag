package com.lisdatasolutions.microrag.rag.answer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F11 T020 (research R3): `offline/eval/judge.py` citations, normalization and refusal, with `test_judge.py`. */
class AnswerReadingTest {

    @Test
    fun citationForms() {
        assertThat(AnswerReading.citations("Uno [1]. Dos [1, 2]. Tres [2][3].")).containsExactly(1, 1, 2, 2, 3).inOrder()
    }

    @Test
    fun otherBracketsAreNotCitations() {
        assertThat(AnswerReading.citations("[ 2 ] [Fuente 1] [1,] [a] [] (3)")).isEmpty()
        assertThat(AnswerReading.citations("[1 ,  2]")).containsExactly(1, 2).inOrder()
    }

    @Test
    fun unicodeDigitsCount_asPythonInt() {
        assertThat(AnswerReading.citations("Uno [١] y [２].")).containsExactly(1, 2).inOrder()
        // Outside the BMP: OSMANYA DIGIT ONE is category Nd too.
        assertThat(AnswerReading.citations("Uno [𐒡].")).containsExactly(1)
    }

    @Test
    fun pythonWhitespaceInsideACitation() {
        // \x1f and the ideographic space are whitespace for Python's \s.
        assertThat(AnswerReading.citations("A [1,\u001f2] y [3 ,　4].")).containsExactly(1, 2, 3, 4).inOrder()
    }

    @Test
    fun marksAsPythonFinditer() {
        // judge.py: re.finditer gives (1, 4) for «[[2]]», (0, 7) for «[1 ,　2]» and nothing for «[3,]» or an open «[1, 2».
        assertThat(AnswerReading.marks("[[2]]").map { it.range }).containsExactly(1..3)
        assertThat(AnswerReading.marks("[1 ,　2]x[3,]").map { it.range to it.numbers }).containsExactly(0..6 to listOf(1, 2))
        assertThat(AnswerReading.marks("[1, 2")).isEmpty()
        assertThat(AnswerReading.marks("[𐒡]").single().range).isEqualTo(0..3)
    }

    @Test
    fun refusalAfterNormalization() {
        assertThat(AnswerReading.isRefusal("No encuentro esa información en los documentos disponibles.")).isTrue()
        assertThat(AnswerReading.isRefusal("  no encuentro esa informacion en los documentos disponibles  ")).isTrue()
        assertThat(AnswerReading.isRefusal("«No encuentro esa información en los documentos disponibles».")).isTrue()
        assertThat(AnswerReading.isRefusal("\"No encuentro esa información en los documentos disponibles.\"")).isTrue()
        assertThat(AnswerReading.isRefusal("No encuentro esa información en los documentos disponibles. Sin embargo, el plazo es 30 días."))
            .isFalse()
    }

    @Test
    fun normRemovesThousandsCaseAccentsAndPunctuation() {
        assertThat(AnswerReading.norm("Hasta 1.000 euros.")).isEqualTo("hasta 1000 euros")
        assertThat(AnswerReading.norm("Gasta 1,632 calorías.")).isEqualTo("gasta 1632 calorias")
        assertThat(AnswerReading.norm("De 1.000.000 a 2 000 €")).isEqualTo("de 1000000 a 2000")
        assertThat(AnswerReading.norm("El plazo es de TREINTA días (art. 5).")).isEqualTo("el plazo es de treinta dias art 5")
        assertThat(AnswerReading.norm("versión 10.7 o 1.0000")).isEqualTo("version 10 7 o 1 0000")
    }

    @Test
    fun normKeepsPythonWordCharacters() {
        assertThat(AnswerReading.norm("x² _y_ ñandú")).isEqualTo("x² _y_ nandu")
    }

    @Test
    fun citedOrder_validNumbersInFirstCitationOrder() {
        assertThat(AnswerReading.citedOrder("A [3]. B [1, 3]. C [7]. D [0]. E [1].", blockCount = 4)).containsExactly(3, 1).inOrder()
        assertThat(AnswerReading.citedOrder("Sin citas.", blockCount = 4)).isEmpty()
        assertThat(AnswerReading.citedOrder("A [1].", blockCount = 0)).isEmpty()
    }
}

package com.lisdatasolutions.microrag.rag.answer

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.rag.answer.PromptBuilder.Block
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * F11 T019 (research R2): `offline/eval/prompt.py` to the character, with the cases of `test_prompt.py`. The
 * desktop's default template is «informe»; the app's is the adopted one (D-056), so the report cases name it.
 */
class PromptBuilderTest {

    private val rules =
        "Responde a la PREGUNTA usando únicamente las FUENTES.\n" +
            "\n" +
            "Reglas:\n" +
            "- Responde en español, de forma clara y breve.\n" +
            "- Después de cada afirmación, indica la fuente entre corchetes, por ejemplo [2].\n" +
            "- Si las fuentes no contienen la respuesta, responde exactamente:\n" +
            "  \"No encuentro esa información en los documentos disponibles.\"\n" +
            "- No uses conocimiento propio ni inventes datos, cifras o plazos.\n" +
            "\n"

    @Test
    fun templateWithNumberedSources() {
        val blocks = listOf(
            Block("Manual de garantías", "Devoluciones › Plazos", 12, " El plazo es de 30 días. "),
            Block("Condiciones generales", null, null, "Texto de empresas."),
        )

        assertThat(PromptBuilder.build(" ¿Cuánto tiempo tengo? ", blocks, null, template = "informe")).isEqualTo(
            rules + "FUENTES:\n" +
                "[1] Manual de garantías › Devoluciones › Plazos (p. 12)\nEl plazo es de 30 días.\n\n" +
                "[2] Condiciones generales\nTexto de empresas.\n\n" +
                "PREGUNTA: ¿Cuánto tiempo tengo?",
        )
    }

    @Test
    fun previousTurnIsTruncated() {
        val prompt = PromptBuilder.build("¿Y para empresas?", emptyList(), "p".repeat(400) to "r".repeat(600))

        assertThat(prompt).contains(
            "CONVERSACIÓN PREVIA (solo como contexto de la pregunta):\n" +
                "Usuario: ${"p".repeat(300)}\nAsistente: ${"r".repeat(500)}\n\nFUENTES:\n",
        )
    }

    @Test
    fun previousTurnIsCutByCodePoints_asPythonSlices() {
        val emoji = "😀"
        val prompt = PromptBuilder.build("¿Y?", emptyList(), "a".repeat(299) + emoji + "b" to "r")

        assertThat(prompt).contains("Usuario: ${"a".repeat(299)}$emoji\nAsistente: r\n")
    }

    @Test
    fun withoutBlocks_thereIsNoBlankLineBeforeTheQuestion() {
        assertThat(PromptBuilder.build("¿Plazo?", emptyList(), null)).endsWith("\n\nFUENTES:\nPREGUNTA: ¿Plazo?")
    }

    @Test
    fun onlyNullPartsAreLeftOut_anEmptyPathAndPageZeroAreWritten() {
        val prompt = PromptBuilder.build("¿Plazo?", listOf(Block("Doc", "", 0, "Texto."), Block(null, "Ruta", null, "Otro.")), null)

        assertThat(prompt).contains("[1] Doc ›  (p. 0)\nTexto.\n\n[2] Ruta\nOtro.\n\n")
    }

    @Test
    fun stripsWithPythonWhitespace_includingTheNoBreakSpace() {
        val prompt = PromptBuilder.build(" ¿Plazo? ", listOf(Block("Doc", null, null, " Texto.\n")), null)

        assertThat(prompt).endsWith("[1] Doc\nTexto.\n\nPREGUNTA: ¿Plazo?")
    }

    @Test
    fun v2AddsItsRulesAfterTheReportRules() {
        val prompt = PromptBuilder.build("¿Plazo?", emptyList(), null, template = "v2")

        assertThat(prompt).startsWith(rules.dropLast(1))
        assertThat(prompt).contains(
            "- Cita solo los números de las FUENTES, nunca números de artículos o apartados.\n" +
                "- Si hay conversación previa, interpreta la PREGUNTA como continuación de ella.\n\nFUENTES:",
        )
    }

    @Test
    fun v3AndV4AddTheirRulesAfterTheReportRules() {
        val v3 = PromptBuilder.build("¿Plazo?", emptyList(), null, template = "v3")
        val v4 = PromptBuilder.build("¿Plazo?", emptyList(), null, template = "v4")
        val v3Rules =
            "- Si la PREGUNTA pide varias cosas, responde a todas las que estén en las FUENTES.\n" +
                "- Copia cifras, plazos, importes y números de artículo tal como aparecen en la fuente que citas.\n"

        assertThat(v3).isEqualTo(rules.dropLast(1) + v3Rules + "\nFUENTES:\nPREGUNTA: ¿Plazo?")
        assertThat(v4).isEqualTo(
            rules.dropLast(1) + v3Rules +
                "- Si hay CONVERSACIÓN PREVIA, la PREGUNTA la continúa: búscala en las FUENTES con ese contexto " +
                "antes de responder con la frase anterior.\n" + "\nFUENTES:\nPREGUNTA: ¿Plazo?",
        )
    }

    @Test
    fun anUnknownTemplateIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PromptBuilder.build("¿Plazo?", emptyList(), null, template = "v9") }
    }

    @Test
    fun theDefaultLanguageIsSpanish_andGivesTheDesktopText() {
        val blocks = listOf(Block("Doc", null, 3, "Texto."))

        assertThat(PromptBuilder.build("¿Plazo?", blocks, null, language = PromptLanguage.SPANISH))
            .isEqualTo(PromptBuilder.build("¿Plazo?", blocks, null))
        assertThat(PromptBuilder.build("¿Plazo?", blocks, null)).contains("- Responde en español, de forma clara y breve.\n")
    }

    @Test
    fun refusalSentence() {
        assertThat(PromptBuilder.REFUSAL).isEqualTo("No encuentro esa información en los documentos disponibles.")
    }

    @Test
    fun estimatedTokensRoundUpCodePointsOverCharsPerToken() {
        assertThat(PromptBuilder.estimatedTokens("a".repeat(4554))).isEqualTo(1000)
        assertThat(PromptBuilder.estimatedTokens("a".repeat(4555))).isEqualTo(1001)
        assertThat(PromptBuilder.estimatedTokens("😀".repeat(5))).isEqualTo(2)
    }
}

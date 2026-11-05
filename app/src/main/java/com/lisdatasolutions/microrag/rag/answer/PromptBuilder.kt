package com.lisdatasolutions.microrag.rag.answer

import com.lisdatasolutions.microrag.domain.chat.PromptPart
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.rag.ingest.PyText
import kotlin.math.ceil

/** Response language of the prompt (F11 FR-014, G25). F12 evaluated «Automático» and kept Spanish only (D-061). */
enum class PromptLanguage(internal val inPrompt: String) { SPANISH("español") }

/**
 * The prompt of the report's template (§Plantilla de prompt), exactly as `offline/eval/prompt.py` builds
 * it (F11 research R2): everything in one user message, numbered sources, `[n]` citations and the
 * refusal sentence. `AnswerGoldenTest` checks it against the desktop for the 426 evaluation questions.
 */
object PromptBuilder {
    const val REFUSAL = "No encuentro esa información en los documentos disponibles."

    /** The refusal when the model answers in English anyway; `judge.REFUSAL_EN` (F12 P17, D-061). */
    const val REFUSAL_EN = "I can't find that information in the available documents."
    const val PREVIOUS_QUESTION_CHARS = 300
    const val PREVIOUS_ANSWER_CHARS = 500

    /** A context block as the prompt shows it; null parts are left out of its header. */
    data class Block(val documentName: String?, val headingPath: String?, val page: Int?, val text: String)

    /** F20: the page that enters as an image; [page] is null for a loose JPG or PNG, whose label is only its name. */
    data class PageSource(val documentName: String, val page: Int?) {
        val label: String get() = if (page != null) "$documentName, página $page" else documentName
    }

    /** The rule the v5-visual adds after the v4's (`visual_prompt.PAGE_RULE`, F18 research R11). */
    const val PAGE_RULE = "- Algunas FUENTES son imágenes de páginas: léelas y cítalas igual que las demás."

    private const val SOURCES = "FUENTES:\n"

    // Rules each template adds after the report's (D-035 levers of F05 and the F11 review, research R4).
    private val V2_RULES = listOf(
        "- Si alguna fuente contiene la respuesta, aunque use otras palabras, respóndela; usa la frase anterior solo si ninguna fuente la contiene.",
        "- Cita solo los números de las FUENTES, nunca números de artículos o apartados.",
        "- Si hay conversación previa, interpreta la PREGUNTA como continuación de ella.",
    )
    private val V3_RULES = listOf(
        "- Si la PREGUNTA pide varias cosas, responde a todas las que estén en las FUENTES.",
        "- Copia cifras, plazos, importes y números de artículo tal como aparecen en la fuente que citas.",
    )
    private val V4_RULES = V3_RULES + listOf(
        "- Si hay CONVERSACIÓN PREVIA, la PREGUNTA la continúa: búscala en las FUENTES con ese contexto antes de responder " +
            "con la frase anterior.",
    )
    private val EXTRA_RULES = mapOf("informe" to emptyList(), "v2" to V2_RULES, "v3" to V3_RULES, "v4" to V4_RULES)

    val TEMPLATES: List<String> = EXTRA_RULES.keys.toList()

    /** [previous] is the (question, answer) of the previous turn, or null. */
    fun build(
        question: String,
        blocks: List<Block>,
        previous: Pair<String, String>?,
        template: String = RagParameters.PROMPT_TEMPLATE,
        language: PromptLanguage = PromptLanguage.SPANISH,
    ): String {
        val extra = requireNotNull(EXTRA_RULES[template]) { "Unknown prompt template $template" }
        val lines = mutableListOf(
            "Responde a la PREGUNTA usando únicamente las FUENTES.",
            "",
            "Reglas:",
            "- Responde en ${language.inPrompt}, de forma clara y breve.",
            "- Después de cada afirmación, indica la fuente entre corchetes, por ejemplo [2].",
            "- Si las fuentes no contienen la respuesta, responde exactamente:",
            "  \"$REFUSAL\"",
            "- No uses conocimiento propio ni inventes datos, cifras o plazos.",
        )
        lines += extra
        lines += ""
        if (previous != null) {
            lines += "CONVERSACIÓN PREVIA (solo como contexto de la pregunta):"
            lines += "Usuario: ${previous.first.takeCodePoints(PREVIOUS_QUESTION_CHARS)}"
            lines += "Asistente: ${previous.second.takeCodePoints(PREVIOUS_ANSWER_CHARS)}"
            lines += ""
        }
        lines += "FUENTES:"
        blocks.forEachIndexed { i, block ->
            lines += "[${i + 1}] ${block.header()}"
            lines += PyText.strip(block.text)
            lines += ""
        }
        return lines.joinToString("\n") + "\nPREGUNTA: " + PyText.strip(question)
    }

    /**
     * F20 (research R4, contract B2): the v5-visual parts, exactly as `offline/eval/visual_prompt.build_parts` makes
     * them. The v4 rules, then [PAGE_RULE], then the previous turn; the text sources as the v4 numbers them; the page
     * after them, its label and then its [image]; and the question. `VisualPromptGoldenTest` checks it against the
     * desktop. [blocks] and [previous] are what the v4's limit left (DefaultChatSession).
     */
    fun buildVisual(
        question: String,
        blocks: List<Block>,
        page: PageSource,
        image: ByteArray,
        previous: Pair<String, String>?,
        language: PromptLanguage = PromptLanguage.SPANISH,
    ): List<PromptPart> {
        val template = RagParameters.PROMPT_TEMPLATE
        val (head, tail) = build(question, emptyList(), previous, template, language).split(SOURCES, limit = 2)
        val rulesEnd = build(question, emptyList(), null, template, language).substringBefore(SOURCES).trimEnd('\n')
        val conversation = head.substring(rulesEnd.length).trimStart('\n')
        val text = StringBuilder("$rulesEnd\n$PAGE_RULE\n\n$conversation$SOURCES")
        blocks.forEachIndexed { i, block -> text.append("[${i + 1}] ${block.header()}\n${PyText.strip(block.text)}\n\n") }
        text.append("[${blocks.size + 1}] ${page.label}\n")
        val question = "\n" + tail.trimStart('\n')
        return listOf(PromptPart.Text(text.toString()), PromptPart.Image(image), PromptPart.Text(question))
    }

    /** «documento › encabezados (p. N)», as the v4 writes a source's first line. */
    private fun Block.header(): String {
        val where = listOfNotNull(documentName, headingPath).joinToString(" › ")
        return where + page?.let { " (p. $it)" }.orEmpty()
    }

    /** ⌈code points / CHARS_PER_TOKEN⌉, the estimate of the context budget (research R10). */
    fun estimatedTokens(prompt: String): Int =
        ceil(prompt.codePointCount(0, prompt.length) / RagParameters.CHARS_PER_TOKEN).toInt()

    /** The first [count] code points, as a Python slice; never splits a surrogate pair. */
    private fun String.takeCodePoints(count: Int): String =
        if (codePointCount(0, length) <= count) this else substring(0, offsetByCodePoints(0, count))
}

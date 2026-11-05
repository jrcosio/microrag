package com.lisdatasolutions.microrag.rag.answer

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.chat.PromptPart
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * F20 T006 (research R4, contract B2): [PromptBuilder.buildVisual] gives, part by part, what
 * `offline/eval/visual_prompt.build_parts` gives for each case of `offline/golden/visual_prompt_golden.jsonl`.
 * An image is its marker there, so here it is the marker's bytes.
 */
class VisualPromptGoldenTest {

    @Serializable
    private data class Case(
        val id: String,
        val question: String,
        val previous: List<String>?,
        val blocks: List<GoldenPromptBlock>,
        val page: Page,
        val parts: List<Part>,
    )

    @Serializable
    private data class Page(@SerialName("document_name") val documentName: String, val page: Int?, val image: String)

    @Serializable
    private data class Part(val text: String? = null, val image: String? = null)

    private val cases = File("../offline/golden/visual_prompt_golden.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.decodeFromString<Case>(it) }

    @Test
    fun everyCase_matchesTheDesktopPartByPart() {
        assertThat(cases).hasSize(12)
        cases.forEach { case ->
            val parts = PromptBuilder.buildVisual(
                question = case.question,
                blocks = case.blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) },
                page = PromptBuilder.PageSource(case.page.documentName, case.page.page),
                image = case.page.image.toByteArray(Charsets.US_ASCII),
                previous = case.previous?.let { it[0] to it[1] },
            )
            val actual = parts.map { part ->
                when (part) {
                    is PromptPart.Text -> Part(text = part.text)
                    is PromptPart.Image -> Part(image = part.jpeg.toString(Charsets.US_ASCII))
                }
            }
            assertWithMessage(case.id).that(actual).isEqualTo(case.parts)
        }
    }

    @Test
    fun thePageIsTheNumberAfterTheBlocks_andTheTextLimitCountsOnlyText() {
        val case = cases.single { it.id == "v4-four-blocks" }
        val parts = PromptBuilder.buildVisual(
            question = case.question,
            blocks = case.blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) },
            page = PromptBuilder.PageSource(case.page.documentName, case.page.page),
            image = byteArrayOf(1, 2, 3),
            previous = null,
        )

        val text = parts.filterIsInstance<PromptPart.Text>().joinToString("") { it.text }
        assertThat(text).contains("[5] informe-ine.pdf, página 4\n")
        assertThat(text).contains(PromptBuilder.PAGE_RULE)
        assertThat(parts.filterIsInstance<PromptPart.Image>()).hasSize(1)
    }

    @Test
    fun withoutAPage_theV4IsUntouched() {
        val blocks = cases.single { it.id == "v3-one-block" }.blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) }

        assertThat(PromptBuilder.build("¿Cuál es la tasa de paro?", blocks, null)).doesNotContain(PromptBuilder.PAGE_RULE)
    }
}

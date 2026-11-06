package com.lisdatasolutions.microrag.rag.answer

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.security.MessageDigest
import org.junit.Test

/**
 * F11 T021 (research R2–R3, SC-002, SC-003): for the 426 live questions of the evaluation set, the app
 * builds the prompt the desktop built, and reads each generated answer as judge.py does.
 */
class AnswerGoldenTest {

    private val cases = AnswerGoldenFiles.cases(File("../offline/golden/${AnswerGoldenFiles.GOLDEN}").readText())

    private fun prompt(case: GoldenAnswerCase): String = PromptBuilder.build(
        question = case.question,
        blocks = case.blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) },
        previous = case.previous?.let { it[0] to it[1] },
        template = case.template,
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Test
    fun everyLiveQuestionIsThere_withTheActiveTemplate() {
        assertThat(cases).hasSize(426)
        assertThat(cases.map { it.id }).isInStrictOrder()
        assertThat(cases.map { it.template }.toSet()).containsExactly(com.lisdatasolutions.microrag.domain.rag.RagParameters.PROMPT_TEMPLATE)
    }

    @Test
    fun thePromptMatchesTheDesktop() {
        cases.forEach { case ->
            val prompt = prompt(case)
            case.prompt?.let { expected ->
                val at = prompt.zip(expected).indexOfFirst { (a, b) -> a != b }.takeIf { it >= 0 } ?: minOf(prompt.length, expected.length)
                assertWithMessage("${case.id}: first difference at char $at").that(prompt).isEqualTo(expected)
            }
            assertWithMessage("${case.id}: code points").that(prompt.codePointCount(0, prompt.length)).isEqualTo(case.promptChars)
            assertWithMessage("${case.id}: SHA-256").that(sha256(prompt)).isEqualTo(case.promptSha256)
        }
    }

    /** F14 T026 (contract G2): the prefix the engine prefills once and the rest add up to the prompt, to the character. */
    @Test
    fun theSplitPrompt_isThePromptToTheCharacter_withOnePrefixPerTemplate() {
        val prefixes = cases.map { case ->
            val split = PromptBuilder.buildSplit(
                question = case.question,
                blocks = case.blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) },
                previous = case.previous?.let { it[0] to it[1] },
                template = case.template,
            )
            assertWithMessage("${case.id}: prefix + rest").that(split.whole).isEqualTo(prompt(case))
            assertWithMessage("${case.id}: the rest starts after the rules").that(split.rest).matches("(?s)(CONVERSACIÓN PREVIA|FUENTES:).*")
            split.prefix
        }
        assertThat(prefixes.toSet()).hasSize(1)
        assertThat(prefixes.first()).endsWith(".\n\n")
    }

    @Test
    fun theAnswersAreReadAsTheDesktop() {
        cases.forEach { case ->
            assertWithMessage("${case.id}: citations").that(AnswerReading.citations(case.answer)).isEqualTo(case.citations)
            assertWithMessage("${case.id}: cited order").that(AnswerReading.citedOrder(case.answer, case.blocks.size)).isEqualTo(case.citedOrder)
            assertWithMessage("${case.id}: refusal").that(AnswerReading.isRefusal(case.answer)).isEqualTo(case.refusal)
            assertWithMessage("${case.id}: norm").that(AnswerReading.norm(case.answer)).isEqualTo(case.norm)
        }
    }

    @Test
    fun theGoldenCoversFollowUpsRefusalsAndCitations() {
        assertThat(cases.count { it.previous != null }).isEqualTo(50)
        assertThat(cases.count { it.refusal }).isGreaterThan(0)
        assertThat(cases.count { it.citedOrder.size >= 2 }).isGreaterThan(0)
    }
}

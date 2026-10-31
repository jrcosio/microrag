package com.lisdatasolutions.microrag.rag.answer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan
import com.lisdatasolutions.microrag.feature.chat.AnswerFormatter
import java.security.MessageDigest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-002 and SC-003 on the device, with Android's ICU and its regex engine: AnswerGoldenTest runs on the
 * JVM, which accepted a pattern Android rejects (the first answer on the phone crashed the app). The
 * golden is a test APK asset taken from offline/golden (F11 research R2, R3).
 */
@RunWith(AndroidJUnit4::class)
class AnswerGoldenInstrumentedTest {

    private val cases = AnswerGoldenFiles.cases(
        InstrumentationRegistry.getInstrumentation().context.assets.open(AnswerGoldenFiles.GOLDEN).bufferedReader().use { it.readText() },
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Test
    fun thePromptMatchesTheDesktop() {
        assertThat(cases).hasSize(426)
        cases.forEach { case ->
            val prompt = PromptBuilder.build(
                question = case.question,
                blocks = case.blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) },
                previous = case.previous?.let { it[0] to it[1] },
                template = case.template,
            )
            assertWithMessage("${case.id}: SHA-256").that(sha256(prompt)).isEqualTo(case.promptSha256)
        }
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
    fun everyAnswerFormats_withOneChipPerCitedSource() {
        cases.forEach { case ->
            val chips = AnswerFormatter.format(case.answer, case.blocks.size).blocks
                .flatMap { block -> if (block is AnswerBlock.Paragraph) block.spans else (block as AnswerBlock.Bullet).spans }
                .filterIsInstance<AnswerSpan.Citation>().map { it.number }.distinct()
            assertWithMessage("${case.id}: chips").that(chips).isEqualTo((1..case.citedOrder.size).toList())
        }
    }
}

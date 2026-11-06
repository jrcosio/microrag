package com.lisdatasolutions.microrag.rag.answer

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import kotlin.math.ceil
import org.junit.Test

/** F14 T035 (research R9, contract G6): the same classes as `offline/tests/test_token_estimate.py`, and today's estimate. */
class TokenEstimateTest {

    private val cases = listOf(
        "El plazo es de 30 días." to Triple(15, 2, 6),
        "Ñandú, pingüino y ñu: 1.234,56 €." to Triple(16, 6, 11),
        "2020 2021 2022" to Triple(0, 12, 2),
        "" to Triple(0, 0, 0),
    )

    @Test
    fun theClasses_areThoseOfTheDesktop() {
        cases.forEach { (text, expected) -> assertWithMessage(text).that(PromptBuilder.tokenClasses(text)).isEqualTo(expected) }
    }

    @Test
    fun withOneRatio_itIsTodaysEstimate() {
        val (a, b, c) = RagParameters.TOKEN_ESTIMATE
        if (a == b && b == c) {
            cases.forEach { (text, _) ->
                assertWithMessage(text).that(PromptBuilder.estimatedTokens(text)).isEqualTo(ceil(text.codePointCount(0, text.length) / a).toInt())
            }
        }
        assertThat(PromptBuilder.estimatedTokens("")).isEqualTo(0)
    }
}

package com.lisdatasolutions.microrag.domain.rag

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.text.SpanishText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.io.File

/** F05 contracts/rag-parameters.md, rule P2. */
class RagParametersTest {

    private val json: JsonObject =
        Json.parseToJsonElement(File("../offline/config/rag_parameters.json").readText()).jsonObject

    private val kotlinValues: Map<String, Any> = mapOf(
        "dims" to RagParameters.DIMS,
        "contextual_header" to RagParameters.CONTEXTUAL_HEADER,
        "normalizer_version" to RagParameters.NORMALIZER_VERSION,
        "chunk_target_chars" to RagParameters.CHUNK_TARGET_CHARS,
        "chunk_max_chars" to RagParameters.CHUNK_MAX_CHARS,
        "chars_per_token" to RagParameters.CHARS_PER_TOKEN,
        "embedding_overflow_cut" to RagParameters.EMBEDDING_OVERFLOW_CUT,
        "candidates" to RagParameters.CANDIDATES,
        "rrf_k" to RagParameters.RRF_K,
        "follow_up_rule" to RagParameters.FOLLOW_UP_RULE,
        "max_blocks" to RagParameters.MAX_BLOCKS,
        "max_per_document" to RagParameters.MAX_PER_DOCUMENT,
        "budget_tokens" to RagParameters.BUDGET_TOKENS,
        "expand_top" to RagParameters.EXPAND_TOP,
        "prompt_limit_tokens" to RagParameters.PROMPT_LIMIT_TOKENS,
        "prompt_template" to RagParameters.PROMPT_TEMPLATE,
        "llm_model" to RagParameters.LLM_MODEL.name,
        "max_output_tokens" to RagParameters.MAX_OUTPUT_TOKENS,
    )

    @Test
    fun everyParameterMatchesTheEvaluationFile() {
        val fileValues = json.filterKeys { it !in setOf("decided_in", "decisions") }
        assertThat(fileValues.keys).containsExactlyElementsIn(kotlinValues.keys)
        kotlinValues.forEach { (key, value) ->
            val content = (json.getValue(key) as JsonPrimitive).content
            assertWithMessage(key).that(content).isEqualTo(value.toString())
        }
    }

    @Test
    fun parametersAgreeWithTheVectorSpaceAndNormalizer() {
        assertThat(RagParameters.DIMS).isEqualTo(EmbeddingMath.DIMENSIONS)
        assertThat(RagParameters.NORMALIZER_VERSION).isEqualTo(SpanishText.VERSION)
        assertThat(RagParameters.DIMS).isEqualTo(RagIndexConfig.current().dims)
    }
}

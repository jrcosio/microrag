package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The desktop embedding golden is consistent with the app (T034): same model, 256 dimensions, inputs
 * built exactly like [EmbeddingPrefixes] and unit vectors. Cosines are checked on the phone.
 */
class EmbeddingGoldenFileTest {

    private fun jsonl(name: String): List<JsonObject> =
        File("../offline/golden/$name").readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }

    private val golden = jsonl("embedding_golden.jsonl")
    private val meta = golden.first().getValue("meta").jsonObject
    private val entries = golden.drop(1)
    private val texts = jsonl("embedding_texts.jsonl").associateBy { it.getValue("id").jsonPrimitive.content }

    @Test
    fun meta_describesTheAppModelAndDimensions() {
        assertThat(meta.getValue("model_sha256").jsonPrimitive.content).isEqualTo(ModelCatalog.byId(ModelId.EG2_270M).sha256)
        assertThat(meta.getValue("model_file").jsonPrimitive.content).isEqualTo(ModelCatalog.byId(ModelId.EG2_270M).fileName)
        assertThat(meta.getValue("dims").jsonPrimitive.int).isEqualTo(EmbeddingMath.DIMENSIONS)
        assertThat(meta.getValue("max_input_length").jsonPrimitive.int).isEqualTo(512)
    }

    @Test
    fun entries_are25QueriesAnd25Documents() {
        val kinds = entries.groupingBy { it.getValue("kind").jsonPrimitive.content }.eachCount()

        assertThat(kinds).containsExactly("query", 25, "doc", 25)
        assertThat(entries.map { it.getValue("id").jsonPrimitive.content }).containsExactlyElementsIn(texts.keys)
    }

    @Test
    fun inputs_areBuiltLikeEmbeddingPrefixes() {
        entries.forEach { entry ->
            val text = texts.getValue(entry.getValue("id").jsonPrimitive.content)
            val content = text.getValue("text").jsonPrimitive.content
            val expected = when (entry.getValue("kind").jsonPrimitive.content) {
                "query" -> EmbeddingPrefixes.query(content)
                else -> EmbeddingPrefixes.document(content, text.getValue("header").jsonPrimitive.content)
            }
            assertThat(entry.getValue("input").jsonPrimitive.content).isEqualTo(expected)
        }
    }

    @Test
    fun vectors_have256UnitComponents() {
        entries.forEach { entry ->
            val vector = entry.getValue("v256").jsonArray.map { it.jsonPrimitive.float }.toFloatArray()

            assertThat(vector.size).isEqualTo(EmbeddingMath.DIMENSIONS)
            assertThat(EmbeddingMath.l2Norm(vector)).isWithin(1e-5).of(1.0)
        }
    }
}

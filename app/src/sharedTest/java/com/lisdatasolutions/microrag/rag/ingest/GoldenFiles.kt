package com.lisdatasolutions.microrag.rag.ingest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reading of the offline golden files shared by the JVM and instrumented tests (F06). */
object GoldenFiles {
    /** JSON lines; split on LF only, because the golden strings may hold other line separators. */
    fun cases(text: String): List<JsonObject> = text.split("\n").filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }

    fun blocks(element: JsonElement): List<RawBlock> = element.jsonArray.map { item ->
        val block = item.jsonObject
        RawBlock(
            headingPath = block.getValue("heading_path").jsonArray.map { it.jsonPrimitive.content },
            text = block.getValue("text").jsonPrimitive.content,
            page = block.getValue("page").let { if (it is JsonNull) null else it.jsonPrimitive.int },
        )
    }

    fun chunks(element: JsonElement): List<Chunk> = (element as JsonArray).map { item ->
        val chunk = item.jsonObject
        Chunk(
            ordinal = chunk.getValue("ordinal").jsonPrimitive.int,
            headingPath = chunk.getValue("heading_path").jsonPrimitive.content,
            content = chunk.getValue("content").jsonPrimitive.content,
            page = chunk.getValue("page").let { if (it is JsonNull) null else it.jsonPrimitive.int },
        )
    }

    /** What the Kotlin pipeline gives for one extraction golden case, as the contract says. */
    fun extract(case: JsonObject): Any = when (case.getValue("kind").jsonPrimitive.content) {
        "decode" -> TextDecoding.decode(java.util.Base64.getDecoder().decode(case.getValue("input_b64").jsonPrimitive.content))
        "text" -> buildList { PlainTextExtractor().extract(PyText.splitLines(input(case)).asSequence(), ::add) }
        "markdown" -> buildList { MarkdownExtractor().extract(PyText.splitLines(input(case)).asSequence(), ::add) }
        "pdf_page" -> PdfExtraction.paragraphs(PyText.splitLines(input(case))).joinToString("\n\n")
        else -> error("unknown kind")
    }

    fun expected(case: JsonObject): Any = when (case.getValue("kind").jsonPrimitive.content) {
        "decode", "pdf_page" -> case.getValue("text").jsonPrimitive.content
        else -> blocks(case.getValue("blocks"))
    }

    private fun input(case: JsonObject) = case.getValue("input").jsonPrimitive.content
}

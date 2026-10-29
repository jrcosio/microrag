package com.lisdatasolutions.microrag.rag.retrieval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Reading of the retrieval golden and the parity sample (F10 data-model §6–§7), shared by the JVM tests
 * (`../offline/golden`) and the instrumented ones (test APK assets). Written by `offline/gen_retrieval_golden.py`.
 */
object RetrievalGoldenFiles {
    const val GOLDEN = "retrieval_golden.jsonl"
    const val PARITY = "retrieval_parity.jsonl"

    fun cases(text: String): List<GoldenRetrievalCase> = lines(text).map { Json.decodeFromString(it) }

    fun parity(text: String): List<ParityQuestion> = lines(text).map { Json.decodeFromString(it) }

    /** JSON lines; split on LF only, like the other golden readers. */
    private fun lines(text: String) = text.split("\n").filter { it.isNotBlank() }
}

/** One live question of the evaluation set, replayed on the desktop reference index. */
@Serializable
data class GoldenRetrievalCase(
    val id: String,
    val question: String,
    val previous: String?,
    @SerialName("follow_up") val followUp: Boolean,
    @SerialName("search_text") val searchText: String,
    val vector: List<Long>,
    val fts: List<Long>,
    val fused: List<Long>,
    /** Chunk id → [document id, ordinal, token estimate, id of the next chunk or null]. */
    @SerialName("chunks") val chunkRows: Map<String, List<Long?>>,
    /** Configuration name (`d034`, `report`) → blocks as [document id, first, last, tokens, chunk ids]. */
    @SerialName("blocks") val blockRows: Map<String, List<JsonArray>>,
) {
    val chunks: Map<Long, GoldenChunk> by lazy {
        chunkRows.entries.associate { (id, row) ->
            id.toLong() to GoldenChunk(id.toLong(), row[0]!!, row[1]!!.toInt(), row[2]!!.toInt(), row[3])
        }
    }

    fun blocks(configuration: String): List<GoldenBlock> = blockRows.getValue(configuration).map { row ->
        GoldenBlock(
            documentId = row[0].jsonPrimitive.long,
            firstOrdinal = row[1].jsonPrimitive.long.toInt(),
            lastOrdinal = row[2].jsonPrimitive.long.toInt(),
            tokens = row[3].jsonPrimitive.long.toInt(),
            chunkIds = row[4].jsonArray.map { it.jsonPrimitive.long },
        )
    }
}

data class GoldenChunk(val id: Long, val documentId: Long, val ordinal: Int, val tokens: Int, val nextId: Long?)

data class GoldenBlock(val documentId: Long, val firstOrdinal: Int, val lastOrdinal: Int, val tokens: Int, val chunkIds: List<Long>)

/** One question of the fixed parity sample, with its desktop hit under D-034. */
@Serializable
data class ParityQuestion(
    val id: String,
    val type: String,
    val question: String,
    val previous: String?,
    /** Manifest id of the document that holds the evidence. */
    val doc: String,
    /** File name of that document in the corpus (`docs/<file>` in the test assets). */
    val file: String,
    val evidence: String,
    @SerialName("desktop_hit") val desktopHit: Int,
)

package com.lisdatasolutions.microrag.debug.feasibility

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Synthetic Spanish test texts built from the project's own RAG report, never user data (FR-019).
 * [prompt] follows the report's prompt template with six numbered sources (research R9).
 */
@Serializable
data class FeasibilityTexts(
    val documentHeader: String,
    val queries: List<String>,
    val documents: List<String>,
    val measuredDocument: String,
    val prompt: String = "",
)

fun interface FeasibilityTextsSource {
    suspend fun load(): FeasibilityTexts
}

class AssetFeasibilityTexts(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : FeasibilityTextsSource {

    override suspend fun load(): FeasibilityTexts = withContext(ioDispatcher) {
        val assets = context.assets
        val texts = assets.open("$DIR/texts_es.json").bufferedReader().use { json.decodeFromString<FeasibilityTexts>(it.readText()) }
        val prompt = assets.open("$DIR/prompt_es.txt").bufferedReader().use { it.readText() }
        texts.copy(prompt = prompt.trim())
    }

    private companion object {
        const val DIR = "feasibility"
        val json = Json { ignoreUnknownKeys = false }
    }
}

package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.flow.Flow

/** Text generation with Gemma. All calls run on `AppDispatchers.llm`; text is never logged. */
interface TextGenerator : ManagedEngine {
    val model: ModelId

    suspend fun initialize()

    /** Streams a fresh single-turn answer to [prompt]. Cancelling the collector stops generation. */
    fun generate(prompt: String): Flow<GenerationEvent>

    /** Stops the generation in progress, if any. */
    fun cancel()
}

sealed interface GenerationEvent {
    data class Token(val textDelta: String) : GenerationEvent
    data class Finished(val stats: GenerationStats) : GenerationEvent
}

data class GenerationConfig(
    val maxNumTokens: Int,
    val maxOutputTokens: Int,
    val greedy: Boolean,
    /** Null keeps the model's default; FLOAT32 avoids fp16 precision issues on some GPUs (research R14). */
    val activationPrecision: ActivationPrecision? = null,
)

enum class ActivationPrecision { FLOAT32, FLOAT16 }

/**
 * Timing of one generation. Fields from the runtime benchmark are null unless
 * [EngineFactory.benchmarkStatsEnabled] is set; [ttftWallMs] is always measured.
 */
data class GenerationStats(
    val initSeconds: Double?,
    val ttftSeconds: Double?,
    val ttftWallMs: Long,
    val totalWallMs: Long,
    val prefillTokens: Int?,
    val prefillTokensPerSecond: Double?,
    val decodeTokens: Int?,
    val decodeTokensPerSecond: Double?,
)

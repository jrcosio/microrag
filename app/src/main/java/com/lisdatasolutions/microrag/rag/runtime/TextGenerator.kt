package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.flow.Flow

/** Text generation with Gemma. All calls run on `AppDispatchers.llm`; text is never logged. */
interface TextGenerator : ManagedEngine {
    val model: ModelId

    suspend fun initialize()

    /** Streams a fresh single-turn answer to [prompt]. Cancelling the collector stops generation. */
    fun generate(prompt: String): Flow<GenerationEvent>

    /**
     * F20 (research R5): as [generate], with text and images when the engine was created with vision
     * ([GenerationConfig.visionBackend]). A generator without vision only takes [GenerationPrompt.Text].
     */
    fun generate(prompt: GenerationPrompt): Flow<GenerationEvent> = when (prompt) {
        is GenerationPrompt.Text -> generate(prompt.text)
        is GenerationPrompt.Parts -> throw UnsupportedOperationException("Generator for $model has no vision")
    }

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
    /** F20 (research R5): the vision encoder's backend, CPU for Gemma; null creates the engine without vision. */
    val visionBackend: ComputeBackend? = null,
    /** Images a prompt may carry; only with [visionBackend]. */
    val maxNumImages: Int = 0,
)

/** What a generator receives (F20 research R5): text, or text and images in order. */
sealed interface GenerationPrompt {
    data class Text(val text: String) : GenerationPrompt

    /** [visualTokens] is the budget per image; null keeps the file's default, as the F18 diagnostic uses. */
    data class Parts(val parts: List<GenerationPart>, val visualTokens: Int?) : GenerationPrompt
}

sealed interface GenerationPart {
    data class Text(val text: String) : GenerationPart

    /** An encoded image (JPEG); never logged. */
    class Image(val bytes: ByteArray) : GenerationPart
}

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

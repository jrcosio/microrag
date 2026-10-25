package com.lisdatasolutions.microrag.debug.feasibility

import kotlinx.serialization.Serializable

/** Repetitions per scenario (spec Assumptions, data-model §3). */
@Serializable
data class MeasurementProtocol(
    val warmup: Int,
    val measured: Int,
    val maxOutputTokens: Int? = null,
    val texts: Int? = null,
) {
    companion object {
        val EMBEDDING = MeasurementProtocol(warmup = 3, measured = 30)
        val GENERATION = MeasurementProtocol(warmup = 1, measured = 3, maxOutputTokens = 128)
        val COMPATIBILITY = MeasurementProtocol(warmup = 0, measured = 1, texts = 20)

        /** Context window for a ~2,000-token prompt plus 128 output tokens, with margin (research R4). */
        const val GENERATION_MAX_NUM_TOKENS = 4096
        const val COMPATIBILITY_MIN_COSINE = 0.99
        const val QUERY_P95_BUDGET_MS = 150.0
    }
}

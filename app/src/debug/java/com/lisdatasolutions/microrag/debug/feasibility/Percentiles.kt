package com.lisdatasolutions.microrag.debug.feasibility

import kotlin.math.ceil

/** Nearest-rank percentiles: with 30 samples, p50 is the 15th and p95 the 29th smallest. */
object Percentiles {
    fun of(samples: List<Double>, percentile: Int): Double {
        require(samples.isNotEmpty()) { "No samples" }
        require(percentile in 1..100) { "Percentile out of range: $percentile" }
        val sorted = samples.sorted()
        val rank = ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}

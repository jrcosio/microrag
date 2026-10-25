package com.lisdatasolutions.microrag.debug.feasibility

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PercentilesTest {

    @Test
    fun nearestRank_onThirtySamples() {
        val samples = (30 downTo 1).map { it.toDouble() }

        assertThat(Percentiles.of(samples, 50)).isEqualTo(15.0)
        assertThat(Percentiles.of(samples, 95)).isEqualTo(29.0)
    }

    @Test
    fun singleSample_isEveryPercentile() {
        assertThat(Percentiles.of(listOf(7.0), 50)).isEqualTo(7.0)
        assertThat(Percentiles.of(listOf(7.0), 95)).isEqualTo(7.0)
    }

    @Test
    fun emptySamples_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { Percentiles.of(emptyList(), 50) }
    }
}

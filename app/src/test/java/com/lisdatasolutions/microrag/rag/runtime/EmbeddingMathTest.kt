package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import kotlin.math.sqrt
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbeddingMathTest {

    @Test
    fun truncateAndNormalize_keepsTheFirst256ComponentsWithUnitNorm() {
        val raw = FloatArray(768) { (it % 7 - 3).toFloat() + 0.5f }

        val result = EmbeddingMath.truncateAndNormalize(raw)

        assertThat(result.size).isEqualTo(256)
        assertThat(EmbeddingMath.l2Norm(result)).isWithin(1e-5).of(1.0)
        // Same direction as the untruncated prefix: cosine 1 with the first 256 raw components.
        assertThat(EmbeddingMath.cosine(result, raw.copyOf(256))).isWithin(1e-6).of(1.0)
    }

    @Test
    fun truncateAndNormalize_rejectsShortOrZeroVectors() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.truncateAndNormalize(FloatArray(128) { 1f })
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.truncateAndNormalize(FloatArray(768))
        }
    }

    @Test
    fun cosine_isOneForIdenticalAndZeroForOrthogonalVectors() {
        val a = floatArrayOf(1f, 2f, 3f)
        assertThat(EmbeddingMath.cosine(a, a)).isWithin(1e-9).of(1.0)
        assertThat(EmbeddingMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 5f))).isWithin(1e-9).of(0.0)
    }

    @Test
    fun l2Norm_matchesTheEuclideanLength() {
        assertThat(EmbeddingMath.l2Norm(floatArrayOf(3f, 4f))).isWithin(1e-9).of(5.0)
        assertThat(EmbeddingMath.l2Norm(floatArrayOf(1f, 1f))).isWithin(1e-9).of(sqrt(2.0))
    }
}

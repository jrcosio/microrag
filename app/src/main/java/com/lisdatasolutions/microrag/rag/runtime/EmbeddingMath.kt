package com.lisdatasolutions.microrag.rag.runtime

import kotlin.math.sqrt

/** Matryoshka truncation and vector helpers, in our own code so on-device and offline vectors match. */
object EmbeddingMath {
    const val DIMENSIONS = 256

    /** Keeps the first [dimensions] components and re-normalizes them to unit L2 norm. */
    fun truncateAndNormalize(vector: FloatArray, dimensions: Int = DIMENSIONS): FloatArray {
        require(vector.size >= dimensions) { "Embedding has ${vector.size} components, expected >= $dimensions" }
        val norm = l2Norm(vector, dimensions)
        require(norm > 0.0) { "Embedding prefix has zero norm" }
        return FloatArray(dimensions) { (vector[it] / norm).toFloat() }
    }

    fun l2Norm(vector: FloatArray): Double = l2Norm(vector, vector.size)

    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectors differ in size: ${a.size} vs ${b.size}" }
        var dot = 0.0
        for (i in a.indices) dot += a[i].toDouble() * b[i].toDouble()
        return dot / (l2Norm(a) * l2Norm(b))
    }

    private fun l2Norm(vector: FloatArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) sum += vector[i].toDouble() * vector[i].toDouble()
        return sqrt(sum)
    }
}

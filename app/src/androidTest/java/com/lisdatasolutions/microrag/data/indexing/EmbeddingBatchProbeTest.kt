package com.lisdatasolutions.microrag.data.indexing

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F07 T003 (research R5): LiteRT-LM 0.18.1 has computeEmbeddingBatch, which the report did not know.
 * Measures 64 real chunks one by one against batches of 8 and 16, per backend, and how close the
 * vectors are. Logged with `batch-probe`; a gain of 30 % or more stops F07 for a decision (FR-014).
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class EmbeddingBatchProbeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val options = EmbeddingOptions(normalize = false, outputSize = null)

    private fun EmbeddingEngine.serial(inputs: List<String>): List<FloatArray> =
        inputs.map { EmbeddingMath.truncateAndNormalize(computeEmbedding(listOf(InputData.Text(it)), options).embedding) }

    private fun EmbeddingEngine.batched(inputs: List<String>, size: Int): List<FloatArray> = inputs.chunked(size).flatMap { group ->
        computeEmbeddingBatch(group.map { listOf(InputData.Text(it)) }, options).map { EmbeddingMath.truncateAndNormalize(it.embedding) }
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val started = SystemClock.elapsedRealtime()
        val result = block()
        return result to SystemClock.elapsedRealtime() - started
    }

    @Test
    fun batchesAgainstOneByOne() {
        assumeTrue("EmbeddingGemma 2 270M is not installed", EmbeddingProbes.modelFile(context).isFile)
        val chunks = EmbeddingProbes.chunks(context, instrumentation.context, CHUNKS)
        val inputs = chunks.map { chunk ->
            EmbeddingPrefixes.document(chunk.content, listOf("text_200_pages", chunk.headingPath).filter { it.isNotBlank() }.joinToString(" › "))
        }
        assertThat(inputs).hasSize(CHUNKS)

        for (backend in EmbeddingProbes.backends) {
            EmbeddingProbes.engine(context, backend).use { engine ->
                engine.serial(inputs.take(2)) // warm-up
                val (serial, serialMs) = timed { engine.serial(inputs) }
                Log.i(EmbeddingProbes.TAG, "batch-probe backend=$backend mode=serial chunks=$CHUNKS ms=$serialMs rate=${rate(serialMs)}")
                for (size in BATCH_SIZES) {
                    val (batched, batchMs) = timed { engine.batched(inputs, size) }
                    val minCosine = serial.indices.minOf { EmbeddingMath.cosine(serial[it], batched[it]) }
                    Log.i(
                        EmbeddingProbes.TAG,
                        "batch-probe backend=$backend mode=batch$size chunks=$CHUNKS ms=$batchMs rate=${rate(batchMs)} " +
                            "speedup=${"%.2f".format(serialMs.toDouble() / batchMs)} min_cosine=${"%.6f".format(minCosine)}",
                    )
                    assertThat(batched).hasSize(CHUNKS)
                }
            }
        }
    }

    private fun rate(ms: Long) = "%.2f".format(CHUNKS * 1000.0 / ms)

    private companion object {
        const val CHUNKS = 64
        val BATCH_SIZES = listOf(8, 16)
    }
}

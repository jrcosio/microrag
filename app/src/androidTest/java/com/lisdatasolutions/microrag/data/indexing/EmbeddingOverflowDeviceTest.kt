package com.lisdatasolutions.microrag.data.indexing

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F07 T002 (research R6): what LiteRT-LM throws for an input over its 512 tokens, and that the D-036
 * cuts make it fit. Logged with `overflow-probe`; the exception decides how the app tells it apart.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class EmbeddingOverflowDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun EmbeddingEngine.embed(text: String): FloatArray =
        computeEmbedding(listOf(InputData.Text(text)), EmbeddingOptions(normalize = false, outputSize = null)).embedding

    /** The D-036 rule as offline/embedding_text.py writes it (lengths in code points); the app's port comes in T013. */
    private fun cut(text: String): String {
        val keep = (text.codePointCount(0, text.length) * 0.9).toInt()
        val cut = text.substring(0, text.offsetByCodePoints(0, keep))
        val space = cut.lastIndexOf(' ')
        return if (space >= 0) cut.substring(0, space) else cut
    }

    @Test
    fun aTooLongInputFailsAndTheCutsMakeItFit() {
        assumeTrue("EmbeddingGemma 2 270M is not installed", EmbeddingProbes.modelFile(context).isFile)
        val input = EmbeddingPrefixes.document(LONG_TEXT, "Manual de garantías › Devoluciones › Plazos")

        for (backend in EmbeddingProbes.backends) {
            EmbeddingProbes.engine(context, backend).use { engine ->
                val shortVector = engine.embed(EmbeddingPrefixes.document("Texto corto de prueba.", "Manual"))
                assertThat(shortVector.size).isAtLeast(256)

                val failure = runCatching { engine.embed(input) }.exceptionOrNull()
                Log.i(
                    EmbeddingProbes.TAG,
                    "overflow-probe backend=$backend chars=${input.length} class=${failure?.javaClass?.name} " +
                        "super=${failure?.javaClass?.superclass?.name} message=${failure?.message}",
                )
                assertWithMessage("an input of ${input.length} chars should not fit in 512 tokens").that(failure).isNotNull()

                var text = input
                var vector: FloatArray? = null
                var attempts = 1
                while (vector == null && attempts < MAX_ATTEMPTS) {
                    text = cut(text)
                    attempts++
                    vector = runCatching { engine.embed(text) }.getOrNull()
                }
                Log.i(EmbeddingProbes.TAG, "overflow-probe backend=$backend accepted_after_cuts=${attempts - 1} chars=${text.length}")
                assertWithMessage("still too long after ${attempts - 1} cuts").that(vector).isNotNull()

                // The engine keeps working after the rejection.
                assertThat(engine.embed(EmbeddingPrefixes.document("Otro texto corto.", "Manual")).size).isAtLeast(256)
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 12

        /** About 3,600 characters of Spanish: roughly 800 tokens with Gemma's tokenizer (4.55 chars per token). */
        val LONG_TEXT = (
            "El plazo para solicitar la devolución es de treinta días naturales desde la entrega del producto. " +
                "Transcurrido ese plazo, la empresa podrá rechazar la solicitud salvo que el defecto sea de fabricación. "
            ).repeat(18)
    }
}

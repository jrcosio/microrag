package com.lisdatasolutions.microrag.rag.runtime.litert

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.chat.EngineAnswerEngine
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.chat.AnswerEvent
import com.lisdatasolutions.microrag.domain.chat.AnswerPrompt
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import com.lisdatasolutions.microrag.rag.answer.PromptBuilder
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F20 T051 (research R19, contract G2–G3): Gemma with its vision encoder on the CPU reads a generated page with the
 * v5-visual and Balanced, and cites it as [1]. A generated page only: nothing of the user is read. Skipped without Gemma.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class GemmaVisionSmokeTest {

    private val context = TempRagIndex.context

    /** A white page that says, big, how many litres the tank holds. */
    private fun page(): ByteArray {
        val bitmap = Bitmap.createBitmap(543, 768, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint().apply { color = Color.BLACK; textSize = 44f; isAntiAlias = true }
            drawText("Capacidad del depósito:", 40f, 300f, paint)
            drawText("$LITRES litros", 40f, 380f, paint.apply { textSize = 72f; isFakeBoldText = true })
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    @Test
    fun gemmaReadsThePage_andCitesIt() = runBlocking<Unit> {
        val models = InstalledModels(context)
        assumeTrue("Gemma is not installed and prepared", models.isReady(ModelId.GEMMA4_E2B))
        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(CapturingLogger())
        try {
            val engine = EngineAnswerEngine(coordinator, LiteRtEngineFactory(context, dispatchers), models)
            val parts = PromptBuilder.buildVisual(
                question = "¿Cuántos litros caben en el depósito?",
                blocks = emptyList(),
                page = PromptBuilder.PageSource("ficha.pdf", 1),
                image = page(),
                previous = null,
            )
            val events = engine.generate(AnswerPrompt.Visual(parts, PageDetail.BALANCED.visualTokens)).toList()
            val answer = events.filterIsInstance<AnswerEvent.Token>().joinToString("") { it.delta }
            val finished = events.filterIsInstance<AnswerEvent.Finished>().single()
            // Numbers only: the answer itself stays out of the log.
            Log.i(TAG, "gemma-vision-smoke ttft_ms=${finished.ttftMs.toLong()} total_ms=${finished.totalMs.toLong()} chars=${answer.length}")

            assertWithMessage("the answer reads the page").that(answer).contains(LITRES)
            assertWithMessage("the answer cites the page").that(answer).contains("[1]")
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    private companion object {
        const val TAG = "MicroRAG-F20"
        const val LITRES = "738"
    }
}

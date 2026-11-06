package com.lisdatasolutions.microrag.data.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.rag.answer.PromptBuilder
import com.lisdatasolutions.microrag.rag.runtime.ActivationPrecision
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationPrompt
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.bench.BenchReport
import com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample
import com.lisdatasolutions.microrag.testing.bench.DevFiles
import com.lisdatasolutions.microrag.testing.bench.DeviceConditions
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T034 (research R8, contract B2, US5, FR-020): the reviewed questions of `offline/testset/numeric` (pushed to
 * `dev-bench/numeric.jsonl`), each with its fixed blocks from the public evaluation corpus, answered on the phone with
 * Gemma on its GPU: the file's activations (fp16) or FP32, and v4 or v4-cifras. The answers go to the report, and the
 * desktop's detector (`numeric.deformed`) counts the figures they deform; nothing of the user is read.
 *
 * Arguments: `f14.precision` (default or fp32) and `f14.template` (v4 or v4-cifras).
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class NumericFidelityBenchmarkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val fp32 = arguments.getString("f14.precision") == "fp32"
    private val template = arguments.getString("f14.template") ?: "v4"
    private val models = InstalledModels(context)

    @Test
    fun theFiguresOfTheAnswers() = runBlocking<Unit> {
        val file = File(DevFiles.benchDir(context), "numeric.jsonl")
        assumeTrue("Gemma is not ready or dev-bench has no numeric.jsonl", models.isReady(ModelId.GEMMA4_E2B) && file.isFile)
        val questions = file.readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        val dispatchers = AppDispatchers.create()
        val report = BenchReport(context, "numeric-$template-${if (fp32) "fp32" else "default"}", sqliteVector = "-")
        report.start = DeviceConditions.snapshot(context)
        val backend = models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
        val config = GenerationConfig(RagParameters.LLM_MAX_NUM_TOKENS, OUTPUT_TOKENS, greedy = true,
            activationPrecision = if (fp32) ActivationPrecision.FLOAT32 else null)
        val generator = LiteRtEngineFactory(context, dispatchers)
            .generator(ModelCatalog.byId(ModelId.GEMMA4_E2B), models.pathOf(ModelId.GEMMA4_E2B)!!, backend, config)
        report.model("GEMMA4_E2B", null, backend.name, if (fp32) "FLOAT32" else "default")
        val variant = report.variant("answers", mapOf("template" to template, "precision" to if (fp32) "fp32" else "default"))
        val answers = mutableListOf<kotlinx.serialization.json.JsonObject>()
        try {
            generator.initialize()
            questions.forEach { q ->
                val blocks = q.getValue("blocks").jsonArray.map { b ->
                    val o = b.jsonObject
                    PromptBuilder.Block(
                        o["document_name"]?.jsonPrimitive?.content, o["heading_path"]?.jsonPrimitive?.takeIf { it.isString }?.content,
                        o["page"]?.jsonPrimitive?.intOrNull, o.getValue("text").jsonPrimitive.content,
                    )
                }
                val prompt = PromptBuilder.build(q.getValue("question").jsonPrimitive.content, blocks, null, template = template)
                val text = StringBuilder()
                var ttft = 0L
                generator.generate(GenerationPrompt.Text(prompt)).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> text.append(event.textDelta)
                        is GenerationEvent.Finished -> ttft = event.stats.ttftWallMs
                    }
                }
                val id = q.getValue("id").jsonPrimitive.content
                variant.add(Sample("ttft", ttft.toDouble(), id, DeviceConditions.thermalNow(context)))
                answers += buildJsonObject {
                    put("id", id)
                    put("answer", text.toString())
                }
            }
        } finally {
            generator.close()
            dispatchers.close()
        }
        variant.extra["answers"] = JsonArray(answers)
        report.end = DeviceConditions.snapshot(context)
        report.write()
    }

    private companion object {
        const val OUTPUT_TOKENS = 256
    }
}

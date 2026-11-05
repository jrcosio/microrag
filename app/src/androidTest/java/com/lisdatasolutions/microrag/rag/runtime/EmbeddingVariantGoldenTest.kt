package com.lisdatasolutions.microrag.rag.runtime

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.models.Sha256
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F18 SC-002 (research R5): the 440M against the golden made with the 270M, and the same texts on both
 * variants on the phone. If their text vectors are the same, F19 can switch models without reindexing.
 * The golden's model hash is not compared, on purpose: this test measures another file. Integrity comes
 * from checking both files against ModelCatalog (finding M2). The figures go to
 * `files/reports/variant-golden-<ts>.json` (finding M3); logcat gets one line of numbers.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class EmbeddingVariantGoldenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val visual = ModelCatalog.byId(ModelId.EG2_440M)
    private val text = ModelCatalog.byId(ModelId.EG2_270M)

    private class Case(val id: String, val isQuery: Boolean, val text: String, val header: String, val vector: FloatArray)

    private fun jsonl(name: String): List<JsonObject> = assets.open(name).bufferedReader().use { it.readLines() }
        .filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun the440mGivesTheSameTextVectorsAsThe270m() = runBlocking<Unit> {
        val visualPath = File(context.noBackupFilesDir, "models/${visual.fileName}")
        val textPath = File(context.noBackupFilesDir, "models/${text.fileName}")
        assumeTrue("EmbeddingGemma 2 440M or 270M is not installed", visualPath.isFile && textPath.isFile)
        assertWithMessage("The 440M is not the catalog's file").that(visualPath.inputStream().use { Sha256.of(it) }).isEqualTo(visual.sha256)
        assertWithMessage("The 270M is not the catalog's file").that(textPath.inputStream().use { Sha256.of(it) }).isEqualTo(text.sha256)

        val texts = jsonl("embedding_texts.jsonl").associateBy { it.getValue("id").jsonPrimitive.content }
        val cases = jsonl("embedding_golden.jsonl").drop(1).map { entry ->
            val id = entry.getValue("id").jsonPrimitive.content
            Case(
                id = id,
                isQuery = entry.getValue("kind").jsonPrimitive.content == "query",
                text = texts.getValue(id).getValue("text").jsonPrimitive.content,
                header = texts.getValue(id)["header"]?.jsonPrimitive?.content.orEmpty(),
                vector = entry.getValue("v256").jsonArray.map { it.jsonPrimitive.float }.toFloatArray(),
            )
        }

        val dispatchers = AppDispatchers.create()
        val golden = mutableMapOf<ComputeBackend, List<Double>>()
        val identity: List<Double>
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            // One engine at a time, never two on the GPU (constitution IV).
            ComputeBackend.entries.forEach { backend ->
                embedAll(factory, visual.id, visualPath, backend, cases)?.let { vectors ->
                    golden[backend] = cases.indices.map { EmbeddingMath.cosine(vectors[it], cases[it].vector) }
                }
            }
            val fromVisual = checkNotNull(embedAll(factory, visual.id, visualPath, ComputeBackend.CPU, cases))
            val fromText = checkNotNull(embedAll(factory, text.id, textPath, ComputeBackend.CPU, cases))
            identity = cases.indices.map { EmbeddingMath.cosine(fromVisual[it], fromText[it]) }
        } finally {
            dispatchers.close()
        }

        val report = writeReport(golden, identity)
        Log.i(
            TAG,
            "variant-golden golden_cpu_min=%.6f identity_min=%.6f identity_mean=%.6f n=%d report=%s".format(
                Locale.ROOT, golden.getValue(ComputeBackend.CPU).min(), identity.min(), identity.average(), cases.size, report.name,
            ),
        )
        assertThat(golden.keys).contains(ComputeBackend.CPU)
        golden.forEach { (backend, cosines) ->
            assertWithMessage("$backend: 440M pairs below $MIN_GOLDEN against the golden").that(cosines.min()).isAtLeast(MIN_GOLDEN)
        }
        assertWithMessage("440M vs 270M on the phone").that(identity.min()).isAtLeast(MIN_IDENTITY)
    }

    /** Null when [backend] cannot run on this device (the emulator offers no usable GPU, F04 T039). */
    private suspend fun embedAll(factory: LiteRtEngineFactory, id: ModelId, path: File, backend: ComputeBackend, cases: List<Case>): List<FloatArray>? {
        if (backend == ComputeBackend.GPU && Build.HARDWARE == "ranchu") return null
        val embedder = try {
            factory.embedder(ModelCatalog.byId(id), path, backend).initializeOrClose { it.initialize() }
        } catch (e: Exception) {
            Log.w(TAG, "variant-golden $id backend=$backend not available: ${e::class.simpleName}")
            assertThat(backend).isNotEqualTo(ComputeBackend.CPU)
            return null
        }
        return embedder.use { engine ->
            cases.map { case -> if (case.isQuery) engine.embedQuery(case.text) else engine.embedDocument(case.text, case.header) }
        }
    }

    private fun writeReport(golden: Map<ComputeBackend, List<Double>>, identity: List<Double>): File {
        val json = buildJsonObject {
            put("schemaVersion", 1)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            put("model440Sha256", visual.sha256)
            put("model270Sha256", text.sha256)
            put("texts", identity.size)
            putJsonObject("golden") {
                golden.forEach { (backend, cosines) ->
                    putJsonObject(backend.name) {
                        put("min", cosines.min())
                        put("mean", cosines.average())
                    }
                }
            }
            putJsonObject("identity") {
                put("min", identity.min())
                put("mean", identity.average())
            }
        }
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val name = "variant-golden-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json"
        return File(dir, name).apply { writeText(json.toString()) }
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val MIN_GOLDEN = 0.99
        const val MIN_IDENTITY = 0.9999
    }
}

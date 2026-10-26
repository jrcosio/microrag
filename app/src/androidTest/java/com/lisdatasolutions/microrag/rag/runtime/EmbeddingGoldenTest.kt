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
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-002 / constitution VI: the phone embeds the 50 golden texts again, on CPU and on GPU, and each
 * pair must reach a cosine >= 0.99 with the desktop vector (D-030). Run it on every test phone and
 * on every LiteRT-LM update.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class EmbeddingGoldenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val model = ModelCatalog.byId(ModelId.EG2_270M)

    private class Case(val id: String, val isQuery: Boolean, val text: String, val header: String, val input: String, val vector: FloatArray)

    private class BackendResult(val backend: ComputeBackend, val cosines: Map<String, Double>) {
        val failing get() = cosines.filterValues { it < MIN_COSINE }.keys.sorted()
    }

    private fun jsonl(name: String): List<JsonObject> = assets.open(name).bufferedReader().use { it.readLines() }
        .filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun deviceVectorsMatchTheDesktopGolden() = runBlocking<Unit> {
        val path = File(context.noBackupFilesDir, "models/${model.fileName}")
        assumeTrue("EmbeddingGemma 2 270M is not installed", path.isFile)

        val golden = jsonl("embedding_golden.jsonl")
        val goldenSha = golden.first().getValue("meta").jsonObject.getValue("model_sha256").jsonPrimitive.content
        val deviceSha = path.inputStream().use { Sha256.of(it) }
        assertWithMessage("The device model does not match the golden model").that(deviceSha).isEqualTo(goldenSha)

        val texts = jsonl("embedding_texts.jsonl").associateBy { it.getValue("id").jsonPrimitive.content }
        val cases = golden.drop(1).map { entry ->
            val id = entry.getValue("id").jsonPrimitive.content
            val text = texts.getValue(id)
            Case(
                id = id,
                isQuery = entry.getValue("kind").jsonPrimitive.content == "query",
                text = text.getValue("text").jsonPrimitive.content,
                header = text["header"]?.jsonPrimitive?.content.orEmpty(),
                input = entry.getValue("input").jsonPrimitive.content,
                vector = entry.getValue("v256").jsonArray.map { it.jsonPrimitive.float }.toFloatArray(),
            )
        }
        cases.forEach { case ->
            val built = if (case.isQuery) EmbeddingPrefixes.query(case.text) else EmbeddingPrefixes.document(case.text, case.header)
            assertThat(built).isEqualTo(case.input)
        }

        val dispatchers = AppDispatchers.create()
        val results = try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            // One engine at a time, never two on the GPU (constitution IV).
            ComputeBackend.entries.mapNotNull { backend -> embedAll(factory, backend, path, cases) }
        } finally {
            dispatchers.close()
        }

        assertThat(results.map { it.backend }).contains(ComputeBackend.CPU)
        results.forEach { result ->
            assertWithMessage("${result.backend}: pairs below $MIN_COSINE").that(result.failing).isEmpty()
        }
    }

    /** Null when the backend cannot run the model on this device (reported as not available). */
    private suspend fun embedAll(factory: LiteRtEngineFactory, backend: ComputeBackend, path: File, cases: List<Case>): BackendResult? {
        // The emulator offers LiteRT-LM no usable GPU and its GPU init blocks in native code (F04 T039).
        if (backend == ComputeBackend.GPU && Build.HARDWARE == "ranchu") {
            Log.w(TAG, "golden backend=GPU not available: emulator")
            return null
        }
        val embedder = try {
            factory.embedder(model, path, backend).initializeOrClose { it.initialize() }
        } catch (e: Exception) {
            Log.w(TAG, "golden backend=$backend not available: ${e.message}")
            assertThat(backend).isNotEqualTo(ComputeBackend.CPU)
            return null
        }
        val cosines = embedder.use {
            cases.associate { case ->
                val vector = if (case.isQuery) it.embedQuery(case.text) else it.embedDocument(case.text, case.header)
                case.id to EmbeddingMath.cosine(vector, case.vector)
            }
        }
        val result = BackendResult(backend, cosines)
        Log.i(
            TAG,
            "golden backend=$backend min=%.6f mean=%.6f n=%d failing=%s".format(
                Locale.ROOT, cosines.values.min(), cosines.values.average(), cosines.size, result.failing,
            ),
        )
        return result
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val MIN_COSINE = 0.99
    }
}

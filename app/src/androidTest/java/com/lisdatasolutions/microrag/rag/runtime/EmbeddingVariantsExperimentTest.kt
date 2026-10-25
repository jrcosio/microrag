package com.lisdatasolutions.microrag.rag.runtime

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.AssetFeasibilityTexts
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F02 investigation for G36 (research R15): the three EmbeddingGemma 2 files showed identical text
 * latencies and CPU↔GPU cosines. Embeds the 20 test texts with each variant on CPU and logs the
 * cosine between variants, to tell whether they share the same text encoder.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class EmbeddingVariantsExperimentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun variants_textVectorsCompared() = runBlocking<Unit> {
        val ids = listOf(ModelId.EG2_270M, ModelId.EG2_440M, ModelId.EG2_740M)
        val paths = ids.associateWith { File(context.noBackupFilesDir, "models/${ModelCatalog.byId(it).fileName}") }
        assumeTrue("embedding models not installed", paths.values.all { it.isFile })
        val texts = AssetFeasibilityTexts(context, Dispatchers.IO).load()
        val dispatchers = AppDispatchers.create()
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            val vectors = ids.associateWith { id ->
                factory.embedder(ModelCatalog.byId(id), paths.getValue(id), ComputeBackend.CPU)
                    .initializeOrClose { it.initialize() }
                    .use { embedder ->
                        texts.queries.map { embedder.embedQuery(it) } +
                            texts.documents.map { embedder.embedDocument(it, texts.documentHeader) }
                    }
            }
            listOf(ModelId.EG2_440M, ModelId.EG2_740M).forEach { other ->
                val cosines = vectors.getValue(ModelId.EG2_270M).zip(vectors.getValue(other)) { a, b -> EmbeddingMath.cosine(a, b) }
                Log.i("MicroRAG", "experiment variants EG2_270M vs $other: min=${cosines.min()} mean=${cosines.average()} n=${cosines.size}")
            }
        } finally {
            dispatchers.close()
        }
    }
}

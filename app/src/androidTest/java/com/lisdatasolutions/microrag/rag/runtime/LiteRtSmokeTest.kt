package com.lisdatasolutions.microrag.rag.runtime

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.models.Sha256
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Early risk check (F02 plan, risk 1): the native library loads and EmbeddingGemma 2 270M embeds a
 * query on CPU with the API verified in research R1.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class LiteRtSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun embeddingGemma270m_embedsAQueryOnCpu() = runBlocking<Unit> {
        val file = ModelCatalog.byId(ModelId.EG2_270M)
        // The app must own dev-models; a folder created by adb is unreadable for the app (research R7).
        val devModels = File(context.getExternalFilesDir(null), "dev-models").apply { mkdirs() }
        val source = listOf(
            File(devModels, file.fileName),
            File(context.noBackupFilesDir, "models/${file.fileName}"),
        ).firstOrNull { it.isFile }
        assumeTrue("${file.fileName} is not provisioned", source != null)

        // Never load an unverified file: copy to a private scratch dir and check the hash first.
        val copy = File(context.cacheDir, "smoke/${file.fileName}")
        copy.parentFile!!.mkdirs()
        source!!.copyTo(copy, overwrite = true)
        val dispatchers = AppDispatchers.create()
        try {
            assertThat(copy.inputStream().use { Sha256.of(it) }).isEqualTo(file.sha256)

            val factory = LiteRtEngineFactory(context, dispatchers)
            val metadata = factory.metadata(copy)
            val initStart = System.nanoTime()
            val embedder = factory.embedder(file, copy, ComputeBackend.CPU).initializeOrClose { it.initialize() }
            val initMs = (System.nanoTime() - initStart) / 1_000_000
            try {
                val embedStart = System.nanoTime()
                val vector = embedder.embedQuery("¿Qué dimensión tienen los embeddings del índice?")
                val embedMs = (System.nanoTime() - embedStart) / 1_000_000

                assertThat(vector.size).isEqualTo(EmbeddingMath.DIMENSIONS)
                assertThat(EmbeddingMath.l2Norm(vector)).isWithin(1e-5).of(1.0)
                assertThat(embedder.rawDimensions).isAtLeast(EmbeddingMath.DIMENSIONS)
                Log.i(
                    "MicroRAG",
                    "smoke: maxContext=${metadata.maxContextTokens} backends=${metadata.supportedBackends} " +
                        "raw=${embedder.rawDimensions} initMs=$initMs firstEmbedMs=$embedMs",
                )
            } finally {
                embedder.close()
            }
        } finally {
            copy.delete()
            dispatchers.close()
        }
    }
}

package com.lisdatasolutions.microrag.rag.runtime

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
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
 * F03 SC-010: after the on-device test of setup, Gemma starts from the kernel cache it left behind.
 * Initializes the generic file on the GPU with the probe configuration, without clearing the cache.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class GemmaWarmInitTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun gemmaInitializesFromTheCacheLeftBySetup() = runBlocking<Unit> {
        val file = ModelCatalog.byId(ModelId.GEMMA4_E2B)
        val path = File(context.noBackupFilesDir, "models/${file.fileName}")
        assumeTrue("${file.fileName} is not installed", path.isFile)
        val dispatchers = AppDispatchers.create()
        try {
            val generator = LiteRtEngineFactory(context, dispatchers)
                .generator(file, path, ComputeBackend.GPU, GenerationConfig(maxNumTokens = 4096, maxOutputTokens = 8, greedy = true))
            val started = System.nanoTime()
            generator.initializeOrClose { it.initialize() }.use {
                val seconds = (System.nanoTime() - started) / 1e9
                Log.i("MicroRAG", "warm init GEMMA4_E2B GPU: %.2f s".format(seconds))
                assertThat(seconds).isLessThan(10.0)
            }
        } finally {
            dispatchers.close()
        }
    }
}

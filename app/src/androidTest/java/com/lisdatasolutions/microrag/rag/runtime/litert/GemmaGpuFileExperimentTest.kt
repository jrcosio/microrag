package com.lisdatasolutions.microrag.rag.runtime.litert

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F02 investigation (research R14): the GPU-specific Gemma file produced incoherent text on the
 * Adreno 642L with default settings. Runs it with each activation data type and logs the start of
 * the answer to the synthetic prompt, so the cause can be told apart from a configuration issue.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class GemmaGpuFileExperimentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun gpuFile_outputPerActivationType() = runBlocking<Unit> {
        val file = ModelCatalog.byId(ModelId.GEMMA4_E2B_GPU)
        val path = File(context.noBackupFilesDir, "models/${file.fileName}")
        assumeTrue("${file.fileName} is not installed", path.isFile)
        val prompt = context.assets.open("feasibility/prompt_es.txt").bufferedReader().use { it.readText() }

        listOf<ActivationDataType?>(null, ActivationDataType.FLOAT32, ActivationDataType.FLOAT16).forEach { type ->
            val outcome = runCatching {
                Engine(
                    EngineConfig(
                        modelPath = path.path,
                        backend = Backend.GPU(),
                        maxNumTokens = 4096,
                        cacheDir = File(context.cacheDir, "litertlm").path,
                        activationDataType = type,
                    ),
                ).use { engine ->
                    engine.initialize()
                    engine.createConversation(
                        ConversationConfig(samplerConfig = SamplerConfig(1, 1.0, 0.0, 0), maxOutputToken = 48),
                    ).use { conversation ->
                        conversation.sendMessage(prompt).contents.contents
                            .filterIsInstance<Content.Text>().joinToString("") { it.text }
                    }
                }
            }
            Log.i("MicroRAG", "experiment gpu-file activation=$type -> ${outcome.getOrElse { "ERROR ${it.message}" }.replace('\n', ' ').take(200)}")
        }
    }
}

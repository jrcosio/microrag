package com.lisdatasolutions.microrag.rag.runtime.litert

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BackendType
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Modality
import com.google.ai.edge.litertlm.ModelInfo
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.ModelMetadata
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import java.io.File

/** The only entry point to LiteRT-LM (constitution II): everything else sees [EngineFactory]. */
class LiteRtEngineFactory(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : EngineFactory {

    private val compiledCacheDir: File
        get() = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    override fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder =
        LiteRtTextEmbedder(file.id, path, backend, compiledCacheDir, dispatchers.embeddings)

    override fun generator(
        file: ModelFile,
        path: File,
        backend: ComputeBackend,
        config: GenerationConfig,
    ): TextGenerator = LiteRtTextGenerator(file.id, path, backend, compiledCacheDir, config, dispatchers.llm)

    /** Blocking native read of the file header; call it off the main thread. */
    override fun metadata(path: File): ModelMetadata = ModelInfo.from(path.path).use { info ->
        ModelMetadata(
            maxContextTokens = info.maxContextTokens().takeIf { it > 0 },
            supportedBackends = info.supportedBackends(Modality.TEXT).mapNotNull { it.toComputeBackend() },
        )
    }

    override fun clearCompiledCache() {
        File(context.cacheDir, CACHE_DIR).deleteRecursively()
    }

    // Experimental runtime flag; only the debug diagnostics turn it on (F02 research R4).
    @OptIn(ExperimentalApi::class)
    override var benchmarkStatsEnabled: Boolean
        get() = ExperimentalFlags.enableBenchmark
        set(value) {
            ExperimentalFlags.enableBenchmark = value
        }

    private companion object {
        const val CACHE_DIR = "litertlm"
    }
}

internal fun ComputeBackend.toLiteRt(): Backend = when (this) {
    ComputeBackend.CPU -> Backend.CPU()
    ComputeBackend.GPU -> Backend.GPU()
}

private fun BackendType.toComputeBackend(): ComputeBackend? = when (this) {
    BackendType.CPU -> ComputeBackend.CPU
    BackendType.GPU -> ComputeBackend.GPU
    BackendType.NPU -> null
}

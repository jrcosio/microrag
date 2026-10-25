package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFile
import java.io.File

/** Creates engines for verified model files. Engines are returned uninitialized. */
interface EngineFactory {
    fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder

    fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig): TextGenerator

    /** Metadata declared inside the model file, read without loading an engine. */
    fun metadata(path: File): ModelMetadata

    /** Deletes compiled kernels so the next initialization is a cold one. */
    fun clearCompiledCache()

    /** Enables the runtime's internal timing (TTFT, prefill, decode) for diagnostics. */
    var benchmarkStatsEnabled: Boolean
}

data class ModelMetadata(
    val maxContextTokens: Int?,
    val supportedBackends: List<ComputeBackend>,
)

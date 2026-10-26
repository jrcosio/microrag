package com.lisdatasolutions.microrag.data.indexing

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.ingest.Chunk
import com.lisdatasolutions.microrag.rag.ingest.Chunker
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.runtime.litert.toLiteRt
import java.io.File

/** Shared setup of the F07 device probes (research R5, R6): the installed model and real chunks. */
internal object EmbeddingProbes {

    const val TAG = "MicroRAG"
    const val MAX_INPUT_LENGTH = 512

    val model = ModelCatalog.byId(ModelId.EG2_270M)

    val isEmulator: Boolean get() = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"

    fun modelFile(context: Context) = File(context.noBackupFilesDir, "models/${model.fileName}")

    /** Backends worth probing here: the emulator's GPU init blocks in native code (F04 report §6). */
    val backends: List<ComputeBackend> get() = if (isEmulator) listOf(ComputeBackend.CPU) else ComputeBackend.entries

    /** A raw LiteRT engine, configured as LiteRtTextEmbedder does, to see its own exceptions. */
    fun engine(context: Context, backend: ComputeBackend): EmbeddingEngine = EmbeddingEngine(
        EmbeddingEngineConfig(
            modelPath = modelFile(context).path,
            backend = backend.toLiteRt(),
            cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.path,
            maxInputLength = MAX_INPUT_LENGTH,
        ),
    ).also { it.initialize() }

    /** The first [count] chunks of text_200_pages.pdf, as the app chunks them. */
    fun chunks(context: Context, testContext: Context, count: Int): List<Chunk> {
        val file = File(context.cacheDir, "probe-200.pdf")
        testContext.assets.open("pdf/text_200_pages.pdf").use { input -> file.outputStream().use(input::copyTo) }
        val out = ArrayList<Chunk>()
        val session = Chunker().Session()
        try {
            DocumentExtractor(PlatformPdfOpener()).extract(file, DocumentFormat.PDF) { block -> session.add(block, out::add) }
            session.finish(out::add)
        } finally {
            file.delete()
        }
        return out.take(count)
    }
}

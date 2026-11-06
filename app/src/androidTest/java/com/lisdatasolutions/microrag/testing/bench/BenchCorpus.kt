package com.lisdatasolutions.microrag.testing.bench

import android.content.Context
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.testing.CorpusIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File

/**
 * F14 (research R5): the evaluation corpus indexed once per text embedder in `cacheDir/bench`, so the benches that
 * answer over it do not index its 1,913 chunks each time (about 10 minutes on the reference phone). It is a test
 * database, never the user's; `BenchCleanupTest` deletes it with the banks.
 */
object BenchCorpus {

    /** The corpus index, built now when it is missing or half built; the caller closes it, and the file stays. */
    suspend fun open(
        context: Context,
        root: File,
        pdf: PlatformPdfOpener,
        coordinator: EngineCoordinator,
        factory: EngineFactory,
        model: File,
        embedder: ModelId,
    ): TempRagIndex {
        val db = File(context.cacheDir, "${BenchBank.CACHE}/corpus-${embedder.name}.db").apply { parentFile?.mkdirs() }
        val done = File(db.path + ".done")
        if (!done.isFile) listOf("", "-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
        val temp = TempRagIndex(dbFile = db, initialTextEmbedder = embedder, keepFile = true)
        if (!done.isFile) {
            CorpusIndex(temp, root, pdf).build(coordinator, factory, model, embedder)
            done.writeText("built")
        }
        return temp
    }
}

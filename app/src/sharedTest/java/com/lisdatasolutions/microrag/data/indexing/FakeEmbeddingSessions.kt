package com.lisdatasolutions.microrag.data.indexing

import com.lisdatasolutions.microrag.data.device.ThermalLevel
import com.lisdatasolutions.microrag.data.device.ThermalStatusSource
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.rag.runtime.DocumentEmbedding
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [EmbeddingSessions] over a [FakeTextEmbedder], for JVM and instrumented tests. With [ready] false
 * the model counts as missing, so a runner stops after chunking (F06 behaviour).
 */
class FakeEmbeddingSessions(
    val embedder: FakeTextEmbedder = FakeTextEmbedder(),
    @Volatile var ready: Boolean = true,
) : EmbeddingSessions {

    @Volatile
    var opened = 0
        private set

    override suspend fun modelReady(): Boolean = ready

    override suspend fun <R> use(block: suspend (ChunkEmbedder) -> R): R {
        opened++
        return block(
            object : ChunkEmbedder {
                override val backend: ComputeBackend = embedder.backend

                override suspend fun embed(content: String, header: String): DocumentEmbedding =
                    embedder.embedDocumentFitting(content, header)
            },
        )
    }

    companion object {
        /** No embedding model: the runner only chunks, as in F06. */
        fun withoutModel() = FakeEmbeddingSessions(ready = false)
    }
}

/** A [ThermalStatusSource] the test drives. */
class FakeThermalStatusSource(initial: ThermalLevel = ThermalLevel.NORMAL) : ThermalStatusSource {
    override val level = MutableStateFlow(initial)
}

/** An [IndexingAlarm] that records its calls in order: `true` to arm, `false` to cancel. */
class RecordingIndexingAlarm : IndexingAlarm {
    val calls = CopyOnWriteArrayList<Boolean>()

    /** Whether a wake-up is set after the last call. */
    val armed: Boolean get() = calls.lastOrNull() == true

    override fun arm() {
        calls += true
    }

    override fun cancel() {
        calls += false
    }
}

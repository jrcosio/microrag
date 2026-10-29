package com.lisdatasolutions.microrag.data.retrieval

import com.lisdatasolutions.microrag.data.indexing.FakeTextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.QueryEmbedding
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic [QueryEmbedder] for JVM and instrumented tests (F10): [engine] embeds on [context], as the
 * real one does on the embeddings thread, so the lexical search can run meanwhile.
 */
class FakeQueryEmbedder(
    val engine: FakeTextEmbedder = FakeTextEmbedder(),
    private val context: CoroutineContext = Dispatchers.IO,
) : QueryEmbedder {

    @Volatile
    var isReady = true

    val readyCalls = AtomicInteger()
    val embedCalls = AtomicInteger()

    override suspend fun ready(): Boolean {
        readyCalls.incrementAndGet()
        return isReady
    }

    override suspend fun embed(question: String, previous: String?): QueryEmbedding = withContext(context) {
        embedCalls.incrementAndGet()
        engine.embedQueryFitting(question, previous).also { loaded = true }
    }

    @Volatile
    override var loaded: Boolean = false
}

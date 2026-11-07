package com.lisdatasolutions.microrag.data.rag

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Whether an indexing batch is running; the INT8 copy waits for its end (F14 contract I4). */
fun interface IngestActivity {
    suspend fun running(): Boolean
}

/**
 * F14 (research R3, contract I4): keeps the INT8 copy of the chunk vectors in step with the chunks. Every change
 * asks for a refresh; requests closer than [debounceMs] run once, never during a batch (its end asks again), and the
 * refresh rebuilds a stale copy and preloads it on the index's connection. A failure only leaves the search exact.
 * Below [minChunks] searchable chunks nothing is built, so the exact search stays (constitution IV); the same for
 * the page vectors below [minPages] pages (research R10).
 */
class QuantizedIndexMaintainer(
    private val index: RagIndex,
    private val ingest: IngestActivity,
    scope: CoroutineScope,
    private val logger: AppLogger,
    private val minChunks: () -> Int = { RagParameters.INT8_MIN_CHUNKS },
    private val minPages: () -> Int = { RagParameters.PAGES_INT8_MIN },
    private val debounceMs: Long = RagParameters.QUANT_REBUILD_DEBOUNCE_MS,
) {
    enum class Reason { STARTUP, INGEST_FINISHED, LIBRARY_CHANGED }

    private val requests = Channel<Reason>(Channel.CONFLATED)

    init {
        scope.launch {
            while (true) {
                var reason = requests.receive()
                while (true) reason = withTimeoutOrNull(debounceMs) { requests.receive() } ?: break
                refresh(reason)
            }
        }
    }

    fun request(reason: Reason) {
        requests.trySend(reason)
    }

    private suspend fun refresh(reason: Reason) {
        try {
            if (ingest.running()) return
            val state = index.refreshQuantized(minChunks(), minPages())
            logger.i(TAG) {
                "int8 refresh reason=$reason usable=${state.usable} chunks=${state.searchableChunks} " +
                    "pagesUsable=${state.pagesUsable} pages=${state.pages}"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, e) { "int8 refresh failed reason=$reason; the search stays exact" }
        }
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}

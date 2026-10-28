package com.lisdatasolutions.microrag.data.ingest

import android.os.SystemClock
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.device.ThermalLevel
import com.lisdatasolutions.microrag.data.device.ThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.ChunkEmbedder
import com.lisdatasolutions.microrag.data.indexing.EmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.IngestRunStore
import com.lisdatasolutions.microrag.data.indexing.RunPauseCause
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportLimits
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DocumentTitle
import com.lisdatasolutions.microrag.domain.rag.IndexStorageFullException
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.StoredDocument
import com.lisdatasolutions.microrag.rag.ingest.Chunk
import com.lisdatasolutions.microrag.rag.ingest.Chunker
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.PdfDamagedException
import com.lisdatasolutions.microrag.rag.ingest.PdfEncryptedException
import com.lisdatasolutions.microrag.rag.ingest.PdfUnavailableException
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingEngineException
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingInputTooLongException
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Indexes the documents of one processing request and of the run (F07 research R8–R10, rules N1–N17).
 *
 * - **Phase 1** (F06): every pending document is extracted and chunked to count its chunks, and ends
 *   `parsed`, or `error` with a code. The run's total is known in seconds.
 * - **Phase 2**: document by document, the copy is chunked again (deterministic) while the chunks are
 *   embedded on a held engine and stored in batches of 64. Stored chunks that match are skipped, so a
 *   resumed document goes on where it stopped. A document becomes `done` only when all its chunks are
 *   stored, so searches never see it half indexed.
 *
 * Heat, a full disk, a missing model and the user's pause stop it safely; errors stay per document.
 */
class IngestRunner(
    private val index: RagIndex,
    private val extractor: DocumentExtractor,
    private val sessions: EmbeddingSessions,
    private val runStore: IngestRunStore,
    private val thermal: ThermalStatusSource,
    private val freeBytes: () -> Long,
    private val io: CoroutineDispatcher,
    private val logger: AppLogger,
    private val newChunker: () -> Chunker = { Chunker() },
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val progressIntervalMs: Long = PROGRESS_INTERVAL_MS,
) {

    private sealed interface Outcome {
        data class Parsed(val chunks: Int, val skippedPages: Int?) : Outcome
        data class Failed(val error: IngestError) : Outcome
    }

    /** Leaves the embedding session so its engine is released while the phone cools down. */
    private class TooHot : Exception()

    /** No room for the next batch (research R10). */
    private class NoSpace : Exception()

    /** The document was removed or retried while it was being embedded (rule N3). */
    private class DocumentGone : Exception()

    suspend fun process(documentIds: List<Long>, publish: suspend (IngestProgress) -> Unit) {
        coroutineScope {
            val heat = MutableStateFlow(ThermalLevel.NORMAL)
            val watcher = launch { thermal.level.collect { heat.value = it } }
            val progress = Progress(this, publish)
            try {
                Run(documentIds, heat, progress).execute()
            } finally {
                watcher.cancel()
                progress.stop()
            }
        }
    }

    /** The documents of one call: the request first, then the rest of the run (rule N16). */
    private inner class Run(
        private val requested: List<Long>,
        private val heat: MutableStateFlow<ThermalLevel>,
        private val progress: Progress,
    ) {
        private var current: Long? = null
        private val rate = Rate()

        suspend fun execute() {
            if (runStore.run.first().paused) return
            parsePending()
            if (nextToEmbed() == null) {
                finishRun()
                return
            }
            if (!sessions.modelReady()) {
                runStore.edit { it.pausedBy(RunPauseCause.MODEL_MISSING) }
                logger.i(TAG) { "ingest paused: embedding model missing" }
                return
            }
            while (nextToEmbed() != null) {
                rate.reset()
                current = null
                try {
                    sessions.use { embedder -> embedAll(embedder) }
                } catch (e: TooHot) {
                    waitForCooling()
                } catch (e: NoSpace) {
                    runStore.edit { it.pausedBy(RunPauseCause.NO_SPACE) }
                    logger.i(TAG) { "ingest paused: no space" }
                    return
                } catch (e: EmbeddingEngineException) {
                    // Also when the engine never loaded: the next document fails, so the run moves on.
                    (current ?: nextToEmbed()?.id)?.let { fail(it, IngestError.EMBEDDING_FAILED) }
                }
                if (runStore.run.first().paused) return
            }
            finishRun()
        }

        private suspend fun members(): List<Long> = (requested + runStore.run.first().documentIds).distinct()

        private suspend fun parsePending() {
            for (id in members()) {
                coroutineContext.ensureActive()
                parse(id, progress)
            }
        }

        /** What the run's other documents have stored, and the run's total, as the Index screen counts them (D-042). */
        private suspend fun runCounts(current: Long): Pair<Int, Int?> {
            val documents = members().mapNotNull { index.document(it) }.filter { it.status != DocumentStatus.ERROR }
            val total = if (documents.any { it.status == DocumentStatus.PENDING }) null else documents.sumOf { it.chunkCount ?: 0 }
            val elsewhere = index.storedChunkCounts(documents.map { it.id }.filter { it != current }).values.sum()
            return elsewhere to total
        }

        private suspend fun nextToEmbed(): StoredDocument? = members().firstNotNullOfOrNull { id ->
            index.document(id)?.takeIf { it.status == DocumentStatus.PARSED || it.status == DocumentStatus.EMBEDDING }
        }

        private suspend fun embedAll(embedder: ChunkEmbedder) {
            while (true) {
                if (runStore.run.first().paused) return
                parsePending()
                val document = nextToEmbed() ?: return
                embedDocument(document, embedder, restarted = false)
            }
        }

        private suspend fun embedDocument(document: StoredDocument, embedder: ChunkEmbedder, restarted: Boolean) {
            val id = document.id
            current = id
            val format = DocumentFormat.fromMime(document.mimeType)
            val file = document.localPath?.let(::File)
            if (format == null || file == null) {
                fail(id, IngestError.EXTRACTION_FAILED)
                return
            }
            if (document.status != DocumentStatus.EMBEDDING) index.updateStatus(id, DocumentStatus.EMBEDDING)
            val stored = index.storedChunkContents(id)
            val total = document.chunkCount
            val title = DocumentTitle.of(document.displayName)
            val (storedElsewhere, runTotal) = runCounts(id)
            val started = clock()
            logger.i(TAG) { "ingest embedding id=$id stored=${stored.size} total=$total backend=${embedder.backend}" }
            val batch = ArrayList<ChunkInput>(BATCH)
            var produced = 0
            var done = stored.size
            var cuts = 0
            var mismatch = false

            fun report(waiting: IngestProgress.WaitReason? = null) = IngestProgress(
                id, IngestProgress.Stage.EMBEDDING, chunksDone = done, chunksTotal = total,
                chunksPerSecond = rate.steady(), backend = embedder.backend, waiting = waiting,
                runChunksDone = storedElsewhere + done, runChunksTotal = runTotal,
            )

            progress.now(report())
            try {
                chunksOf(file, format) { chunk ->
                    produced++
                    val have = stored[chunk.ordinal]
                    if (have != null) {
                        mismatch = have != chunk.content
                        return@chunksOf !mismatch
                    }
                    if (heat.value == ThermalLevel.SEVERE_OR_WORSE) {
                        progress.now(report(IngestProgress.WaitReason.THERMAL))
                        throw TooHot()
                    }
                    val embedding = embedder.embed(chunk.content, header(title, chunk))
                    cuts += embedding.cuts
                    batch += chunk.toInput(embedding.vector)
                    done++
                    progress.offer(report())
                    if (batch.size == BATCH) {
                        flush(id, batch)
                        rate.batchStored(BATCH)
                        progress.now(report())
                    }
                    true
                }
            } catch (e: EmbeddingInputTooLongException) {
                fail(id, IngestError.EMBEDDING_FAILED)
                return
            } catch (e: DocumentGone) {
                return
            } catch (e: PdfEncryptedException) {
                fail(id, IngestError.ENCRYPTED)
                return
            } catch (e: PdfDamagedException) {
                fail(id, IngestError.DAMAGED)
                return
            } catch (e: PdfUnavailableException) {
                fail(id, IngestError.PDF_UNAVAILABLE)
                return
            } catch (e: java.io.IOException) {
                fail(id, IngestError.EXTRACTION_FAILED)
                return
            } finally {
                if (batch.isNotEmpty()) withContext(NonCancellable) { withTimeoutOrNull(FLUSH_TIMEOUT_MS) { flushQuietly(id, batch) } }
            }

            if (mismatch || stored.keys.any { it >= produced }) {
                // Chunked differently from what is stored (another app version): never mixed (FR-009).
                logger.w(TAG) { "ingest chunks differ id=$id; indexing it again from the start" }
                index.deleteChunks(id)
                if (!restarted) embedDocument(index.document(id) ?: return, embedder, restarted = true) else fail(id, IngestError.INCOMPLETE)
                return
            }
            close(id, produced, cuts, started, embedder.backend, storedElsewhere to runTotal)
        }

        /** Rule N1: done only with exactly the chunking's chunks, ordinals 0..n-1; a shadow is published instead (F08). */
        private suspend fun close(id: Long, produced: Int, cuts: Int, started: Long, backend: ComputeBackend, run: Pair<Int, Int?>) {
            progress.now(
                IngestProgress(
                    id, IngestProgress.Stage.CLOSING, chunksDone = produced, chunksTotal = produced, backend = backend,
                    runChunksDone = run.first + produced, runChunksTotal = run.second,
                ),
            )
            val document = index.document(id)?.takeIf { it.status == DocumentStatus.EMBEDDING } ?: return
            val stored = index.storedChunkCounts(listOf(id)).getValue(id)
            when {
                produced == 0 || stored != produced -> fail(id, IngestError.INCOMPLETE)
                // F08 rule L7: a reindex shadow replaces its original's chunks, never becomes a document of its own.
                document.replacesId != null -> {
                    val published = index.publishReindex(id)
                    if (published == null) {
                        logger.i(TAG) { "ingest reindex gone id=$id" }
                    } else {
                        logger.i(TAG) { "ingest reindex published id=$id original=${document.replacesId} chunks=$published in ${clock() - started} ms" }
                    }
                }
                else -> {
                    index.updateStatus(id, DocumentStatus.DONE, chunkCount = produced)
                    logger.i(TAG) { "ingest done id=$id chunks=$produced cuts=$cuts backend=$backend in ${clock() - started} ms" }
                }
            }
        }

        private suspend fun flush(id: Long, batch: MutableList<ChunkInput>) {
            if (freeBytes() < ImportLimits.SPACE_MARGIN_BYTES) throw NoSpace()
            if (index.document(id)?.status != DocumentStatus.EMBEDDING) throw DocumentGone()
            try {
                index.insertChunks(id, batch.toList())
            } catch (e: IndexStorageFullException) {
                throw NoSpace()
            } catch (e: IllegalArgumentException) {
                throw DocumentGone()
            }
            batch.clear()
        }

        /** Rule N4: what was already embedded is kept when the work stops, if it can be. */
        private suspend fun flushQuietly(id: Long, batch: MutableList<ChunkInput>) {
            try {
                flush(id, batch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG) { "ingest partial batch not stored id=$id ${e.javaClass.simpleName}" }
            }
        }

        private suspend fun waitForCooling() {
            logger.i(TAG) { "ingest paused: too hot" }
            heat.first { it != ThermalLevel.SEVERE_OR_WORSE }
            logger.i(TAG) { "ingest resumed: cooler" }
        }

        private suspend fun fail(id: Long, error: IngestError) {
            if (index.document(id) == null) return
            index.updateStatus(id, DocumentStatus.ERROR, error = error.code)
            logger.i(TAG) { "ingest failed id=$id ${error.code}" }
        }

        /** Rule N17: the run empties once none of its documents is left to finish. */
        private suspend fun finishRun() {
            val run = runStore.run.first()
            val finished = run.documentIds.filter { id ->
                index.document(id)?.status.let { it == null || it !in UNFINISHED }
            }.toSet()
            runStore.edit { latest -> if (latest.documentIds.all { it in finished }) latest.without(latest.documentIds) else latest }
        }
    }

    /** Phase 1 for one document, exactly as F06 does it (research R7). */
    private suspend fun parse(id: Long, progress: Progress) {
        val document = index.document(id) ?: return
        if (document.status != DocumentStatus.PENDING) return
        val started = clock()
        val format = DocumentFormat.fromMime(document.mimeType)
        val file = document.localPath?.let(::File)
        val outcome = if (format == null || file == null) {
            Outcome.Failed(IngestError.EXTRACTION_FAILED)
        } else {
            progress.now(IngestProgress(id, IngestProgress.Stage.EXTRACTING, page = 0, pages = document.pageCount))
            count(id, file, format, progress)
        }
        // Removed or retried meanwhile: nothing to write (F06 contracts I7).
        if (index.document(id)?.status != DocumentStatus.PENDING) return
        when (outcome) {
            is Outcome.Parsed -> {
                index.updateStatus(id, DocumentStatus.PARSED, chunkCount = outcome.chunks, skippedPages = outcome.skippedPages)
                logger.i(TAG) { "ingest parsed id=$id chunks=${outcome.chunks} skipped=${outcome.skippedPages} in ${clock() - started} ms" }
            }
            is Outcome.Failed -> {
                index.updateStatus(id, DocumentStatus.ERROR, error = outcome.error.code)
                logger.i(TAG) { "ingest failed id=$id ${outcome.error.code}" }
            }
        }
    }

    private suspend fun count(id: Long, file: File, format: DocumentFormat, progress: Progress): Outcome = try {
        withContext(io) {
            // A missing private copy cannot be extracted; checked here, off the caller's thread.
            if (!file.isFile) return@withContext Outcome.Failed(IngestError.EXTRACTION_FAILED)
            val job = coroutineContext.job
            val session = newChunker().Session()
            var chunks = 0
            val summary = extractor.extract(
                file,
                format,
                onPage = { done, total ->
                    job.ensureActive()
                    progress.offer(IngestProgress(id, IngestProgress.Stage.EXTRACTING, done, total))
                },
            ) { block ->
                job.ensureActive()
                session.add(block) { chunks++ }
            }
            if (summary.blocks == 0) {
                Outcome.Failed(IngestError.NO_TEXT)
            } else {
                progress.now(IngestProgress(id, IngestProgress.Stage.CHUNKING))
                session.finish { chunks++ }
                Outcome.Parsed(chunks, summary.skippedPages)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: PdfEncryptedException) {
        Outcome.Failed(IngestError.ENCRYPTED)
    } catch (e: PdfDamagedException) {
        Outcome.Failed(IngestError.DAMAGED)
    } catch (e: PdfUnavailableException) {
        Outcome.Failed(IngestError.PDF_UNAVAILABLE)
    } catch (e: Exception) {
        logger.w(TAG) { "ingest extraction failed id=$id ${e.javaClass.simpleName}" }
        Outcome.Failed(IngestError.EXTRACTION_FAILED)
    }

    /**
     * Streams the chunks of [file] to [consume] while extraction runs on [io]: extraction is blocking
     * and embedding suspends, so a small channel joins them and memory stays bounded. [consume] returns
     * false to stop early.
     */
    private suspend fun chunksOf(file: File, format: DocumentFormat, consume: suspend (Chunk) -> Boolean) = coroutineScope {
        val channel = Channel<Chunk>(CHANNEL_CAPACITY)
        val producer = launch(io) {
            try {
                if (!file.isFile) throw java.io.IOException("private copy missing")
                val session = newChunker().Session()
                val send: (Chunk) -> Unit = { channel.trySendBlocking(it).getOrThrow() }
                extractor.extract(file, format) { block -> session.add(block, send) }
                session.finish(send)
                channel.close()
            } catch (e: Throwable) {
                channel.close(e)
            }
        }
        try {
            for (chunk in channel) {
                // Receiving a buffered chunk does not suspend, so a pause would go unnoticed without this.
                coroutineContext.ensureActive()
                if (!consume(chunk)) break
            }
        } finally {
            channel.cancel()
            producer.cancel()
        }
    }

    private fun header(title: String, chunk: Chunk): String =
        listOf(title, chunk.headingPath).filter { it.isNotBlank() }.joinToString(" › ")

    private fun Chunk.toInput(vector: FloatArray) = ChunkInput(
        ordinal = ordinal,
        headingPath = headingPath.ifBlank { null },
        page = page,
        content = content,
        // As offline/eval/chunker.py: UTF-16 length over the measured characters per token.
        tokenEstimate = ceil(content.length / RagParameters.CHARS_PER_TOKEN).toInt(),
        embedding = vector,
    )

    /** Publishes stage changes at once and fast-changing counts at most every [progressIntervalMs]. */
    private inner class Progress(scope: CoroutineScope, private val publish: suspend (IngestProgress) -> Unit) {
        private val latest = AtomicReference<IngestProgress?>(null)
        private val job = scope.launch {
            while (isActive) {
                delay(progressIntervalMs)
                latest.getAndSet(null)?.let { publish(it) }
            }
        }

        fun offer(progress: IngestProgress) = latest.set(progress)

        suspend fun now(progress: IngestProgress) {
            latest.set(null)
            publish(progress)
        }

        fun stop() = job.cancel()
    }

    /** Chunks per second of the last two stored batches, only when they agree (research R8, FR-023). */
    private inner class Rate {
        private val batches = ArrayDeque<Pair<Long, Int>>()
        private var since = clock()

        fun reset() {
            batches.clear()
            since = clock()
        }

        fun batchStored(chunks: Int) {
            val now = clock()
            batches.addLast((now - since) to chunks)
            since = now
            while (batches.size > 2) batches.removeFirst()
        }

        fun steady(): Double? {
            if (batches.size < 2) return null
            val rates = batches.map { (ms, chunks) -> if (ms > 0) chunks * 1000.0 / ms else return null }
            val (a, b) = rates
            if (abs(a - b) / maxOf(a, b) >= STEADY_SPREAD) return null
            return batches.sumOf { it.second } * 1000.0 / batches.sumOf { it.first }
        }
    }

    companion object {
        /** Chunks stored per transaction (FR-014). */
        const val BATCH = 64
        private const val TAG = "MicroRAG"
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val CHANNEL_CAPACITY = 16
        private const val FLUSH_TIMEOUT_MS = 5_000L
        private const val STEADY_SPREAD = 0.25
        private val UNFINISHED = setOf(DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.EMBEDDING)
    }
}

package com.lisdatasolutions.microrag.data.ingest

import android.os.SystemClock
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.rag.ingest.Chunker
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.PdfDamagedException
import com.lisdatasolutions.microrag.rag.ingest.PdfEncryptedException
import com.lisdatasolutions.microrag.rag.ingest.PdfUnavailableException
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extracts and chunks the documents of one processing request, one at a time (F06 research R7,
 * contracts I3–I6, I10). A document ends `parsed` with its chunk count and skipped pages, or `error`
 * with a code. Interrupted work leaves it `pending`; running it again gives the same result, because
 * extraction and chunking are deterministic. F07 adds embeddings and storage here.
 */
class IngestRunner(
    private val index: RagIndex,
    private val extractor: DocumentExtractor,
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

    suspend fun process(documentIds: List<Long>, publish: suspend (IngestProgress) -> Unit) {
        for (id in documentIds) {
            coroutineContext.ensureActive()
            processOne(id, publish)
        }
    }

    private suspend fun processOne(id: Long, publish: suspend (IngestProgress) -> Unit) {
        val document = index.document(id) ?: return
        if (document.status != DocumentStatus.PENDING) return
        val started = clock()
        val format = DocumentFormat.fromMime(document.mimeType)
        val file = document.localPath?.let(::File)
        val outcome = if (format == null || file == null) {
            Outcome.Failed(IngestError.EXTRACTION_FAILED)
        } else {
            publish(IngestProgress(id, IngestProgress.Stage.EXTRACTING, page = 0, pages = document.pageCount))
            extract(id, file, format, publish)
        }
        // Removed or retried meanwhile: nothing to write (contracts I7).
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

    private suspend fun extract(id: Long, file: File, format: DocumentFormat, publish: suspend (IngestProgress) -> Unit): Outcome = try {
        coroutineScope {
            val latest = AtomicReference<IngestProgress?>(null)
            val publisher = launch {
                while (isActive) {
                    delay(progressIntervalMs)
                    latest.getAndSet(null)?.let { publish(it) }
                }
            }
            try {
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
                            latest.set(IngestProgress(id, IngestProgress.Stage.EXTRACTING, done, total))
                        },
                    ) { block ->
                        job.ensureActive()
                        session.add(block) { chunks++ }
                    }
                    if (summary.blocks == 0) {
                        Outcome.Failed(IngestError.NO_TEXT)
                    } else {
                        publish(IngestProgress(id, IngestProgress.Stage.CHUNKING, page = null, pages = null))
                        session.finish { chunks++ }
                        Outcome.Parsed(chunks, summary.skippedPages)
                    }
                }
            } finally {
                publisher.cancel()
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

    private companion object {
        const val TAG = "MicroRAG"
        const val PROGRESS_INTERVAL_MS = 250L
    }
}

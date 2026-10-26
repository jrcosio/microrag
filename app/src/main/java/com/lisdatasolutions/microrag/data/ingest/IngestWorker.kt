package com.lisdatasolutions.microrag.data.ingest

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The processing request of «Iniciar indexación» (F06 research R7, F07 research R1): the runner chunks
 * the pending documents, then embeds and stores them. Errors stay per document, so it always succeeds;
 * a stop by the system leaves the work where it was, and WorkManager runs the request again.
 */
class IngestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val runner: IngestRunner by inject()
    private val logger: AppLogger by inject()

    override suspend fun doWork(): Result {
        val ids = inputData.getLongArray(WorkManagerIngestScheduler.KEY_DOCUMENT_IDS)?.toList().orEmpty()
        return try {
            runner.process(ids) { progress -> setProgress(WorkManagerIngestScheduler.progressData(progress)) }
            Result.success()
        } catch (e: IndexUnavailableException) {
            // The documents stay pending; the user starts again once the library is available.
            logger.w(TAG) { "ingest work stopped: index unavailable" }
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}

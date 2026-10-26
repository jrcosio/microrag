package com.lisdatasolutions.microrag.data.ingest

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.indexing.IndexingNotifications
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The processing request of «Iniciar indexación» (F06 research R7, F07 research R1): the runner chunks
 * the pending documents, then embeds and stores them. Errors stay per document, so it always succeeds;
 * a stop by the system leaves the work where it was, and WorkManager runs the request again.
 *
 * It runs as a `dataSync` foreground service («local file processing») when Android allows it, so the
 * process is never frozen. When it does not (started in the background, 6 h used, Android 16 quotas),
 * the work goes on as a plain job with the same notification, and tries again at every stage change:
 * with the app visible it gets the foreground back.
 */
class IngestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val runner: IngestRunner by inject()
    private val notifications: IndexingNotifications by inject()
    private val logger: AppLogger by inject()

    private var inForeground = false
    private var lastShownAt = 0L
    private var lastStage: Pair<Long, IngestProgress.Stage>? = null

    override suspend fun doWork(): Result {
        val ids = inputData.getLongArray(WorkManagerIngestScheduler.KEY_DOCUMENT_IDS)?.toList().orEmpty()
        show(notifications.build(null), stageChanged = true)
        return try {
            runner.process(ids) { progress ->
                setProgress(WorkManagerIngestScheduler.progressData(progress))
                val stage = progress.documentId to progress.stage
                show(notifications.build(progress), stageChanged = stage != lastStage)
                lastStage = stage
            }
            Result.success()
        } catch (e: IndexUnavailableException) {
            // The documents stay pending; the user starts again once the library is available.
            logger.w(TAG) { "ingest work stopped: index unavailable" }
            Result.failure()
        } finally {
            // A foreground notification goes with its service; a plain one is ours to remove.
            if (!inForeground) notifications.cancel()
        }
    }

    /** Foreground when Android allows it, otherwise the same notification as a plain one (research R1). */
    private suspend fun show(notification: Notification, stageChanged: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!stageChanged && now - lastShownAt < IndexingNotifications.UPDATE_INTERVAL_MS) return
        lastShownAt = now
        if (inForeground || stageChanged) {
            val wasInForeground = inForeground
            inForeground = try {
                setForeground(ForegroundInfo(IndexingNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (wasInForeground || lastStage == null) logger.i(TAG) { "ingest without foreground service: ${e.javaClass.simpleName}" }
                false
            }
        }
        if (!inForeground) notifications.notify(notification)
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}

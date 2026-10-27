package com.lisdatasolutions.microrag.data.ingest

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.device.ProcessState
import com.lisdatasolutions.microrag.data.indexing.IndexingAlarm
import com.lisdatasolutions.microrag.data.indexing.IndexingNotifications
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The processing request of «Iniciar indexación» (F06 research R7, F07 research R1): the runner chunks
 * the pending documents, then embeds and stores them. Errors stay per document, so it always succeeds;
 * a stop by the system leaves the work where it was, and WorkManager runs the request again.
 *
 * It runs as a `dataSync` foreground service («local file processing») when Android allows it, so the
 * process is never frozen. When it does not (started in the background, 6 h used, Android 16 quotas),
 * the work goes on as a plain job with the same notification, and tries again at every new document,
 * stage or stored batch: with the app visible it gets the foreground back within a batch. setForeground can also return while WorkManager's
 * service failed to reach the foreground (seen after a kill, with that service restarted in the
 * background), so the process state has the last word.
 *
 * While it runs, it moves the [IndexingAlarm] forward and keeps a plain notification fresh: that one
 * expires, so a process killed outside the foreground leaves no «Indexando» behind (D-041).
 */
class IngestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val runner: IngestRunner by inject()
    private val notifications: IndexingNotifications by inject()
    private val logger: AppLogger by inject()
    private val process: ProcessState by inject()
    private val alarm: IndexingAlarm by inject()

    private var inForeground = false
    private var lastShownAt = 0L
    private var attemptedAt = 0L
    /** The document, stage and stored batch last shown: a new one updates at once and tries the foreground. */
    private var lastStep: Triple<Long, IngestProgress.Stage, Int?>? = null
    private var lastNotification: Notification? = null

    /** The runner's progress and the keep-alive both show the notification. */
    private val showing = Mutex()

    override suspend fun doWork(): Result = coroutineScope {
        val ids = inputData.getLongArray(WorkManagerIngestScheduler.KEY_DOCUMENT_IDS)?.toList().orEmpty()
        alarm.arm()
        show(notifications.build(null), newStep = true)
        // Also while the runner waits for the phone to cool down, when no progress comes.
        val keepAlive = launch {
            while (true) {
                delay(KEEP_ALIVE_MS)
                alarm.arm()
                lastNotification?.let { show(it, newStep = false) }
            }
        }
        try {
            runner.process(ids) { progress ->
                setProgress(WorkManagerIngestScheduler.progressData(progress))
                val step = Triple(progress.documentId, progress.stage, progress.chunksDone?.div(IngestRunner.BATCH))
                show(notifications.build(progress), newStep = step != lastStep)
                lastStep = step
            }
            // Finished, or paused by the runner itself (no space, no model): nothing to wake up for.
            alarm.cancel()
            Result.success()
        } catch (e: IndexUnavailableException) {
            // The documents stay pending; the user starts again once the library is available.
            logger.w(TAG) { "ingest work stopped: index unavailable" }
            alarm.cancel()
            Result.failure()
        } finally {
            keepAlive.cancel()
            // A plain notification is ours to remove; Android keeps a foreground one until its service stops.
            notifications.cancel()
        }
    }

    /** Foreground when Android allows it, otherwise the same notification as a plain one (research R1). */
    private suspend fun show(notification: Notification, newStep: Boolean) = showing.withLock {
        lastNotification = notification
        val now = SystemClock.elapsedRealtime()
        if (!newStep && now - lastShownAt < IndexingNotifications.UPDATE_INTERVAL_MS) return@withLock
        lastShownAt = now
        // A second after setForeground, the service has started or failed: no more tries until the next step.
        if (inForeground && now - attemptedAt >= IndexingNotifications.UPDATE_INTERVAL_MS && !process.isForeground()) {
            inForeground = false
            logger.i(TAG) { "ingest foreground service not running" }
        }
        if (inForeground || newStep) {
            val wasInForeground = inForeground
            attemptedAt = now
            inForeground = try {
                setForeground(ForegroundInfo(IndexingNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (wasInForeground || lastStep == null) logger.i(TAG) { "ingest without foreground service: ${e.javaClass.simpleName}" }
                false
            }
        }
        // Same id as the foreground one: if the service does start, its notification replaces this one.
        if (!inForeground || !process.isForeground()) notifications.notify(notification)
    }

    private companion object {
        const val TAG = "MicroRAG"

        /** Well under the plain notification's expiry and the alarm's delay. */
        const val KEEP_ALIVE_MS = 60_000L
    }
}

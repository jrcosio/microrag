package com.lisdatasolutions.microrag.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lisdatasolutions.microrag.core.logging.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Download queue on API 31–33 (D-028): a long-running worker with a `dataSync` foreground service.
 * If the process was started in the background the foreground service may be refused; the worker
 * then keeps going in the normal job window, which is fine because downloads resume.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val runner: ModelDownloadRunner by inject()
    private val notifications: DownloadNotifications by inject()
    private val logger: AppLogger by inject()

    override suspend fun doWork(): Result = coroutineScope {
        promoteToForeground()
        val updates = launch {
            var lastUpdate = 0L
            runner.progress.collect {
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate >= DownloadNotifications.UPDATE_INTERVAL_MS) {
                    lastUpdate = now
                    promoteToForeground()
                }
            }
        }
        val result = runner.runQueue()
        updates.cancel()
        // Failures are final until the user taps «Reintentar»; only a lost network waits to run again.
        if (result.retryLater) Result.retry() else Result.success()
    }

    private suspend fun promoteToForeground() {
        val info = ForegroundInfo(
            DownloadNotifications.NOTIFICATION_ID,
            notifications.build(runner.progress.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        try {
            setForeground(info)
        } catch (e: CancellationException) {
            // Also an IllegalStateException: cancellation must propagate, not be logged as a refusal.
            throw e
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) when started in the background.
            logger.w(TAG, e) { "download worker continues without a foreground service" }
        }
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}

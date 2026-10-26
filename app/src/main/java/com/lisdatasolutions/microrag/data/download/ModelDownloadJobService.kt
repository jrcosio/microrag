package com.lisdatasolutions.microrag.data.download

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * User-initiated data transfer job (API 34+, D-028) that runs the shared download queue. The
 * notification is mandatory and is updated at most once per second.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ModelDownloadJobService : JobService(), KoinComponent {

    private val runner: ModelDownloadRunner by inject()
    private val notifications: DownloadNotifications by inject()
    private var scope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters): Boolean {
        postNotification(params)
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope = it }
        jobScope.launch {
            val updates = launch {
                var lastUpdate = 0L
                runner.progress.collect {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUpdate >= DownloadNotifications.UPDATE_INTERVAL_MS) {
                        lastUpdate = now
                        postNotification(params)
                    }
                }
            }
            val result = runner.runQueue()
            updates.cancel()
            jobFinished(params, result.retryLater)
        }
        return true
    }

    /** Rescheduled unless the app itself cancelled the job (user pause, FR-018). */
    override fun onStopJob(params: JobParameters): Boolean {
        scope?.cancel()
        scope = null
        return params.stopReason != JobParameters.STOP_REASON_CANCELLED_BY_APP
    }

    private fun postNotification(params: JobParameters) = setNotification(
        params,
        DownloadNotifications.NOTIFICATION_ID,
        notifications.build(runner.progress.value),
        JOB_END_NOTIFICATION_POLICY_REMOVE,
    )
}

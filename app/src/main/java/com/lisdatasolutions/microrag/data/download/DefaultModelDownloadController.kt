package com.lisdatasolutions.microrag.data.download

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.ModelDownloadController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Schedules the download queue (D-028): a user-initiated data transfer job on API 34+, a unique
 * WorkManager worker on API 31–33. Any network is allowed once the user confirmed the size (D-027).
 */
class DefaultModelDownloadController(
    private val context: Context,
    private val runner: ModelDownloadRunner,
    private val dirs: AppDirs,
    private val logger: AppLogger,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : ModelDownloadController {

    override val progress: StateFlow<DownloadProgress> = runner.progress

    private val jobScheduler get() = context.getSystemService(JobScheduler::class.java)

    override fun schedule(pendingBytes: Long) {
        runner.resetOutcomes()
        if (usesTransferJobs()) scheduleTransferJob(pendingBytes) else scheduleWorker()
    }

    override fun cancel() {
        if (usesTransferJobs()) {
            jobScheduler.cancel(JOB_ID)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }

    override suspend fun isScheduledOrRunning(): Boolean = if (usesTransferJobs()) {
        jobScheduler.getPendingJob(JOB_ID) != null
    } else {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK).first().any { !it.state.isFinished }
    }

    override fun partialBytes(id: ModelId): Long = dirs.partial(ModelCatalog.byId(id)).length()

    override fun deleteOrphanPartials() {
        val expected = ModelCatalog.required.map { dirs.partial(it).name }.toSet()
        dirs.downloads.listFiles()?.filter { it.isFile && it.name !in expected }?.forEach { it.delete() }
    }

    /** User-initiated data transfer jobs exist from Android 14 (API 34). */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun usesTransferJobs(): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun scheduleTransferJob(pendingBytes: Long) {
        val network = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, ModelDownloadJobService::class.java))
            .setUserInitiated(true)
            .setRequiredNetwork(network)
            .setEstimatedNetworkBytes(pendingBytes, 0)
            .setPersisted(true)
            .build()
        val result = jobScheduler.schedule(job)
        logger.i(TAG) { "download job scheduled: ${result == JobScheduler.RESULT_SUCCESS}, ${pendingBytes / MB} MB" }
    }

    private fun scheduleWorker() {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        logger.i(TAG) { "download worker enqueued" }
    }

    companion object {
        const val JOB_ID = 3001
        const val UNIQUE_WORK = "model-download"
        private const val TAG = "MicroRAG"
        private const val MB = 1024L * 1024L
    }
}

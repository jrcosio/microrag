package com.lisdatasolutions.microrag.data.download

import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** T030: user-initiated data transfer job on API 34+, unique worker on API 31–33 (D-028). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DefaultModelDownloadControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun controller(sdk: Int): DefaultModelDownloadController {
        val dirs = AppDirs(tmp.root)
        val runner = ModelDownloadRunner(
            downloader = { _, _, _ -> DownloadResult.Complete },
            repository = InMemoryModelFileRepository(),
            store = FakeOnboardingStore(),
            dirs = dirs,
            freeBytes = { Long.MAX_VALUE },
            hasInternet = { true },
            logger = RecordingLogger(),
        )
        return DefaultModelDownloadController(context, runner, dirs, RecordingLogger(), sdkInt = sdk)
    }

    @Test
    @Config(sdk = [35])
    fun api34Plus_schedulesAUserInitiatedTransferOnAnyNetwork() = runBlocking {
        val controller = controller(35)

        controller.schedule(pendingBytes = 1_234L)

        val job = context.getSystemService(JobScheduler::class.java).getPendingJob(DefaultModelDownloadController.JOB_ID)!!
        assertThat(job.isUserInitiated).isTrue()
        assertThat(job.isPersisted).isTrue()
        assertThat(job.estimatedNetworkDownloadBytes).isEqualTo(1_234L)
        val network = job.requiredNetwork!!
        assertThat(network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue()
        // Any network once the size is confirmed (D-027): no unmetered requirement.
        assertThat(network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).isFalse()
        assertThat(controller.isScheduledOrRunning()).isTrue()

        controller.cancel()
        assertThat(controller.isScheduledOrRunning()).isFalse()
    }

    @Test
    @Config(sdk = [32])
    fun api31To33_enqueuesOneUniqueWorkerThatNeedsAConnection() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val controller = controller(32)

        controller.schedule(pendingBytes = 1_234L)
        controller.schedule(pendingBytes = 1_234L)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(DefaultModelDownloadController.UNIQUE_WORK).get()
        assertThat(infos).hasSize(1)
        assertThat(infos.single().constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(controller.isScheduledOrRunning()).isTrue()

        controller.cancel()
        val cancelled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(DefaultModelDownloadController.UNIQUE_WORK).get()
        assertThat(cancelled.single().state).isEqualTo(WorkInfo.State.CANCELLED)
        assertThat(controller.isScheduledOrRunning()).isFalse()
    }
}

package com.lisdatasolutions.microrag.data.download

import android.app.Application
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config

/** T031: the API 31–33 worker runs the shared queue as a dataSync foreground service, or without it. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [32])
class ModelDownloadWorkerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val foregroundTypes = mutableListOf<Int>()
    private var online = true

    private fun startGraph(result: DownloadResult) {
        val store = FakeOnboardingStore()
        runBlocking { store.requestDownload(setOf(ModelId.EG2_270M)) }
        val runner = ModelDownloadRunner(
            downloader = { _, partial, _ ->
                partial.parentFile!!.mkdirs()
                partial.writeBytes(ByteArray(1))
                if (result is DownloadResult.Failed) online = false
                result
            },
            repository = InMemoryModelFileRepository(),
            store = store,
            dirs = AppDirs(tmp.root),
            freeBytes = { Long.MAX_VALUE },
            hasInternet = { online },
            logger = RecordingLogger(),
            sleep = { },
        )
        startKoin {
            modules(
                module {
                    single { runner }
                    single { DownloadNotifications(context) }
                    single<AppLogger> { RecordingLogger() }
                },
            )
        }
    }

    @After
    fun tearDown() = stopKoin()

    private fun worker(updater: ForegroundUpdater) =
        TestListenableWorkerBuilder<ModelDownloadWorker>(context).setForegroundUpdater(updater).build()

    private val recordingUpdater = ForegroundUpdater { _: Context, _: UUID, info: ForegroundInfo ->
        foregroundTypes += info.foregroundServiceType
        Futures.immediateVoidFuture()
    }

    @Test
    fun runsTheQueueAsADataSyncForegroundService() = runBlocking<Unit> {
        startGraph(DownloadResult.Complete)

        val result = worker(recordingUpdater).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(foregroundTypes).isNotEmpty()
        assertThat(foregroundTypes.toSet()).containsExactly(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    @Test
    fun keepsGoingWhenTheForegroundServiceIsRefused() = runBlocking<Unit> {
        startGraph(DownloadResult.Complete)
        val refusing = ForegroundUpdater { _: Context, _: UUID, _: ForegroundInfo ->
            Futures.immediateFailedFuture<Void>(IllegalStateException("not allowed from background")) as ListenableFuture<Void>
        }

        assertThat(worker(refusing).doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun aLostNetworkAsksToRunAgain() = runBlocking<Unit> {
        startGraph(DownloadResult.Failed(DownloadError.Network("unreachable")))

        assertThat(worker(recordingUpdater).doWork()).isEqualTo(ListenableWorker.Result.retry())
    }
}

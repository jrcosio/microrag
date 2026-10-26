package com.lisdatasolutions.microrag.data.ingest

import android.app.Application
import android.app.NotificationManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.IndexingNotifications
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** F07 T038 (research R1): without a foreground service the work goes on with a plain notification. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class IngestWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir: File = Files.createTempDirectory("documents").toFile()
    private val index = FakeRagIndex()
    private val sessions = FakeEmbeddingSessions()
    private val logger = RecordingLogger()

    @Before
    fun setUp() {
        val runner = IngestRunner(
            index, DocumentExtractor(FakePdfOpener()), sessions, InMemoryIngestRunStore(), FakeThermalStatusSource(),
            { Long.MAX_VALUE }, Dispatchers.IO, logger, clock = System::currentTimeMillis,
        )
        startKoin {
            modules(
                module {
                    single { runner }
                    single { IndexingNotifications(context) }
                    single<AppLogger> { this@IngestWorkerTest.logger }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        dir.deleteRecursively()
    }

    @Test
    fun withoutForeground_itIndexesWithAPlainNotificationAndKeepsTrying() = runBlocking<Unit> {
        val file = File(dir, "notas.txt").apply { writeText((1..40).joinToString("\n\n") { "Párrafo $it con texto." }) }
        val id = index.addDocument(NewDocument(displayName = "notas.txt", contentHash = "h", mimeType = "text/plain", localPath = file.path))
        val attempts = AtomicInteger()
        val refused = ForegroundUpdater { _, _, _ ->
            attempts.incrementAndGet()
            Futures.immediateFailedFuture(ForegroundServiceStartNotAllowedException("app is in the background"))
        }
        var shownWhileWorking = 0
        sessions.embedder.beforeEmbedding = {
            shownWhileWorking = maxOf(shownWhileWorking, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)
        }
        val worker = TestListenableWorkerBuilder<IngestWorker>(context)
            .setInputData(Data.Builder().putLongArray(WorkManagerIngestScheduler.KEY_DOCUMENT_IDS, longArrayOf(id)).build())
            .setForegroundUpdater(refused)
            .build()

        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
        assertThat(attempts.get()).isAtLeast(3)
        assertThat(shownWhileWorking).isEqualTo(1)
        assertThat(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications).isEmpty()
        assertThat(logger.lines.joinToString()).contains("ingest without foreground service: ForegroundServiceStartNotAllowedException")
    }
}

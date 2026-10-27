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
import androidx.work.ProgressUpdater
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.device.ProcessState
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.IndexingAlarm
import com.lisdatasolutions.microrag.data.indexing.IndexingNotifications
import com.lisdatasolutions.microrag.data.indexing.RecordingIndexingAlarm
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
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
import org.robolectric.shadows.ShadowSystemClock

/**
 * F07 T038 (research R1): without a foreground service the work goes on with a plain notification, also
 * when setForeground returns but the process never reaches the foreground (seen on API 37 after a kill).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class IngestWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir: File = Files.createTempDirectory("documents").toFile()
    private val index = FakeRagIndex()
    private val sessions = FakeEmbeddingSessions()
    private val logger = RecordingLogger()
    private val alarm = RecordingIndexingAlarm()

    @Volatile
    private var foreground = false

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
                    single { ProcessState { foreground } }
                    single<IndexingAlarm> { alarm }
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
        var expiresAfter = 0L
        sessions.embedder.beforeEmbedding = {
            val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
            shownWhileWorking = maxOf(shownWhileWorking, shown.size)
            shown.firstOrNull()?.let { expiresAfter = it.timeoutAfter }
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
        // D-041: if the process died now, the plain notification would go by itself.
        assertThat(expiresAfter).isEqualTo(IndexingNotifications.PLAIN_TIMEOUT_MS)
        assertThat(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications).isEmpty()
        assertThat(logger.lines.joinToString()).contains("ingest without foreground service: ForegroundServiceStartNotAllowedException")
        // The wake-up was set while it ran, and the finished run needs none.
        assertThat(alarm.calls.first()).isTrue()
        assertThat(alarm.armed).isFalse()
    }

    // The service starts or fails after setForeground returns: an accepting updater with a process that
    // never reaches the foreground, and the same with a real foreground.

    private val notificationManager get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    private class Run(val attempts: Int, val stageChanges: Int, val shownWhileWorking: Int)

    /**
     * Indexes a long text with every chunk a second apart on the worker's clock, so each count is shown.
     * On Dispatchers.Default, as CoroutineWorker runs, so the runner's progress job is not starved.
     */
    private fun runAccepted(): Run = runBlocking(Dispatchers.Default) {
        val file = File(dir, "largo.txt").apply { writeText((1..400).joinToString("\n\n") { "Párrafo $it con bastante texto para trocear." }) }
        val id = index.addDocument(NewDocument(displayName = "largo.txt", contentHash = "l", mimeType = "text/plain", localPath = file.path))
        val attempts = AtomicInteger()
        val accepted = ForegroundUpdater { _, _, _ ->
            attempts.incrementAndGet()
            Futures.immediateVoidFuture()
        }
        val steps = StepCounter()
        var shown = 0
        sessions.embedder.beforeEmbedding = {
            shown = maxOf(shown, notificationManager.allNotifications.size)
            Thread.sleep(RUNNER_INTERVAL_MS)
            ShadowSystemClock.advanceBy(Duration.ofMillis(1_100))
        }
        val worker = TestListenableWorkerBuilder<IngestWorker>(context)
            .setInputData(Data.Builder().putLongArray(WorkManagerIngestScheduler.KEY_DOCUMENT_IDS, longArrayOf(id)).build())
            .setForegroundUpdater(accepted)
            .setProgressUpdater(steps.updater)
            .build()

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
        assertThat(notificationManager.allNotifications).isEmpty()
        // The first try comes before any progress, with the «Preparando documentos» notification.
        Run(attempts.get(), steps.count + 1, shown)
    }

    /** Counts the new documents, stages and stored batches the worker reports (its retry points). */
    private class StepCounter {
        private val steps = CopyOnWriteArrayList<String>()
        val count get() = steps.size

        val updater = ProgressUpdater { _, _, data ->
            val done = data.getInt(WorkManagerIngestScheduler.KEY_CHUNKS_DONE, -1)
            val step = listOf(
                data.getLong(WorkManagerIngestScheduler.KEY_DOCUMENT, -1),
                data.getString(WorkManagerIngestScheduler.KEY_STAGE),
                if (done < 0) null else done / IngestRunner.BATCH,
            ).toString()
            if (steps.lastOrNull() != step) steps += step
            Futures.immediateVoidFuture()
        }
    }

    @Test
    fun withoutForeground_itTriesAgainAtEveryStoredBatch() = runBlocking<Unit> {
        val file = File(dir, "extenso.txt").apply { writeText((1..5_000).joinToString("\n\n") { "Párrafo $it con bastante texto para trocear." }) }
        val id = index.addDocument(NewDocument(displayName = "extenso.txt", contentHash = "e", mimeType = "text/plain", localPath = file.path))
        val attempts = AtomicInteger()
        val refused = ForegroundUpdater { _, _, _ ->
            attempts.incrementAndGet()
            Futures.immediateFailedFuture(ForegroundServiceStartNotAllowedException("app is in the background"))
        }
        val steps = StepCounter()
        val worker = TestListenableWorkerBuilder<IngestWorker>(context)
            .setInputData(Data.Builder().putLongArray(WorkManagerIngestScheduler.KEY_DOCUMENT_IDS, longArrayOf(id)).build())
            .setForegroundUpdater(refused)
            .setProgressUpdater(steps.updater)
            .build()

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
        val chunks = index.docs.getValue(id).chunkCount!!
        assertThat(chunks).isGreaterThan(2 * IngestRunner.BATCH)
        // The first try, then one per document stage and per stored batch: the app gets the foreground back within a batch.
        assertThat(attempts.get()).isEqualTo(steps.count + 1)
        assertThat(steps.count).isAtLeast(chunks / IngestRunner.BATCH + 2)
    }

    @Test
    fun aForegroundThatNeverStarts_showsThePlainNotificationAndTriesOnlyAtNewStages() {
        foreground = false

        val run = runAccepted()

        assertThat(run.shownWhileWorking).isEqualTo(1)
        assertThat(run.attempts).isEqualTo(run.stageChanges)
        assertThat(logger.lines.joinToString()).contains("ingest foreground service not running")
    }

    @Test
    fun aRealForeground_needsNoPlainNotificationAndKeepsItsOwnUpToDate() {
        foreground = true

        val run = runAccepted()

        assertThat(run.shownWhileWorking).isEqualTo(0)
        assertThat(run.attempts).isGreaterThan(run.stageChanges)
        assertThat(logger.lines.joinToString()).doesNotContain("ingest foreground service not running")
    }

    private companion object {
        /** Above the runner's progress interval, so every chunk publishes its count. */
        const val RUNNER_INTERVAL_MS = 300L
    }
}

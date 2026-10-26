package com.lisdatasolutions.microrag.data.ingest

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F06 T046: one unique request chain with the ids, tags and progress keys of contracts/ingest-api.md. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WorkManagerIngestSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerIngestScheduler

    @Before
    fun setUp() {
        // The test WorkManager runs work at once: a parked worker keeps the chain enqueued, running and blocked.
        val parked = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                object : CoroutineWorker(appContext, workerParameters) {
                    override suspend fun doWork(): Result = awaitCancellation()
                }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setWorkerFactory(parked).build())
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerIngestScheduler(workManager)
    }

    @Test
    fun enqueuedRequestsCarryTheirIdsAndTagsAndMarkTheDocumentsActive() = runBlocking<Unit> {
        scheduler.enqueue(listOf(3L, 1L))
        scheduler.enqueue(listOf(7L))

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerIngestScheduler.UNIQUE_NAME).get()
        assertThat(infos).hasSize(2)
        assertThat(infos.flatMap { it.tags }).containsAtLeast("ingest", "ingest-doc:3", "ingest-doc:1", "ingest-doc:7")
        assertThat(infos.map { it.state }).containsExactly(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
        assertThat(scheduler.activeDocumentIds.first()).containsExactly(1L, 3L, 7L)
    }

    @Test
    fun nothingToEnqueueAddsNoRequest() = runBlocking<Unit> {
        scheduler.enqueue(emptyList())

        assertThat(workManager.getWorkInfosForUniqueWork(WorkManagerIngestScheduler.UNIQUE_NAME).get()).isEmpty()
        assertThat(scheduler.activeDocumentIds.first()).isEmpty()
    }

    @Test
    fun progressDataRoundTrips() {
        val progress = IngestProgress(5, IngestProgress.Stage.EXTRACTING, 12, 200)
        assertThat(WorkManagerIngestScheduler.progressOf(WorkManagerIngestScheduler.progressData(progress))).isEqualTo(progress)
        val chunking = IngestProgress(5, IngestProgress.Stage.CHUNKING, null, null)
        assertThat(WorkManagerIngestScheduler.progressOf(WorkManagerIngestScheduler.progressData(chunking))).isEqualTo(chunking)
        assertThat(WorkManagerIngestScheduler.documentIdOf("ingest-doc:42")).isEqualTo(42L)
        assertThat(WorkManagerIngestScheduler.documentIdOf("ingest")).isNull()
    }
}

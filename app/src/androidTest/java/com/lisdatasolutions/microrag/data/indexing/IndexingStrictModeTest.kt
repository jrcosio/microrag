package com.lisdatasolutions.microrag.data.indexing

import android.net.Uri
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentImporter
import com.lisdatasolutions.microrag.data.ingest.ImportResult
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.IngestScheduler
import com.lisdatasolutions.microrag.data.ingest.IngestWork
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.ingest.SafDocumentSource
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-009: starting, observing, pausing and resuming the run, all called from the main thread, never
 * touch the disk there (constitution IV, F07 T061). The run store is the real DataStore one; same policy
 * and control as F04 and F06.
 */
@RunWith(AndroidJUnit4::class)
class IndexingStrictModeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "indexing-strict-${UUID.randomUUID()}")

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun mainThreadViolations(block: () -> Unit): List<Violation> {
        val violations = CopyOnWriteArrayList<Violation>()
        var previous: StrictMode.ThreadPolicy? = null
        instrumentation.runOnMainSync {
            previous = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyListener({ it.run() }) { violations += it }
                    .build(),
            )
        }
        try {
            instrumentation.runOnMainSync(block)
            instrumentation.waitForIdleSync()
        } finally {
            instrumentation.runOnMainSync { StrictMode.setThreadPolicy(previous) }
        }
        return violations
    }

    private class RecordingScheduler : IngestScheduler {
        override val activeDocumentIds = MutableStateFlow(emptySet<Long>())
        override val progress = MutableStateFlow<IngestProgress?>(null)
        override val work = MutableStateFlow(IngestWork.NONE)
        val requests = CopyOnWriteArrayList<List<Long>>()

        override suspend fun enqueue(documentIds: List<Long>) {
            requests += documentIds
        }

        override suspend fun replace(documentIds: List<Long>) = Unit

        override suspend fun cancel() = Unit
    }

    @Test
    fun runControlsAndStateStayOffTheMainThread() {
        val picked = File(root, "picked").apply { mkdirs() }
        val logger = CapturingLogger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val (violations, statuses) = TempRagIndex().use { temp ->
            val pdf = PlatformPdfOpener()
            val source = SafDocumentSource(context.contentResolver)
            val importer = DocumentImporter(source, DocumentFileStore(File(root, "documents")), pdf, temp.index, { Long.MAX_VALUE / 2 }, logger)
            // Two documents as F06 leaves them, imported off the main thread: only the run is under the policy.
            val ids = listOf(
                File(picked, "notas.txt").apply { writeText("Primera nota sobre bombas.\n\nSegunda nota sobre válvulas.") },
                File(picked, "guia.md").apply { writeText("# Guía\n\nMantenimiento de la bomba principal.") },
            ).map { file ->
                val uri = Uri.fromFile(file).toString()
                runBlocking { (importer.import(uri, source.describe(uri)) as ImportResult.Imported).documentId }
            }
            val runs = DataStoreIngestRunStore(DataStoreIngestRunStore.create(root, scope))
            val thermal = FakeThermalStatusSource()
            val scheduler = RecordingScheduler()
            val repository = DefaultIndexingRepository(temp.index, scheduler, runs, thermal, Dispatchers.IO, logger)
            val runner = IngestRunner(
                temp.index, DocumentExtractor(pdf), FakeEmbeddingSessions(), runs, thermal, { Long.MAX_VALUE }, Dispatchers.IO, logger,
            )

            val found = mainThreadViolations {
                runBlocking {
                    withTimeout(30_000) {
                        assertThat(repository.start()).isEqualTo(2)
                        repository.state.first { it is IndexingState.Active }
                        repository.pause()
                        repository.state.first { (it as? IndexingState.Active)?.pause == PauseReason.USER }
                        repository.resume()
                        runner.process(scheduler.requests.last()) { }
                        repository.state.first { it is IndexingState.Idle }
                    }
                }
            }
            found to runBlocking { ids.map { temp.index.document(it)?.status } }
        }
        scope.cancel()

        assertThat(statuses).containsExactly(DocumentStatus.DONE, DocumentStatus.DONE)
        assertWithMessage(violations.joinToString("\n") { it.stackTraceToString().take(800) }).that(violations).isEmpty()
    }

    /** Control: the same policy does catch a disk read made on the main thread. */
    @Test
    fun policy_detectsAMainThreadDiskRead() {
        val file = File(context.cacheDir, "indexing-strict-control.txt").apply { writeText("x") }

        val violations = mainThreadViolations { file.readText() }
        file.delete()

        assertThat(violations).isNotEmpty()
    }
}

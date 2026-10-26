package com.lisdatasolutions.microrag.data.ingest

import android.net.Uri
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.data.indexing.DefaultIndexingRepository
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
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
 * SC-009: importing, observing the queue, enqueuing and processing, all called from the main thread,
 * never touch the disk there (constitution IV, F06 T066). Same policy and control as F04.
 */
@RunWith(AndroidJUnit4::class)
class IngestStrictModeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "ingest-strict-${UUID.randomUUID()}")

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

    @Test
    fun importQueueAndProcessingStayOffTheMainThread() {
        val picked = File(root, "picked").apply { mkdirs() }
        val uris = listOf(
            File(picked, "notas.txt").apply { writeText("Primera nota.\n\nSegunda nota.") },
            File(picked, "guia.md").apply { writeText("# Guía\ncuerpo") },
        ).map { Uri.fromFile(it).toString() } + listOf("pdf/text_3_pages.pdf").map { asset ->
            val file = File(picked, asset.substringAfter('/'))
            instrumentation.context.assets.open(asset).use { input -> file.outputStream().use(input::copyTo) }
            Uri.fromFile(file).toString()
        }
        val violations = TempRagIndex().use { temp ->
            val logger = CapturingLogger()
            val files = DocumentFileStore(File(root, "documents"))
            val pdf = PlatformPdfOpener()
            val source = SafDocumentSource(context.contentResolver)
            val importer = DocumentImporter(source, files, pdf, temp.index, { Long.MAX_VALUE / 2 }, logger)
            val scheduler = object : IngestScheduler {
                override val activeDocumentIds = MutableStateFlow(emptySet<Long>())
                override val progress = MutableStateFlow<IngestProgress?>(null)
                override val work = MutableStateFlow(IngestWork.NONE)
                val requests = mutableListOf<List<Long>>()
                override suspend fun enqueue(documentIds: List<Long>) {
                    requests += documentIds
                }
                override suspend fun replace(documentIds: List<Long>) = Unit
                override suspend fun cancel() = Unit
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val runs = InMemoryIngestRunStore()
            val repository = DefaultImportQueueRepository(
                importer, source, temp.index, files, scheduler, pdf,
                DefaultIndexingRepository(temp.index, scheduler, runs, FakeThermalStatusSource(), Dispatchers.IO, logger), runs,
                scope, Dispatchers.IO, logger,
            )
            val runner = IngestRunner(temp.index, DocumentExtractor(pdf), FakeEmbeddingSessions.withoutModel(), InMemoryIngestRunStore(), FakeThermalStatusSource(), { Long.MAX_VALUE }, Dispatchers.IO, logger)
            val expected = if (pdf.isAvailable) 3 else 2

            val found = mainThreadViolations {
                runBlocking {
                    repository.import(uris)
                    withTimeout(30_000) {
                        repository.queue.first { queue -> queue.items.count { it.state == QueueItemState.Ready } == expected }
                    }
                    repository.startProcessing()
                    runner.process(scheduler.requests.flatten()) { }
                    withTimeout(30_000) {
                        repository.queue.first { queue -> queue.items.count { it.state is QueueItemState.Prepared } == expected }
                    }
                }
            }
            scope.cancel()
            found
        }

        assertWithMessage(violations.joinToString("\n") { it.stackTraceToString().take(800) }).that(violations).isEmpty()
    }

    /** Control: the same policy does catch a disk read made on the main thread. */
    @Test
    fun policy_detectsAMainThreadDiskRead() {
        val file = File(context.cacheDir, "ingest-strict-control.txt").apply { writeText("x") }

        val violations = mainThreadViolations { file.readText() }
        file.delete()

        assertThat(violations).isNotEmpty()
    }
}

package com.lisdatasolutions.microrag.data.library

import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.data.indexing.DataStoreIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.DefaultIndexingRepository
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFiles
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.LibraryResult
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.RecordingIngestScheduler
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F08 T050 (rule L10): observing the Library and a detail, the preview, the switch, reindexing,
 * «Quitar del índice» and deleting, all called from the main thread, never touch the disk there. The
 * run store is the real DataStore one and the copies are real files; same policy and control as F04,
 * F06 and F07.
 */
@RunWith(AndroidJUnit4::class)
class LibraryStrictModeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "library-strict-${UUID.randomUUID()}")

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
    fun libraryActionsStayOffTheMainThread() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val violations = TempRagIndex().use { temp ->
            val documents = File(root, "documents").apply { mkdirs() }
            // Documents as F07 leaves them, written off the main thread: only the Library is under the policy.
            val ids = runBlocking {
                (1..3).map { n ->
                    val copy = File(documents, "copia-$n.md").apply { writeText("# Manual $n\n\nMantenimiento de la bomba $n.") }
                    val id = temp.index.addDocument(
                        NewDocument(displayName = "Manual $n.md", contentHash = "h$n", mimeType = "text/markdown", localPath = copy.path),
                    )
                    temp.index.insertChunks(id, listOf(chunk(0, content = "Mantenimiento de la bomba $n.", page = null)))
                    temp.index.updateStatus(id, DocumentStatus.DONE, chunkCount = 1)
                    id
                }
            }
            val runs = DataStoreIngestRunStore(DataStoreIngestRunStore.create(root, scope))
            val scheduler = RecordingIngestScheduler()
            val indexing = DefaultIndexingRepository(temp.index, scheduler, runs, FakeThermalStatusSource(), Dispatchers.IO, temp.logger)
            val files = DocumentFiles(DocumentFileStore(documents), temp.index, Dispatchers.IO, temp.logger)
            val repository = DefaultLibraryRepository(temp.index, files, runs, indexing, Dispatchers.IO, temp.logger)
            val (first, second, third) = ids

            val found = mainThreadViolations {
                runBlocking {
                    withTimeout(30_000) {
                        val page = repository.page(LibraryQuery()).first() as LibraryResult.Loaded
                        assertThat(page.page.total).isEqualTo(3)
                        assertThat(repository.detail(first).first()).isInstanceOf(DetailResult.Loaded::class.java)
                        assertThat(repository.preview(first, null)?.text).isEqualTo("Mantenimiento de la bomba 1.")
                        repository.setIncluded(first, false)
                        repository.reindex(first)
                        repository.removeFromIndex(second)
                        repository.delete(third)
                    }
                }
            }
            runBlocking {
                assertThat(temp.index.shadowOf(first)).isNotNull()
                assertThat(temp.index.document(second)!!.status).isEqualTo(DocumentStatus.OUT_OF_INDEX)
                assertThat(temp.index.document(third)).isNull()
            }
            assertThat(File(documents, "copia-3.md").exists()).isFalse()
            found
        }
        scope.cancel()

        assertWithMessage(violations.joinToString("\n") { it.stackTraceToString().take(800) }).that(violations).isEmpty()
    }

    /** Control: the same policy does catch a disk read made on the main thread. */
    @Test
    fun policy_detectsAMainThreadDiskRead() {
        val file = File(context.cacheDir, "library-strict-control.txt").apply { writeText("x") }

        val violations = mainThreadViolations { file.readText() }
        file.delete()

        assertThat(violations).isNotEmpty()
    }
}

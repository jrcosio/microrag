package com.lisdatasolutions.microrag.data.rag

import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-007: every RagIndex operation called from the main thread does its disk work on the database
 * dispatcher, so a strict main-thread policy sees no violation (constitution IV, T045).
 */
@RunWith(AndroidJUnit4::class)
class RagIndexStrictModeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * Runs [block] on the main thread under a disk read/write policy and returns its violations. On a
     * Looper thread StrictMode reports after the current message, so the policy stays until the main
     * thread is idle.
     */
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
    fun operationsCalledFromTheMainThread_neverTouchTheDiskThere() {
        val violations = TempRagIndex().use { temp ->
            val index = temp.index
            mainThreadViolations {
                runBlocking {
                    index.open()
                    val doc = index.addDocument(NewDocument(displayName = "Manual", contentHash = "h"))
                    index.insertChunks(doc, (0..4).map { chunk(it, content = "bombas $it") })
                    index.vectorSearch(unitVector(1))
                    index.textSearch("bombas")
                    index.loadChunks(listOf(1L, 2L))
                    index.neighbor(doc, 1)
                    index.document(doc)
                    index.updateStatus(doc, DocumentStatus.DONE, chunkCount = 5)
                    index.setIncluded(doc, false)
                    index.meta()
                    index.counts()
                    index.close()
                    index.deleteDocument(doc)
                    index.counts()
                }
            }
        }

        assertWithMessage(violations.joinToString("\n") { it.stackTraceToString().take(600) }).that(violations).isEmpty()
    }

    /** Control: the same policy does catch a disk read made on the main thread. */
    @Test
    fun policy_detectsAMainThreadDiskRead() {
        val file = File(instrumentation.targetContext.cacheDir, "strict-control.txt").apply { writeText("x") }

        val violations = mainThreadViolations { file.readText() }
        file.delete()

        assertThat(violations).isNotEmpty()
    }
}

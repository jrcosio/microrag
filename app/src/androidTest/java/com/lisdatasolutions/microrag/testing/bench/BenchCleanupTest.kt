package com.lisdatasolutions.microrag.testing.bench

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T011 (contract B2, SC-010): what the phone phase leaves behind is removed at its end: the bank databases, the
 * banks and the two test models of `dev-models`, and the reports of F14 and of the visual chat bench. The real
 * cleanup runs only with `-e f14.cleanup true`, so a battery never wipes the banks in the middle of the phase;
 * without it, the same cleanup is checked on simulated files.
 */
@RunWith(AndroidJUnit4::class)
class BenchCleanupTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun theCleanup_removesWhatTheBenchesLeft() {
        val sandbox = File(context.cacheDir, "bench-cleanup-test").apply { deleteRecursively() }
        val folders = Folders(File(sandbox, "cache"), File(sandbox, "dev-bench"), File(sandbox, "dev-models"), File(sandbox, "reports"))
        File(folders.cache, "bench").mkdirs()
        File(folders.cache, "bench/bank-10000.db").writeText("db")
        File(folders.bench, "bank-10000").mkdirs()
        File(folders.bench, "bank-10000/manifest.json").writeText("{}")
        folders.models.mkdirs()
        File(folders.models, DevFiles.EG2_270M.fileName).writeText("model")
        File(folders.models, DevFiles.GEMMA4_E4B.fileName).writeText("model")
        File(folders.models, "other-model.litertlm").writeText("kept")
        folders.reports.mkdirs()
        listOf("f14-search-1.json", "visual-chat-2.json", "f19-visual-ingest-3.json", "feasibility-4.json").forEach { File(folders.reports, it).writeText("{}") }

        clean(folders)

        assertThat(File(folders.cache, "bench").exists()).isFalse()
        assertThat(folders.bench.listFiles().orEmpty()).isEmpty()
        assertThat(folders.models.list().orEmpty().toList()).containsExactly("other-model.litertlm")
        assertThat(folders.reports.list().orEmpty().toList()).containsExactly("feasibility-4.json")
        sandbox.deleteRecursively()
    }

    @Test
    fun cleanThePhone() {
        assumeTrue("only with -e f14.cleanup true", InstrumentationRegistry.getArguments().getString(ARGUMENT) == "true")
        val folders = Folders.of(context)

        clean(folders)

        assertThat(File(folders.cache, "bench").exists()).isFalse()
        assertThat(folders.bench.listFiles().orEmpty()).isEmpty()
        assertThat(File(folders.models, DevFiles.EG2_270M.fileName).exists()).isFalse()
        assertThat(File(folders.models, DevFiles.GEMMA4_E4B.fileName).exists()).isFalse()
        assertThat(folders.reports.list().orEmpty().filter(::isBenchReport)).isEmpty()
    }

    private data class Folders(val cache: File, val bench: File, val models: File, val reports: File) {
        companion object {
            fun of(context: Context) = Folders(context.cacheDir, DevFiles.benchDir(context), DevFiles.modelsDir(context),
                File(context.filesDir, BenchReport.REPORTS))
        }
    }

    private fun clean(folders: Folders) {
        File(folders.cache, BenchBank.CACHE).deleteRecursively()
        folders.bench.listFiles()?.forEach { it.deleteRecursively() }
        listOf(DevFiles.EG2_270M, DevFiles.GEMMA4_E4B).forEach { File(folders.models, it.fileName).delete() }
        folders.reports.listFiles { f -> isBenchReport(f.name) }?.forEach { it.delete() }
    }

    /** The reports the F14 phase leaves: its own, the visual chat bench's and the visual ingest bench's (T048). */
    private fun isBenchReport(name: String) = listOf(BenchReport.PREFIX, VISUAL_CHAT, VISUAL_INGEST).any(name::startsWith)

    private companion object {
        const val ARGUMENT = "f14.cleanup"
        const val VISUAL_CHAT = "visual-chat-"
        const val VISUAL_INGEST = "f19-visual-ingest-"
    }
}

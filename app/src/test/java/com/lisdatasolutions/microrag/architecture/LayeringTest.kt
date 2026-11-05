package com.lisdatasolutions.microrag.architecture

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** Constitution II: native SDKs stay behind their adapters. */
class LayeringTest {

    private val sources = listOf(File("src/main/java"), File("src/debug/java"))
        .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }

    /** Files that import [prefix] outside [allowedDirs]. */
    private fun offenders(prefix: String, vararg allowedDirs: File): List<String> = sources
        .filter { it.readText().contains("import $prefix") }
        .filterNot { file -> allowedDirs.any { file.canonicalPath.startsWith(it.canonicalPath + File.separator) } }
        .map { it.path }

    /** F18 research R3: the visual diagnostic's adapters live in the same package of the debug source set. */
    @Test
    fun onlyLiteRtAdaptersImportTheInferenceSdk() {
        val main = File("src/main/java/com/lisdatasolutions/microrag/rag/runtime/litert")
        val debug = File("src/debug/java/com/lisdatasolutions/microrag/rag/runtime/litert")

        assertWithMessage("Files importing LiteRT-LM outside rag/runtime/litert")
            .that(offenders("com.google.ai.edge.litertlm", main, debug)).isEmpty()
    }

    @Test
    fun onlyTheRagDataLayerImportsSqlite() {
        val allowedDir = File("src/main/java/com/lisdatasolutions/microrag/data/rag")

        assertWithMessage("Files importing androidx.sqlite outside data/rag")
            .that(offenders("androidx.sqlite", allowedDir)).isEmpty()
    }

    @Test
    fun onlyTheIngestDataLayerImportsThePlatformPdfApi() {
        val main = File("src/main/java/com/lisdatasolutions/microrag/data/ingest")
        val debug = File("src/debug/java/com/lisdatasolutions/microrag/data/ingest")

        assertWithMessage("Files importing android.graphics.pdf outside data/ingest")
            .that(offenders("android.graphics.pdf", main, debug)).isEmpty()
    }

    @Test
    fun theIngestScreenUsesNeitherWorkManagerNorThePdfApi() {
        val featureDir = File("src/main/java/com/lisdatasolutions/microrag/feature/ingest")
        val leaks = sources
            .filter { it.canonicalPath.startsWith(featureDir.canonicalPath + File.separator) }
            .filter { file -> listOf("import androidx.work", "import android.graphics.pdf").any { file.readText().contains(it) } }
            .map { it.path }

        assertWithMessage("feature/ingest files importing WorkManager or the PDF API").that(leaks).isEmpty()
    }

    @Test
    fun onlyTheDeviceLayerAndTheDiagnosticsReadThePowerManager() {
        val allowed = listOf(
            File("src/main/java/com/lisdatasolutions/microrag/data/device"),
            File("src/debug/java/com/lisdatasolutions/microrag/debug/feasibility"),
        )
        val leaks = sources
            .filter { it.readText().contains("import android.os.PowerManager") }
            .filterNot { file -> allowed.any { file.canonicalPath.startsWith(it.canonicalPath + File.separator) } }
            .map { it.path }

        assertWithMessage("Files importing android.os.PowerManager outside data/device").that(leaks).isEmpty()
    }

    @Test
    fun theIndexScreenUsesNoBackendApi() {
        val featureDir = File("src/main/java/com/lisdatasolutions/microrag/feature/indexing")
        val forbidden = listOf("import androidx.work", "import androidx.sqlite", "import com.google.ai.edge.litertlm", "import android.os.PowerManager")
        val leaks = sources
            .filter { it.canonicalPath.startsWith(featureDir.canonicalPath + File.separator) }
            .filter { file -> forbidden.any { file.readText().contains(it) } }
            .map { it.path }

        assertWithMessage("feature/indexing files importing WorkManager, SQLite, LiteRT or PowerManager").that(leaks).isEmpty()
    }

    @Test
    fun theLibraryAndDocumentScreensUseNoDataLayerApi() {
        val featureDirs = listOf("library", "document").map { File("src/main/java/com/lisdatasolutions/microrag/feature/$it") }
        val forbidden = listOf(
            "import com.lisdatasolutions.microrag.data.", "import androidx.sqlite", "import androidx.work", "import java.io.File",
        )
        val files = sources.filter { file -> featureDirs.any { file.canonicalPath.startsWith(it.canonicalPath + File.separator) } }
        val leaks = files.filter { file -> forbidden.any { file.readText().contains(it) } }.map { it.path }

        assertWithMessage("feature/library and feature/document files exist").that(files).isNotEmpty()
        assertWithMessage("feature/library and feature/document files importing the data layer, SQLite, WorkManager or File")
            .that(leaks).isEmpty()
    }

    /** F10 T038 (rule V1): the retrieval diagnostic lives in src/debug only; main and release never name it. */
    @Test
    fun nothingOutsideDebugReferencesTheRetrievalDiagnostic() {
        val roots = listOf(File("src/main"), File("src/release"))
        val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.toList() }
        val leaks = files.filter { it.readText().contains("debug.retrieval") }.map { it.path }

        assertWithMessage("main and release files exist").that(files).isNotEmpty()
        assertWithMessage("main or release files referencing debug.retrieval").that(leaks).isEmpty()
        assertWithMessage("the diagnostic exists in debug")
            .that(File("src/debug/java/com/lisdatasolutions/microrag/debug/retrieval/RetrievalDiagnosticScreen.kt").isFile).isTrue()
    }

    /** F18 T004 (research R3): the visual diagnostic lives in src/debug only; main and release never name it. */
    @Test
    fun nothingOutsideDebugReferencesTheVisualDiagnostic() {
        val roots = listOf(File("src/main"), File("src/release"))
        val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.toList() }
        val leaks = files.filter { file ->
            listOf("debug.feasibility.visual", "LiteRtVisionEngines", "DebugPageRasterizer").any { file.readText().contains(it) }
        }.map { it.path }

        assertWithMessage("main or release files referencing the visual diagnostic").that(leaks).isEmpty()
        assertWithMessage("the visual diagnostic exists in debug")
            .that(File("src/debug/java/com/lisdatasolutions/microrag/debug/feasibility/visual/VisualFeasibilityScreen.kt").isFile)
            .isTrue()
    }

    /** F09 T030: Home reads models, library and run through use cases only (contracts/home-api.md). */
    @Test
    fun theHomeScreenUsesNoBackendApi() {
        val featureDir = File("src/main/java/com/lisdatasolutions/microrag/feature/home")
        val forbidden = listOf(
            "import com.lisdatasolutions.microrag.data.", "import androidx.sqlite", "import androidx.work",
            "import com.google.ai.edge.litertlm", "import java.io.File",
        )
        val files = sources.filter { it.canonicalPath.startsWith(featureDir.canonicalPath + File.separator) }
        val leaks = files.filter { file -> forbidden.any { file.readText().contains(it) } }.map { it.path }

        assertWithMessage("feature/home files exist").that(files).isNotEmpty()
        assertWithMessage("feature/home files importing the data layer, SQLite, WorkManager, LiteRT or File").that(leaks).isEmpty()
    }

    /** F11 U1: the chat reaches the session, the index and Gemma through use cases only (contracts/chat-ui.md §6). */
    @Test
    fun theChatScreenUsesNoBackendApi() {
        val featureDir = File("src/main/java/com/lisdatasolutions/microrag/feature/chat")
        val forbidden = listOf(
            "import com.lisdatasolutions.microrag.data.", "import androidx.sqlite", "import androidx.work",
            "import com.google.ai.edge.litertlm", "import java.io.File",
        )
        val files = sources.filter { it.canonicalPath.startsWith(featureDir.canonicalPath + File.separator) }
        val leaks = files.filter { file -> forbidden.any { file.readText().contains(it) } }.map { it.path }

        assertWithMessage("feature/chat files exist").that(files).isNotEmpty()
        assertWithMessage("feature/chat files importing the data layer, SQLite, WorkManager, LiteRT or File").that(leaks).isEmpty()
    }

    /**
     * F12 U1: Ajustes and Idiomas reach models, history and the app language through use cases only (settings-ui.md §6).
     * F16 U9: Tema reaches the theme the same way, never the system's UiModeManager.
     */
    @Test
    fun theSettingsScreensUseNoBackendApi() {
        val featureDir = File("src/main/java/com/lisdatasolutions/microrag/feature/settings")
        val forbidden = listOf(
            "import com.lisdatasolutions.microrag.data.", "import androidx.sqlite", "import androidx.work",
            "import com.google.ai.edge.litertlm", "import java.io.File", "import android.app.LocaleManager",
            "import android.app.UiModeManager",
        )
        val files = sources.filter { it.canonicalPath.startsWith(featureDir.canonicalPath + File.separator) }
        val leaks = files.filter { file -> forbidden.any { file.readText().contains(it) } }.map { it.path }

        assertWithMessage("feature/settings files exist").that(files).isNotEmpty()
        assertWithMessage("feature/settings files importing the data layer, SQLite, WorkManager, LiteRT, File, LocaleManager or UiModeManager")
            .that(leaks).isEmpty()
    }

    /** F13 U1: Historial reaches the saved conversations and the chat session through use cases only (history-ui.md §6). */
    @Test
    fun theHistoryScreenUsesNoBackendApi() {
        val featureDir = File("src/main/java/com/lisdatasolutions/microrag/feature/history")
        val forbidden = listOf(
            "import com.lisdatasolutions.microrag.data.", "import androidx.sqlite", "import androidx.work",
            "import com.google.ai.edge.litertlm", "import java.io.File", "import android.app.LocaleManager",
        )
        val files = sources.filter { it.canonicalPath.startsWith(featureDir.canonicalPath + File.separator) }
        val leaks = files.filter { file -> forbidden.any { file.readText().contains(it) } }.map { it.path }

        assertWithMessage("feature/history files exist").that(files).isNotEmpty()
        assertWithMessage("feature/history files importing the data layer, SQLite, WorkManager, LiteRT, File or LocaleManager")
            .that(leaks).isEmpty()
    }

    /** F17 U6 (T030): the folder sheets and dialogs reach the folders through use cases only (contract UI §5). */
    @Test
    fun theFolderScreensUseNoBackendApi() {
        val featureDir = File("src/main/java/com/lisdatasolutions/microrag/feature/folders")
        val forbidden = listOf(
            "import com.lisdatasolutions.microrag.data.", "import androidx.sqlite", "import androidx.work",
            "import com.google.ai.edge.litertlm", "import java.io.File", "import android.app.",
        )
        val files = sources.filter { it.canonicalPath.startsWith(featureDir.canonicalPath + File.separator) }
        val leaks = files.filter { file -> forbidden.any { file.readText().contains(it) } }.map { it.path }

        assertWithMessage("feature/folders files exist").that(files).isNotEmpty()
        assertWithMessage("feature/folders files importing the data layer, SQLite, WorkManager, LiteRT, File or a system manager")
            .that(leaks).isEmpty()
    }
}

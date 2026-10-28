package com.lisdatasolutions.microrag.architecture

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** Constitution II: native SDKs stay behind their adapters. */
class LayeringTest {

    private val sources = listOf(File("src/main/java"), File("src/debug/java"))
        .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }

    /** Files that import [prefix] outside [allowedDir]. */
    private fun offenders(prefix: String, allowedDir: File): List<String> = sources
        .filter { it.readText().contains("import $prefix") }
        .filterNot { it.canonicalPath.startsWith(allowedDir.canonicalPath + File.separator) }
        .map { it.path }

    @Test
    fun onlyLiteRtAdaptersImportTheInferenceSdk() {
        val allowedDir = File("src/main/java/com/lisdatasolutions/microrag/rag/runtime/litert")

        assertWithMessage("Files importing LiteRT-LM outside rag/runtime/litert")
            .that(offenders("com.google.ai.edge.litertlm", allowedDir)).isEmpty()
    }

    @Test
    fun onlyTheRagDataLayerImportsSqlite() {
        val allowedDir = File("src/main/java/com/lisdatasolutions/microrag/data/rag")

        assertWithMessage("Files importing androidx.sqlite outside data/rag")
            .that(offenders("androidx.sqlite", allowedDir)).isEmpty()
    }

    @Test
    fun onlyTheIngestDataLayerImportsThePlatformPdfApi() {
        val allowedDir = File("src/main/java/com/lisdatasolutions/microrag/data/ingest")

        assertWithMessage("Files importing android.graphics.pdf outside data/ingest")
            .that(offenders("android.graphics.pdf", allowedDir)).isEmpty()
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
}

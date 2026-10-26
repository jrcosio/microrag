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
}

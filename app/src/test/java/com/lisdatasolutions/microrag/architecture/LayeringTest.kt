package com.lisdatasolutions.microrag.architecture

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** Constitution II: only the LiteRT-LM adapters may touch the inference SDK. */
class LayeringTest {

    @Test
    fun onlyLiteRtAdaptersImportTheInferenceSdk() {
        val allowedDir = File("src/main/java/com/lisdatasolutions/microrag/rag/runtime/litert")
        val offenders = listOf(File("src/main/java"), File("src/debug/java"))
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
            .filter { it.readText().contains("import com.google.ai.edge.litertlm") }
            .filterNot { it.canonicalPath.startsWith(allowedDir.canonicalPath + File.separator) }
            .map { it.path }

        assertWithMessage("Files importing LiteRT-LM outside rag/runtime/litert").that(offenders).isEmpty()
    }
}

package com.lisdatasolutions.microrag.architecture

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Constitution I and F03 FR-026: only the model download package may use network APIs. `android.net.Uri`
 * and `ConnectivityManager` are allowed anywhere because they never open connections.
 */
class NetworkConfinementTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun onlyTheDownloadPackageUsesNetworkApis() {
        val offenders = offenders(
            roots = listOf(File("src/main/java"), File("src/debug/java")),
            allowedDir = File("src/main/java/com/lisdatasolutions/microrag/data/download"),
        )

        assertWithMessage("Network APIs outside data/download").that(offenders).isEmpty()
    }

    @Test
    fun detectsAnOffender() {
        val root = tmp.newFolder("java")
        File(root, "a/Leaky.kt").apply { parentFile!!.mkdirs() }.writeText("package a\nimport java.net.HttpURLConnection\n")
        File(root, "a/Fine.kt").writeText("package a\nimport android.net.Uri\nimport java.net.URI\n")
        File(root, "dl/Client.kt").apply { parentFile!!.mkdirs() }.writeText("package dl\nimport javax.net.ssl.HttpsURLConnection\n")

        assertThat(offenders(listOf(root), File(root, "dl"))).containsExactly(File(root, "a/Leaky.kt").path)
    }

    private fun offenders(roots: List<File>, allowedDir: File): List<String> = roots
        .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.toList() }
        .filterNot { it.canonicalPath.startsWith(allowedDir.canonicalPath + File.separator) }
        .filter { file -> file.readLines().any { line -> line.trim().startsWith("import ") && line.isNetworkImport() } }
        .map { it.path }

    private fun String.isNetworkImport(): Boolean {
        val target = trim().removePrefix("import ").trim()
        if (target == "java.net.URI") return false
        return FORBIDDEN.any { target.startsWith(it) }
    }

    private companion object {
        val FORBIDDEN = listOf("java.net.", "javax.net.", "okhttp3.", "android.net.http.", "java.nio.channels.SocketChannel")
    }
}

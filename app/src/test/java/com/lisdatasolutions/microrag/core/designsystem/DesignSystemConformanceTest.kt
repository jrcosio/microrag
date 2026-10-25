package com.lisdatasolutions.microrag.core.designsystem

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Static checks for SC-008 / FR-001 / FR-002 / FR-032: colors and text sizes only come from the
 * design system theme, and UI text comes from resources (previews may use sample literals).
 */
class DesignSystemConformanceTest {

    private val sourceRoot = File("src/main/java")
    private val sources: List<File> by lazy {
        check(sourceRoot.isDirectory) { "Run from the :app module directory (missing ${sourceRoot.absolutePath})" }
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun hexColors_onlyInThemePackage() =
        assertNoMatches(Regex("""Color\(\s*0x"""), exempt = ::isThemeFile)

    @Test
    fun textSizes_onlyInThemePackage() =
        assertNoMatches(Regex("""\.sp\b"""), exempt = ::isThemeFile)

    @Test
    fun uiTexts_comeFromResources() = assertNoMatches(
        Regex("""\bText\(\s*"|\btext\s*=\s*"|contentDescription\s*=\s*""""),
        exempt = ::isPreviewFile,
    )

    @Test
    fun dynamicColor_isNeverUsed() =
        assertNoMatches(Regex("""dynamic(Light|Dark)ColorScheme"""), exempt = { false })

    private fun isThemeFile(file: File) = file.invariantPath().contains("/core/designsystem/theme/")

    private fun isPreviewFile(file: File) = file.name.endsWith("Previews.kt")

    private fun assertNoMatches(pattern: Regex, exempt: (File) -> Boolean) {
        val offenders = sources.filterNot(exempt).flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                if (pattern.containsMatchIn(code)) "${file.invariantPath()}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertWithMessage("Pattern '$pattern' found outside the allowed files").that(offenders).isEmpty()
    }

    private fun File.invariantPath() = path.replace(File.separatorChar, '/')
}

package com.lisdatasolutions.microrag.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.designsystem.theme.DarkLisdataColors
import com.lisdatasolutions.microrag.core.designsystem.theme.DefaultLisdataGradients
import com.lisdatasolutions.microrag.core.designsystem.theme.LightLisdataColors
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataColors
import com.lisdatasolutions.microrag.core.designsystem.theme.contrastRatio
import org.junit.Test

/**
 * Executable source of the contrast rule (FR-008, SC-002, data-model.md §1.1):
 * 4.5:1 for normal text, 3:1 for large text and graphics.
 */
class ContrastTest {

    @Test
    fun lightTextPairs_meetWcagAa() = assertPairs("light", textPairs(LightLisdataColors), NORMAL_TEXT)

    @Test
    fun darkTextPairs_meetWcagAa() = assertPairs("dark", textPairs(DarkLisdataColors), NORMAL_TEXT)

    @Test
    fun graphicPairs_meetNonTextContrast() {
        assertPairs("light", graphicPairs(LightLisdataColors), LARGE_TEXT_OR_GRAPHIC)
        assertPairs("dark", graphicPairs(DarkLisdataColors), LARGE_TEXT_OR_GRAPHIC)
    }

    @Test
    fun whiteLabelOnCtaGradient_meetsLargeTextContrastEverywhere() {
        val (start, end) = DefaultLisdataGradients.ctaColors
        for (step in 0..SAMPLES) {
            val color = lerp(start, end, step / SAMPLES.toFloat())
            val ratio = contrastRatio(LightLisdataColors.onDarkText, color)
            assertWithMessage("white on CTA gradient at ${step * 100 / SAMPLES} %: ${ratio.format2()}")
                .that(ratio).isAtLeast(LARGE_TEXT_OR_GRAPHIC)
        }
    }

    private fun textPairs(c: LisdataColors) = listOf(
        "textPrimary/background" to (c.textPrimary to c.background),
        "textPrimary/surface" to (c.textPrimary to c.surface),
        "textPrimary/citationHighlight" to (c.textPrimary to c.citationHighlight),
        "textSecondary/background" to (c.textSecondary to c.background),
        "textSecondary/surface" to (c.textSecondary to c.surface),
        "textSecondaryOnContainer/surfaceInfo" to (c.textSecondaryOnContainer to c.surfaceInfo),
        "textSecondaryOnContainer/surfaceInfoAlt" to (c.textSecondaryOnContainer to c.surfaceInfoAlt),
        "textSecondaryOnContainer/neutralTile" to (c.textSecondaryOnContainer to c.neutralTile),
        "blue800/surfaceInfo" to (c.blue800 to c.surfaceInfo),
        "blue800/surfaceInfoAlt" to (c.blue800 to c.surfaceInfoAlt),
        "blue800/citationHighlight" to (c.blue800 to c.citationHighlight),
        "brandBlue/surface" to (c.brandBlue to c.surface),
        "brandBlue/background" to (c.brandBlue to c.background),
        "onBrand/brandBlue" to (c.onBrand to c.brandBlue),
        "success/successContainer" to (c.success to c.successContainer),
        "success/surface" to (c.success to c.surface),
        "warning/warningContainer" to (c.warning to c.warningContainer),
        "error/errorContainer" to (c.error to c.errorContainer),
        "error/surface" to (c.error to c.surface),
        "onDarkText/navy900" to (c.onDarkText to c.navy900),
        "onDarkText/onDarkTile" to (c.onDarkText to c.onDarkTile),
        "onDarkAccent/onDarkChip" to (c.onDarkAccent to c.onDarkChip),
    ) + DefaultLisdataGradients.heroColors.mapIndexed { i, stop ->
        "onDarkAccent/hero[$i]" to (c.onDarkAccent to stop)
    } + DefaultLisdataGradients.welcomeColors.mapIndexed { i, stop ->
        "onDarkAccent/welcome[$i]" to (c.onDarkAccent to stop)
    }

    private fun graphicPairs(c: LisdataColors) = listOf(
        "successGraphic/successContainer" to (c.successGraphic to c.successContainer),
        "brandBlue/surfaceInfoAlt" to (c.brandBlue to c.surfaceInfoAlt),
        "textSecondary/surface (unselected nav icon)" to (c.textSecondary to c.surface),
    )

    private fun assertPairs(theme: String, pairs: List<Pair<String, Pair<Color, Color>>>, minimum: Double) {
        for ((name, colors) in pairs) {
            val ratio = contrastRatio(colors.first, colors.second)
            assertWithMessage("$theme $name: ${ratio.format2()} < $minimum").that(ratio).isAtLeast(minimum)
        }
    }

    private fun Double.format2() = "%.2f".format(this)

    private companion object {
        const val NORMAL_TEXT = 4.5
        const val LARGE_TEXT_OR_GRAPHIC = 3.0
        const val SAMPLES = 20
    }
}

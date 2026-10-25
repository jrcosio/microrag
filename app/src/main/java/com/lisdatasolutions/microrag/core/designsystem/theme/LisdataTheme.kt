package com.lisdatasolutions.microrag.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalLisdataColors = staticCompositionLocalOf { LightLisdataColors }
private val LocalLisdataGradients = staticCompositionLocalOf { DefaultLisdataGradients }
private val LocalLisdataTypography = staticCompositionLocalOf { DefaultLisdataTypography }
private val LocalLisdataSpacing = staticCompositionLocalOf { LisdataSpacing() }
private val LocalLisdataShapes = staticCompositionLocalOf { LisdataShapes() }

/**
 * LISDATA theme. Follows the system dark setting and never uses dynamic (wallpaper) color (FR-002).
 */
@Composable
fun LisdataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkLisdataColors else LightLisdataColors
    val typography = DefaultLisdataTypography
    val shapes = LisdataShapes()
    CompositionLocalProvider(
        LocalLisdataColors provides colors,
        LocalLisdataGradients provides DefaultLisdataGradients,
        LocalLisdataTypography provides typography,
        LocalLisdataSpacing provides LisdataSpacing(),
        LocalLisdataShapes provides shapes,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.material,
            shapes = shapes.material,
            content = content,
        )
    }
}

/** Token accessors for components: `LisdataTheme.colors.brandBlue`, `LisdataTheme.spacing.space4`… */
object LisdataTheme {
    val colors: LisdataColors
        @Composable @ReadOnlyComposable get() = LocalLisdataColors.current
    val gradients: LisdataGradients
        @Composable @ReadOnlyComposable get() = LocalLisdataGradients.current
    val typography: LisdataTypography
        @Composable @ReadOnlyComposable get() = LocalLisdataTypography.current
    val spacing: LisdataSpacing
        @Composable @ReadOnlyComposable get() = LocalLisdataSpacing.current
    val shapes: LisdataShapes
        @Composable @ReadOnlyComposable get() = LocalLisdataShapes.current
}

/** Maps LISDATA tokens to Material 3 roles so standard controls inherit them (research R14, D-018). */
internal fun LisdataColors.toMaterialColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = brandBlue,
        onPrimary = onBrand,
        primaryContainer = surfaceInfo,
        onPrimaryContainer = blue800,
        inversePrimary = brandCyan,
        secondary = blue800,
        onSecondary = if (isDark) navy900 else Color.White,
        secondaryContainer = surfaceInfoAlt,
        onSecondaryContainer = blue800,
        tertiary = successGraphic,
        onTertiary = if (isDark) navy900 else Color.White,
        tertiaryContainer = successContainer,
        onTertiaryContainer = success,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceInfoAlt,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = navy900,
        inverseOnSurface = Color.White,
        error = error,
        onError = if (isDark) navy900 else Color.White,
        errorContainer = errorContainer,
        onErrorContainer = error,
        outline = outline,
        outlineVariant = divider,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = background,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = neutralTile,
    )
}

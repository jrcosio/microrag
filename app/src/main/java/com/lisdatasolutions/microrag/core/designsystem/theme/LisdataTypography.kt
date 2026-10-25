package com.lisdatasolutions.microrag.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lisdatasolutions.microrag.R

/** Google Sans variable font (OFL), one file for all weights (research R4). */
@OptIn(ExperimentalTextApi::class)
val GoogleSans = FontFamily(
    googleSans(FontWeight.Normal),
    googleSans(FontWeight.Medium),
    googleSans(FontWeight.SemiBold),
    googleSans(FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun googleSans(weight: FontWeight) = Font(
    resId = R.font.google_sans_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Material scale plus the LISDATA-specific styles (data-model.md §3). */
@Immutable
data class LisdataTypography(
    val material: Typography,
    val navLabel: TextStyle,
    val navLabelSelected: TextStyle,
    val progressValue: TextStyle,
    val eyebrow: TextStyle,
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = GoogleSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private fun TextStyle.withGoogleSans() = copy(fontFamily = GoogleSans)

private val eyebrowStyle = style(12, 16, FontWeight.Bold, letterSpacing = 0.6)

val DefaultLisdataTypography: LisdataTypography = Typography().let { base ->
    LisdataTypography(
        material = Typography(
            displayLarge = base.displayLarge.withGoogleSans(),
            displayMedium = style(34, 40, FontWeight.Bold),
            displaySmall = base.displaySmall.withGoogleSans(),
            headlineLarge = style(30, 36, FontWeight.Bold),
            headlineMedium = style(26, 32, FontWeight.Bold),
            headlineSmall = base.headlineSmall.withGoogleSans(),
            titleLarge = style(22, 28, FontWeight.Bold),
            titleMedium = style(18, 24, FontWeight.SemiBold),
            titleSmall = base.titleSmall.withGoogleSans(),
            bodyLarge = style(17, 26, FontWeight.Normal),
            bodyMedium = style(15, 22, FontWeight.Normal),
            bodySmall = base.bodySmall.withGoogleSans(),
            labelLarge = style(17, 22, FontWeight.Bold),
            labelMedium = style(13, 18, FontWeight.SemiBold),
            labelSmall = eyebrowStyle,
        ),
        navLabel = style(13, 16, FontWeight.Medium),
        navLabelSelected = style(13, 16, FontWeight.SemiBold),
        progressValue = style(40, 44, FontWeight.Bold),
        eyebrow = eyebrowStyle,
    )
}

package com.lisdatasolutions.microrag.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Gradient tokens (data-model.md §2). Colors are exposed for contrast checks. */
@Immutable
data class LisdataGradients(
    val ctaColors: List<Color>,
    val brandAccentColors: List<Color>,
    val heroColors: List<Color>,
    val welcomeColors: List<Color>,
    val stepProgressColors: List<Color>,
) {
    /** Primary buttons with text; cyan stop darkened for white text >= 3:1 (D-019). */
    val cta: Brush get() = Brush.horizontalGradient(ctaColors)

    /** Text-free accents such as the assistant avatar and sparkle tile. */
    val brandAccent: Brush get() = Brush.linearGradient(brandAccentColors)

    /** Dark hero cards (Home, Document detail, Indexing, Languages). */
    val hero: Brush get() = Brush.linearGradient(heroColors)

    /** Full-screen welcome background. */
    val welcome: Brush get() = Brush.verticalGradient(welcomeColors)

    /** Step indicator fill. */
    val stepProgress: Brush get() = Brush.horizontalGradient(stepProgressColors)
}

val DefaultLisdataGradients = LisdataGradients(
    ctaColors = listOf(Color(0xFF0099D4), Color(0xFF0072DD)),
    brandAccentColors = listOf(Color(0xFF02AEE2), Color(0xFF0072DD)),
    heroColors = listOf(Color(0xFF042B3C), Color(0xFF0155A4)),
    welcomeColors = listOf(Color(0xFF052E45), Color(0xFF023C6E), Color(0xFF01468A)),
    stepProgressColors = listOf(Color(0xFF02ACE1), Color(0xFF0072DD)),
)

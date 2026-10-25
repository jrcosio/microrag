package com.lisdatasolutions.microrag.core.designsystem.model

/**
 * Progress shown to the user. When the total is unknown the UI must use [Indeterminate]
 * instead of inventing a percentage (constitution V).
 */
sealed interface ProgressValue {
    data class Determinate(val fraction: Float) : ProgressValue {
        init {
            require(fraction in 0f..1f) { "Progress fraction must be in [0, 1], was $fraction" }
        }

        val percent: Int get() = (fraction * 100).toInt()
    }

    data object Indeterminate : ProgressValue
}

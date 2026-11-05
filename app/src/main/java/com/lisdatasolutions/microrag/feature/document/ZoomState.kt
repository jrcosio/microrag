package com.lisdatasolutions.microrag.feature.document

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * The page viewer's zoom (F20 research R15, contract U13–U14): from [MIN] to [MAX], with the image never pulled
 * past its edges. Pure state, so its rules run on the JVM; the viewer feeds it gestures and its sizes in pixels.
 */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(MIN)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    /** The viewport and the image as fitted in it, at 1×. */
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var fittedWidth = 0f
    private var fittedHeight = 0f

    fun setSizes(viewportWidth: Float, viewportHeight: Float, fittedWidth: Float, fittedHeight: Float) {
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        this.fittedWidth = fittedWidth
        this.fittedHeight = fittedHeight
        clampOffsets()
    }

    /** A pinch and a drag together. */
    fun transform(zoomChange: Float, panX: Float, panY: Float) {
        scale = (scale * zoomChange).coerceIn(MIN, MAX)
        offsetX += panX
        offsetY += panY
        clampOffsets()
    }

    /** Between 1× and [DOUBLE_TAP]. */
    fun doubleTap() {
        if (scale > MIN) reset() else setScale(DOUBLE_TAP)
    }

    /** TalkBack's «Ampliar» and «Reducir»: steps of 1×. */
    fun zoomIn(): Boolean = setScale(scale + STEP)

    fun zoomOut(): Boolean = setScale(scale - STEP)

    fun reset() {
        scale = MIN
        offsetX = 0f
        offsetY = 0f
    }

    /** False when the scale was already at its limit. */
    private fun setScale(value: Float): Boolean {
        val next = value.coerceIn(MIN, MAX)
        if (next == scale) return false
        scale = next
        clampOffsets()
        return true
    }

    /** Half of what the scaled image exceeds the viewport, on each axis; nothing when it fits. */
    private fun clampOffsets() {
        val maxX = ((fittedWidth * scale - viewportWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((fittedHeight * scale - viewportHeight) / 2f).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    companion object {
        const val MIN = 1f
        const val MAX = 4f
        const val DOUBLE_TAP = 2f
        const val STEP = 1f
    }
}

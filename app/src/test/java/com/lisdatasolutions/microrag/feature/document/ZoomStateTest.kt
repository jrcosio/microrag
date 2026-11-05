package com.lisdatasolutions.microrag.feature.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F20 T035 (contract U13–U14, research R15): the page viewer's zoom. */
class ZoomStateTest {

    /** A 1,000 × 2,000 px viewport with a page fitted to 1,000 × 1,414 px. */
    private fun zoom() = ZoomState().apply { setSizes(1_000f, 2_000f, 1_000f, 1_414f) }

    @Test
    fun theScaleStaysBetweenOneAndFour() {
        val zoom = zoom()

        zoom.transform(zoomChange = 10f, panX = 0f, panY = 0f)
        assertThat(zoom.scale).isEqualTo(4f)
        zoom.transform(zoomChange = 0.01f, panX = 0f, panY = 0f)
        assertThat(zoom.scale).isEqualTo(1f)
    }

    @Test
    fun aDoubleTap_goesToTwo_andBack() {
        val zoom = zoom()

        zoom.doubleTap()
        assertThat(zoom.scale).isEqualTo(2f)
        zoom.transform(zoomChange = 1f, panX = 300f, panY = 0f)
        zoom.doubleTap()
        assertThat(zoom.scale).isEqualTo(1f)
        assertThat(zoom.offsetX).isEqualTo(0f)
    }

    @Test
    fun theImageIsNeverDraggedPastItsEdges() {
        val zoom = zoom()

        // At 1× it fits: no drag at all.
        zoom.transform(zoomChange = 1f, panX = 500f, panY = 500f)
        assertThat(zoom.offsetX).isEqualTo(0f)
        assertThat(zoom.offsetY).isEqualTo(0f)

        // At 2× it is 2,000 × 2,828: 500 px to each side, 414 up and down.
        zoom.doubleTap()
        zoom.transform(zoomChange = 1f, panX = 5_000f, panY = -5_000f)
        assertThat(zoom.offsetX).isEqualTo(500f)
        assertThat(zoom.offsetY).isEqualTo(-414f)

        // Zooming out pulls it back inside (to ±0).
        zoom.zoomOut()
        assertThat(zoom.offsetX).isWithin(0f).of(0f)
        assertThat(zoom.offsetY).isWithin(0f).of(0f)
    }

    @Test
    fun theAccessibilityActions_stepByOne_andSayWhenTheyCannot() {
        val zoom = zoom()

        assertThat(zoom.zoomOut()).isFalse()
        repeat(3) { assertThat(zoom.zoomIn()).isTrue() }
        assertThat(zoom.scale).isEqualTo(4f)
        assertThat(zoom.zoomIn()).isFalse()

        zoom.reset()
        assertThat(zoom.scale).isEqualTo(1f)
    }
}

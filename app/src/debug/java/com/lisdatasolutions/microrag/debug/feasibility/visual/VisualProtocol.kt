package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.lisdatasolutions.microrag.debug.feasibility.MeasurementProtocol
import kotlinx.serialization.Serializable

/**
 * A Gemma combination of the matrix (research R4): its pages, its visual budget, whether pages are split
 * in two tiles, and whether the D-034 text context goes with them. [key] matches the desktop's variant
 * names (`mezcla-p1-b280-t0`); [layout] names the ordered parts of `contexts.json`, which do not depend on
 * the budget.
 */
@Serializable
data class PagesCombination(val pages: Int, val budget: Int?, val tiles: Boolean, val withText: Boolean) {
    val key: String
        get() = if (pages == 0) BASELINE_KEY else "${family()}-p$pages-b$budget-t${if (tiles) 1 else 0}"

    val layout: String
        get() = if (pages == 0) BASELINE_KEY else "${family()}-p$pages-t${if (tiles) 1 else 0}"

    val images: Int get() = pages * if (tiles) 2 else 1

    private fun family() = if (withText) "mezcla" else "paginas"

    companion object {
        const val BASELINE_KEY = "texto"

        /** The D-034 text context alone, with no image: the base the encoder cost is measured against. */
        val BASELINE = PagesCombination(pages = 0, budget = null, tiles = false, withText = true)

        /** Parses a desktop variant key (`mezcla-p2-b140-t0`). */
        fun parse(key: String): PagesCombination {
            if (key == BASELINE_KEY) return BASELINE
            val match = requireNotNull(KEY.matchEntire(key)) { "Not a combination: $key" }
            val (family, pages, budget, tiles) = match.destructured
            return PagesCombination(pages.toInt(), budget.toInt(), tiles == "1", family == "mezcla")
        }

        private val KEY = Regex("(paginas|mezcla)-p(\\d)-b(\\d+)-t([01])")
    }
}

/** Repetitions, the matrix and the resolutions of the visual diagnostic (research R4, R6, R14). */
object VisualProtocol {
    /** Vision signatures of gemma-4-E2B-it.litertlm (ModelInfo, research R2); 280 is its maximum. */
    val GEMMA_BUDGETS = listOf(70, 140, 280)
    const val TILE_BUDGET = 280
    val PAGE_COUNTS = 1..3

    /** Vision signatures of the 440M. */
    val EMBEDDER_TOKENS = listOf(70, 140)
    val PAGE_DIMENSIONS = listOf(256, 512)

    val RESOLUTIONS = listOf(768, 1024, 1536)
    val RASTER_FORMATS = listOf(ImageFormat.WEBP, ImageFormat.JPEG)
    const val QUALITY = 85

    /** Three pages split in two tiles. */
    const val MAX_NUM_IMAGES = 6

    /**
     * Gemma's window with pages: the visual corpus is full of figures, so its D-034 text costs about 2.2 times
     * its estimate (3,471 real tokens for 1,542), and three pages in tiles add 1,680. The text path keeps 4,096.
     */
    const val MAX_NUM_TOKENS = 8192

    val GENERATION = MeasurementProtocol(warmup = 1, measured = 3, maxOutputTokens = 64)
    val EMBEDDING = MeasurementProtocol.EMBEDDING
    const val STAGE_B_QUESTIONS = 20
    const val PROJECTION_PAGES = 50
    const val PROJECTION_TARGET = 200

    /** Stage A: pages alone and with the text context, 1–3 pages × 70/140/280, tiles at 280, and the base. */
    fun stageA(): List<PagesCombination> = buildList {
        add(PagesCombination.BASELINE)
        listOf(false, true).forEach { withText ->
            PAGE_COUNTS.forEach { pages ->
                GEMMA_BUDGETS.forEach { budget -> add(PagesCombination(pages, budget, tiles = false, withText = withText)) }
                add(PagesCombination(pages, TILE_BUDGET, tiles = true, withText = withText))
            }
        }
    }
}

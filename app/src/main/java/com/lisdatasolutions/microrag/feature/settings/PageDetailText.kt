package com.lisdatasolutions.microrag.feature.settings

import androidx.annotation.StringRes
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import java.text.NumberFormat
import java.util.Locale

/** The page detail's name (wireframe §1–§2). */
@StringRes
fun PageDetail.label(): Int = when (this) {
    PageDetail.FAST -> R.string.page_detail_fast
    PageDetail.BALANCED -> R.string.page_detail_balanced
    PageDetail.DETAILED -> R.string.page_detail_detailed
}

/** The wait of a page detail in seconds, as [locale] writes them: «11,5» in Spanish, «11.5» in English (R13). */
fun waitSeconds(tenthsOfSecond: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(tenthsOfSecond / 10.0)

/** The wait of the detail whose page carried [visualTokens], or null for another number. */
fun waitTenthsFor(visualTokens: Int): Int? = PageDetail.entries.firstOrNull { it.visualTokens == visualTokens }?.estimateTenthsOfSecond

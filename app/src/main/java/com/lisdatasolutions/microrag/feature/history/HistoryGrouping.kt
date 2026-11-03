package com.lisdatasolutions.microrag.feature.history

import com.lisdatasolutions.microrag.domain.chat.ConversationSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The date groups of Historial (D-063, data-model §5). */
sealed interface HistoryGroup {
    data object Today : HistoryGroup

    data object Yesterday : HistoryGroup

    /** 2 to 6 calendar days before today. */
    data object Last7Days : HistoryGroup

    data class Month(val year: Int, val month: java.time.Month) : HistoryGroup
}

/** A group and its entries, in the order of the list. */
data class HistorySection(val group: HistoryGroup, val entries: List<ConversationSummary>)

/**
 * Research R3: by calendar days in [zone], so a change of time never moves a row. A last activity after
 * [today] (a clock set back) is Today. The entries come newest first, so each group appears once.
 */
fun groupHistory(entries: List<ConversationSummary>, today: LocalDate, zone: ZoneId): List<HistorySection> {
    val sections = mutableListOf<HistorySection>()
    entries.forEach { entry ->
        val group = historyGroupOf(entry.lastActivityAt, today, zone)
        val last = sections.lastOrNull()
        if (last?.group == group) sections[sections.lastIndex] = last.copy(entries = last.entries + entry)
        else sections += HistorySection(group, listOf(entry))
    }
    return sections
}

fun historyGroupOf(lastActivityAt: Long, today: LocalDate, zone: ZoneId): HistoryGroup {
    val day = Instant.ofEpochMilli(lastActivityAt).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> HistoryGroup.Today
        days == 1L -> HistoryGroup.Yesterday
        days <= LAST_DAYS -> HistoryGroup.Last7Days
        else -> HistoryGroup.Month(day.year, day.month)
    }
}

/** «Últimos 7 días» counts today: today and yesterday have their own groups, then 2 to 6 days before. */
private const val LAST_DAYS = 6L

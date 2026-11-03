package com.lisdatasolutions.microrag.feature.history

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.chat.ConversationSummary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import org.junit.Test

/** F13 T012 (research R3, data-model §5): the date groups of Historial. */
class HistoryGroupingTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val utc = ZoneId.of("UTC")
    private val kiritimati = ZoneId.of("Pacific/Kiritimati")

    private fun at(text: String, zone: ZoneId): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: Long, at: Long) = ConversationSummary(id, "¿$id?", at, 1)

    @Test
    fun eachGroupHasItsLimits() {
        val today = LocalDate.of(2026, 11, 3)
        fun group(text: String) = historyGroupOf(at(text, madrid), today, madrid)

        assertThat(group("2026-11-03T00:00")).isEqualTo(HistoryGroup.Today)
        assertThat(group("2026-11-03T23:59:59.999")).isEqualTo(HistoryGroup.Today)
        assertThat(group("2026-11-02T23:59:59.999")).isEqualTo(HistoryGroup.Yesterday)
        assertThat(group("2026-11-02T00:00")).isEqualTo(HistoryGroup.Yesterday)
        assertThat(group("2026-11-01T23:59")).isEqualTo(HistoryGroup.Last7Days)
        assertThat(group("2026-10-28T00:00")).isEqualTo(HistoryGroup.Last7Days)
        assertThat(group("2026-10-27T23:59")).isEqualTo(HistoryGroup.Month(2026, Month.OCTOBER))
        assertThat(group("2025-12-31T12:00")).isEqualTo(HistoryGroup.Month(2025, Month.DECEMBER))
    }

    @Test
    fun aDateAfterToday_isToday() {
        assertThat(historyGroupOf(at("2026-11-05T09:00", madrid), LocalDate.of(2026, 11, 3), madrid)).isEqualTo(HistoryGroup.Today)
    }

    /** The clocks went back on 2026-10-25 in Madrid: that day has 25 hours, and it is still one day. */
    @Test
    fun aChangeOfTime_neverMovesARow() {
        val today = LocalDate.of(2026, 10, 26)

        assertThat(historyGroupOf(at("2026-10-25T00:30", madrid), today, madrid)).isEqualTo(HistoryGroup.Yesterday)
        assertThat(historyGroupOf(at("2026-10-25T23:30", madrid), today, madrid)).isEqualTo(HistoryGroup.Yesterday)
        assertThat(historyGroupOf(at("2026-10-26T00:10", madrid), today, madrid)).isEqualTo(HistoryGroup.Today)
    }

    @Test
    fun theSameMoment_fallsOnTheDayOfTheZone() {
        // 2026-11-02 12:00 UTC is already 2026-11-03 02:00 in Kiritimati (+14 h).
        val moment = at("2026-11-02T12:00", utc)

        assertThat(historyGroupOf(moment, LocalDate.of(2026, 11, 3), utc)).isEqualTo(HistoryGroup.Yesterday)
        assertThat(historyGroupOf(moment, LocalDate.of(2026, 11, 3), kiritimati)).isEqualTo(HistoryGroup.Today)
    }

    @Test
    fun eachGroupAppearsOnce_inOrder_andEmptyOnesAreLeftOut() {
        val today = LocalDate.of(2026, 11, 3)
        val entries = listOf(
            entry(1, at("2026-11-03T10:42", madrid)),
            entry(2, at("2026-11-03T08:15", madrid)),
            entry(3, at("2026-10-30T18:05", madrid)),
            entry(4, at("2026-10-12T11:00", madrid)),
            entry(5, at("2026-10-01T09:00", madrid)),
            entry(6, at("2026-09-30T09:00", madrid)),
        )

        val sections = groupHistory(entries, today, madrid)

        assertThat(sections.map { it.group }).containsExactly(
            HistoryGroup.Today, HistoryGroup.Last7Days, HistoryGroup.Month(2026, Month.OCTOBER), HistoryGroup.Month(2026, Month.SEPTEMBER),
        ).inOrder()
        assertThat(sections.map { s -> s.entries.map { it.id } }).containsExactly(listOf(1L, 2L), listOf(3L), listOf(4L, 5L), listOf(6L)).inOrder()
    }

    @Test
    fun noEntries_noSections() {
        assertThat(groupHistory(emptyList(), LocalDate.of(2026, 11, 3), madrid)).isEmpty()
    }
}

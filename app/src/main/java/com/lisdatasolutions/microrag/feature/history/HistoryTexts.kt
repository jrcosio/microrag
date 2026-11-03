package com.lisdatasolutions.microrag.feature.history

import android.content.res.Resources
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.domain.chat.ConversationSummary
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Research R3 (contracts/history-ui.md §2): the texts of Historial in the app's language: the group
 * headers, each row's meta line and the sentence TalkBack reads for it. Dates are shown in [zone], the
 * zone that grouped them, and the hour follows the system's 12/24 h setting.
 */
class HistoryTexts(private val resources: Resources, locale: Locale, is24Hour: Boolean, private val zone: ZoneId) {

    private val time = format(locale, if (is24Hour) "Hm" else "hm")
    private val weekday = format(locale, "EEEE")
    private val dayMonth = format(locale, "dMMM")
    private val longDate = format(locale, "dMMMMy")
    private val monthYear = format(locale, "MMMMy")

    /** ICU, so the stand-alone forms of the best patterns («cccc», «LLLL») work. */
    private fun format(locale: Locale, skeleton: String) =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).apply { timeZone = TimeZone.getTimeZone(zone.id) }

    fun header(group: HistoryGroup): String = when (group) {
        HistoryGroup.Today -> resources.getString(R.string.history_today)
        HistoryGroup.Yesterday -> resources.getString(R.string.history_yesterday)
        HistoryGroup.Last7Days -> resources.getString(R.string.history_last_7_days)
        // Mid-month, so no zone moves the date into the next or the previous one.
        is HistoryGroup.Month -> monthYear.format(Date.from(LocalDate.of(group.year, group.month, 15).atStartOfDay(zone).toInstant()))
    }

    /** «10:42 · 3 preguntas», «lunes · 2 preguntas», «12 oct. · 5 preguntas». */
    fun meta(entry: ConversationSummary, group: HistoryGroup): String {
        val at = Date(entry.lastActivityAt)
        val date = when (group) {
            HistoryGroup.Today, HistoryGroup.Yesterday -> time.format(at)
            HistoryGroup.Last7Days -> weekday.format(at)
            is HistoryGroup.Month -> dayMonth.format(at)
        }
        return resources.getString(R.string.history_meta, date, questions(entry.questions))
    }

    /** «¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas.», with « Abierta.» for the chat's one. */
    fun sentence(entry: ConversationSummary, group: HistoryGroup, open: Boolean): String {
        val at = Date(entry.lastActivityAt)
        val date = when (group) {
            HistoryGroup.Today -> resources.getString(R.string.history_today_at, time.format(at))
            HistoryGroup.Yesterday -> resources.getString(R.string.history_yesterday_at, time.format(at))
            HistoryGroup.Last7Days -> resources.getString(R.string.history_weekday_at, weekday.format(at), time.format(at))
            is HistoryGroup.Month -> longDate.format(at)
        }
        val template = if (open) R.string.history_row_sentence_open else R.string.history_row_sentence
        return resources.getString(template, ended(entry.title), date, questions(entry.questions))
    }

    fun deleteLabel(entry: ConversationSummary): String = resources.getString(R.string.history_delete_label, entry.title)

    private fun questions(count: Int): String = resources.getQuantityString(R.plurals.history_questions, count, count)

    /** A title already ending a sentence gets no second full stop (analyze AN-11). */
    private fun ended(title: String): String = if (title.lastOrNull() in SENTENCE_ENDS) title else "$title."

    private companion object {
        val SENTENCE_ENDS = setOf('?', '!', '.', '…')
    }
}

/** The texts for this composition's language and 12/24 h setting, in [zone]. */
@Composable
fun rememberHistoryTexts(zone: ZoneId): HistoryTexts {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(resources, configuration, zone, is24Hour) { HistoryTexts(resources, configuration.locales[0], is24Hour, zone) }
}

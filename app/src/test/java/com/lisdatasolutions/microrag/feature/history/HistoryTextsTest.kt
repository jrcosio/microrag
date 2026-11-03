package com.lisdatasolutions.microrag.feature.history

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.chat.ConversationSummary
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F13 T013 (research R3, contracts/history-ui.md §2): the row texts in Spanish and English, 12 and 24 h. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class HistoryTextsTest {

    private val zone = ZoneId.of("Europe/Madrid")

    private fun texts(language: String, is24Hour: Boolean = true): HistoryTexts {
        val locale = Locale.forLanguageTag(language)
        val base = ApplicationProvider.getApplicationContext<Application>()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return HistoryTexts(base.createConfigurationContext(configuration).resources, locale, is24Hour, zone)
    }

    private fun entry(title: String, at: String, questions: Int = 3) =
        ConversationSummary(1, title, LocalDateTime.parse(at).atZone(zone).toInstant().toEpochMilli(), questions)

    private val question = entry("¿Cuál es el plazo de devolución?", "2026-11-03T10:42")

    @Test
    fun spanish_metaLines_byGroup() {
        val es = texts("es")

        assertThat(es.meta(question, HistoryGroup.Today)).isEqualTo("10:42 · 3 preguntas")
        assertThat(es.meta(entry("Resume el capítulo 2", "2026-11-02T18:05", questions = 1), HistoryGroup.Yesterday)).isEqualTo("18:05 · 1 pregunta")
        assertThat(es.meta(entry("Diferencias", "2026-10-26T18:05", questions = 2), HistoryGroup.Last7Days)).isEqualTo("lunes · 2 preguntas")
        assertThat(es.meta(entry("Vacaciones", "2026-10-12T11:00", questions = 5), HistoryGroup.Month(2026, Month.OCTOBER)))
            .startsWith("12 oct")
    }

    @Test
    fun spanish_sentences_neverPutAFullStopAfterAQuestionMark() {
        val es = texts("es")

        assertThat(es.sentence(question, HistoryGroup.Today, open = false)).isEqualTo("¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas.")
        assertThat(es.sentence(question, HistoryGroup.Today, open = true)).isEqualTo("¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas. Abierta.")
        assertThat(es.sentence(entry("Resume el capítulo 2", "2026-11-02T18:05", questions = 1), HistoryGroup.Yesterday, open = false))
            .isEqualTo("Resume el capítulo 2. Ayer, 18:05. 1 pregunta.")
        assertThat(es.sentence(entry("Diferencias", "2026-10-26T18:05", questions = 2), HistoryGroup.Last7Days, open = false))
            .isEqualTo("Diferencias. lunes, 18:05. 2 preguntas.")
        assertThat(es.sentence(entry("Vacaciones", "2026-10-12T11:00", questions = 5), HistoryGroup.Month(2026, Month.OCTOBER), open = false))
            .isEqualTo("Vacaciones. 12 de octubre de 2026. 5 preguntas.")
        assertThat(es.deleteLabel(question)).isEqualTo("Borrar conversación: ¿Cuál es el plazo de devolución?")
    }

    @Test
    fun spanish_headers() {
        val es = texts("es")

        assertThat(es.header(HistoryGroup.Today)).isEqualTo("Hoy")
        assertThat(es.header(HistoryGroup.Yesterday)).isEqualTo("Ayer")
        assertThat(es.header(HistoryGroup.Last7Days)).isEqualTo("Últimos 7 días")
        assertThat(es.header(HistoryGroup.Month(2026, Month.OCTOBER))).isEqualTo("octubre de 2026")
    }

    @Test
    fun english_textsAndHeaders() {
        val en = texts("en", is24Hour = false)
        val afternoon = entry("What is the return period?", "2026-11-03T15:05", questions = 1)

        assertThat(en.meta(afternoon, HistoryGroup.Today)).matches("3:05\\sPM · 1 question")
        assertThat(en.sentence(afternoon, HistoryGroup.Today, open = true)).matches("What is the return period\\? Today, 3:05\\sPM\\. 1 question\\. Open\\.")
        assertThat(en.meta(entry("Notes", "2026-10-26T18:05", questions = 2), HistoryGroup.Last7Days)).isEqualTo("Monday · 2 questions")
        assertThat(en.meta(entry("Notes", "2026-10-12T11:00", questions = 2), HistoryGroup.Month(2026, Month.OCTOBER))).isEqualTo("Oct 12 · 2 questions")
        assertThat(en.header(HistoryGroup.Month(2026, Month.OCTOBER))).isEqualTo("October 2026")
        assertThat(en.header(HistoryGroup.Last7Days)).isEqualTo("Last 7 days")
        assertThat(en.deleteLabel(afternoon)).isEqualTo("Delete conversation: What is the return period?")
    }

    @Test
    fun the12HourClock_isUsedWhenTheSystemAsksForIt() {
        val es = texts("es", is24Hour = false)

        assertThat(es.meta(entry("Tarde", "2026-11-03T15:05"), HistoryGroup.Today)).doesNotContain("15:05")
        assertThat(es.meta(entry("Tarde", "2026-11-03T15:05"), HistoryGroup.Today)).contains("3:05")
    }
}

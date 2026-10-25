package com.lisdatasolutions.microrag.debug.feasibility

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Plain Application: the real one starts Koin, which must not run once per test.
@Config(application = Application::class)
@RunWith(AndroidJUnit4::class)
class FeasibilityTextsTest {

    private val texts = runBlocking {
        AssetFeasibilityTexts(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined).load()
    }

    @Test
    fun embeddingTexts_haveTheExpectedShape() {
        assertThat(texts.queries).hasSize(10)
        assertThat(texts.documents).hasSize(10)
        (texts.queries + texts.documents).forEach { assertThat(it.isNotBlank()).isTrue() }
        assertThat(texts.documentHeader).contains("›")
        // ~350 tokens; the real token count is confirmed on the phone.
        assertThat(texts.measuredDocument.length).isIn(1000..2500)
    }

    @Test
    fun prompt_followsTheReportTemplateWithSixSources() {
        assertThat(texts.prompt).startsWith("Responde a la PREGUNTA usando únicamente las FUENTES.")
        (1..6).forEach { assertThat(texts.prompt).contains("[$it] ") }
        assertThat(texts.prompt).doesNotContain("[7] ")
        assertThat(texts.prompt).contains("\nPREGUNTA: ")
    }
}

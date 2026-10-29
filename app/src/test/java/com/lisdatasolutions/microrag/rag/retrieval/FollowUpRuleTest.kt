package com.lisdatasolutions.microrag.rag.retrieval

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** F10 T026 (research R2, FR-007): the cases of offline/tests/test_retrieval.py, and our own. */
class FollowUpRuleTest {

    // --- test_retrieval.py

    @Test
    fun shortOrConnectorQuestionsAreFollowUps() {
        assertThat(FollowUpRule.isFollowUp("¿Y para empresas?")).isTrue()
        assertThat(FollowUpRule.isFollowUp("pero entonces cuál es el plazo para presentar la declaración del impuesto anual")).isTrue()
        // 9 normalized tokens: plaz, presentar, declaracion, trimestral, impuest, valor, anadid, empres, exportador.
        assertThat(
            FollowUpRule.isFollowUp(
                "¿Cuál es el plazo para presentar la declaración trimestral del impuesto sobre el valor añadido de las empresas exportadoras?",
            ),
        ).isFalse()
    }

    @Test
    fun searchTextPrependsThePreviousQuestion() {
        assertThat(FollowUpRule.searchText("¿Y el aceite?", "¿Cada cuánto se revisa la bomba?", enabled = true))
            .isEqualTo("¿Cada cuánto se revisa la bomba? ¿Y el aceite?")
        assertThat(FollowUpRule.searchText("¿Y el aceite?", "¿Cada cuánto se revisa la bomba?", enabled = false)).isEqualTo("¿Y el aceite?")
        assertThat(FollowUpRule.searchText("¿Y el aceite?", null, enabled = true)).isEqualTo("¿Y el aceite?")
    }

    // --- our own cases

    @Test
    fun everyConnector_inAnyCase_afterLeadingSpaces() {
        val longEnough = "el filtro de aire del motor principal del camión grande cada semana"
        listOf("Y además", "  ¿Y si cambio", "PERO", "\tEntonces qué pasa con", "¿y", "y si").forEach { start ->
            assertWithMessage(start).that(FollowUpRule.isFollowUp("$start $longEnough")).isTrue()
        }
        assertThat(FollowUpRule.isFollowUp("Cambiar $longEnough")).isFalse()
        // A connector must start the question: «ya» is not «y ».
        assertThat(FollowUpRule.isFollowUp("Ya cambié $longEnough")).isFalse()
    }

    @Test
    fun sevenTermsAreAFollowUp_eightAreNot() {
        assertThat(FollowUpRule.isFollowUp("bombas hidráulicas motores filtros válvulas aceite correas")).isTrue()
        assertThat(FollowUpRule.isFollowUp("bombas hidráulicas motores filtros válvulas aceite correas juntas")).isFalse()
    }

    @Test
    fun withoutAPreviousQuestion_orWithTheRuleOff_theQuestionIsSearchedAlone() {
        assertThat(FollowUpRule.searchText("¿Y el aceite?", "", enabled = true)).isEqualTo("¿Y el aceite?")
        assertThat(FollowUpRule.searchText(LONG, "¿Cada cuánto se revisa la bomba?", enabled = true)).isEqualTo(LONG)
        assertThat(FollowUpRule.searchText("¿Y el aceite?", "Anterior")).isEqualTo("Anterior ¿Y el aceite?")
    }

    private companion object {
        const val LONG = "¿Cuál es el plazo para presentar la declaración trimestral del impuesto sobre el valor añadido de las empresas exportadoras?"
    }
}

package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F10 T009 (research R4): a query too long for the model loses its previous question first, then its own end. */
class QueryOverflowTest {

    @Test
    fun joinPutsThePreviousQuestionFirst() {
        assertThat(QueryOverflow.join("¿Cada cuánto se revisa la bomba?", "¿Y el aceite?"))
            .isEqualTo("¿Cada cuánto se revisa la bomba? ¿Y el aceite?")
        assertThat(QueryOverflow.join(null, "¿Y el aceite?")).isEqualTo("¿Y el aceite?")
    }

    @Test
    fun thePreviousQuestionIsCutFirst_withTheD036Rule() {
        val previous = "uno dos tres cuatro cinco seis siete ocho nueve diez"
        val step = QueryOverflow.next(previous, QUESTION)
        assertThat(step.previous).isEqualTo(EmbeddingOverflow.cut(previous))
        assertThat(step.question).isEqualTo(QUESTION)
    }

    @Test
    fun anEmptyPreviousQuestionIsDropped_thenTheQuestionIsCut() {
        val dropped = QueryOverflow.next("a", QUESTION)
        assertThat(dropped.previous).isNull()
        assertThat(dropped.question).isEqualTo(QUESTION)

        val cut = QueryOverflow.next(null, QUESTION)
        assertThat(cut.previous).isNull()
        assertThat(cut.question).isEqualTo(EmbeddingOverflow.cut(QUESTION))
    }

    @Test
    fun theQuestionStaysWholeWhileThereIsAPreviousOne() {
        var step = QueryOverflow.Step("tema anterior", QUESTION)
        val texts = mutableListOf(QueryOverflow.join(step.previous, step.question))
        repeat(EmbeddingOverflow.MAX_ATTEMPTS - 1) {
            step = QueryOverflow.next(step.previous, step.question)
            texts += QueryOverflow.join(step.previous, step.question)
        }
        val whileThereWasAPrevious = texts.takeWhile { it != QUESTION }
        assertThat(whileThereWasAPrevious).isNotEmpty()
        whileThereWasAPrevious.forEach { assertThat(it).endsWith(" $QUESTION") }
        // Every attempt is shorter than the one before: the loop cannot repeat a rejected text.
        texts.zipWithNext().forEach { (before, after) -> assertThat(after.length).isLessThan(before.length) }
        assertThat(texts.last().length).isLessThan(QUESTION.length)
    }

    private companion object {
        const val QUESTION = "¿Y cada cuánto se cambia el aceite del motor principal?"
    }
}

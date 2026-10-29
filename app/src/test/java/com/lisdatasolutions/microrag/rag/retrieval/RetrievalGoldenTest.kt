package com.lisdatasolutions.microrag.rag.retrieval

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * F10 T022 (research R3, SC-001): the 426 live questions of the evaluation set, replayed by the desktop
 * harness on its reference index, give the same fusion and the same blocks with the Kotlin port.
 */
class RetrievalGoldenTest {

    private val cases = RetrievalGoldenFiles.cases(File("../offline/golden/${RetrievalGoldenFiles.GOLDEN}").readText())

    private val selectors = mapOf(
        "d034" to ContextSelector(),
        // The report's starting values, to exercise the neighbour expansion.
        "report" to ContextSelector(maxBlocks = 6, maxPerDocument = 3, budgetTokens = 2_200, expandTop = 2),
    )

    @Test
    fun everyLiveQuestionIsThere() {
        assertThat(cases).hasSize(426)
        assertThat(cases.map { it.id }).isInStrictOrder()
    }

    @Test
    fun theFollowUpRuleAndTheSearchTextMatchTheDesktop() {
        cases.forEach { case ->
            assertWithMessage(case.id).that(FollowUpRule.isFollowUp(case.question)).isEqualTo(case.followUp)
            assertWithMessage(case.id).that(FollowUpRule.searchText(case.question, case.previous)).isEqualTo(case.searchText)
        }
    }

    @Test
    fun theFusionMatchesTheDesktop() {
        cases.forEach { case ->
            val fused = ReciprocalRankFusion.fuse(listOf(case.vector, case.fts), RagParameters.RRF_K).take(RagParameters.FUSED_LIMIT)
            assertWithMessage(case.id).that(fused.map { it.id }).containsExactlyElementsIn(case.fused).inOrder()
        }
    }

    @Test
    fun theBlocksMatchTheDesktop_withD034AndWithNeighbours() = runBlocking {
        cases.forEach { case ->
            val views = case.chunks.values.map { chunk ->
                ChunkView(
                    id = chunk.id, documentId = chunk.documentId, ordinal = chunk.ordinal, headingPath = null, page = null,
                    content = "", tokenEstimate = chunk.tokens, documentName = "",
                )
            }
            val byPosition = views.associateBy { it.documentId to it.ordinal }
            val ranked = case.fused.map { id -> views.first { it.id == id } }
            selectors.forEach { (name, selector) ->
                val blocks = selector.select(ranked) { doc, ordinal -> byPosition[doc to ordinal] }
                val expected = case.blocks(name)
                assertWithMessage("${case.id} $name").that(blocks.map { listOf(it.documentId, it.firstOrdinal, it.lastOrdinal, it.tokens, it.chunkIds) })
                    .containsExactlyElementsIn(expected.map { listOf(it.documentId, it.firstOrdinal, it.lastOrdinal, it.tokens, it.chunkIds) })
                    .inOrder()
            }
        }
    }

    @Test
    fun theGoldenExercisesTheRules() {
        val report = cases.flatMap { it.blocks("report") }
        assertWithMessage("blocks with a neighbour").that(report.count { it.lastOrdinal > it.firstOrdinal }).isGreaterThan(0)
        assertWithMessage("questions that fill the D-034 blocks").that(cases.count { it.blocks("d034").size == RagParameters.MAX_BLOCKS })
            .isGreaterThan(0)
        assertWithMessage("follow-ups").that(cases.count { it.previous != null && it.searchText != it.question }).isGreaterThan(0)
    }
}

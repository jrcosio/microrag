package com.lisdatasolutions.microrag.rag.retrieval

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F10 T020 (research R2): the cases of offline/tests/test_retrieval.py, and what the diagnostic reads. */
class ReciprocalRankFusionTest {

    @Test
    fun reciprocalRankFusionByHand() {
        // k = 60, ranks from 1: a = 1/61 + 1/63, b = 1/62 + 1/61, c = 1/63, d = 1/62.
        val fused = ReciprocalRankFusion.fuse(listOf(listOf(A, B, C), listOf(B, D, A)), k = 60)

        assertThat(fused.map { it.id }).containsExactly(B, A, D, C).inOrder()
        assertThat(fused.first { it.id == A }.score).isEqualTo(1.0 / 61 + 1.0 / 63)
        assertThat(fused.first { it.id == D }.score).isEqualTo(1.0 / 62)
    }

    @Test
    fun tiesKeepTheBestRankThenTheSmallerId() {
        assertThat(ReciprocalRankFusion.fuse(listOf(listOf(X, Y), listOf(Y, X)), k = 60).map { it.id }).containsExactly(X, Y).inOrder()
        // Same score and same best rank: the smaller id first.
        assertThat(ReciprocalRankFusion.fuse(listOf(listOf(9L), listOf(4L)), k = 60).map { it.id }).containsExactly(4L, 9L).inOrder()
    }

    @Test
    fun ranksStartAtOneAndAreKeptPerList() {
        val fused = ReciprocalRankFusion.fuse(listOf(listOf(A, B, C), listOf(B, D, A)), k = 60).associateBy { it.id }

        assertThat(fused.getValue(A).ranks).containsExactly(1, 3).inOrder()
        assertThat(fused.getValue(A).bestRank).isEqualTo(1)
        assertThat(fused.getValue(C).ranks).containsExactly(3, null).inOrder()
        assertThat(fused.getValue(D).ranks).containsExactly(null, 2).inOrder()
        assertThat(fused.getValue(D).bestRank).isEqualTo(2)
    }

    @Test
    fun anIdInOneListOnly_andEmptyLists() {
        assertThat(ReciprocalRankFusion.fuse(listOf(emptyList(), listOf(D)), k = 60).single().ranks).containsExactly(null, 1).inOrder()
        assertThat(ReciprocalRankFusion.fuse(listOf(emptyList(), emptyList()), k = 60)).isEmpty()
    }

    @Test
    fun theDefaultKIsTheSharedParameter() {
        assertThat(ReciprocalRankFusion.fuse(listOf(listOf(A))).single().score).isEqualTo(1.0 / 61)
    }

    private companion object {
        const val A = 1L
        const val B = 2L
        const val C = 3L
        const val D = 4L
        const val X = 10L
        const val Y = 11L
    }
}

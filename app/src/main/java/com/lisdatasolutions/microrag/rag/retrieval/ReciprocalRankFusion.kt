package com.lisdatasolutions.microrag.rag.retrieval

import com.lisdatasolutions.microrag.domain.rag.RagParameters

/** One id of the fusion: its RRF score, its best single rank and its rank in each list (null: not there). */
data class FusedCandidate(val id: Long, val score: Double, val bestRank: Int, val ranks: List<Int?>)

/**
 * Reciprocal Rank Fusion, exact port of `rrf` in offline/eval/retrieval.py (F10 research R2). Ranks
 * start at 1, and scores add up in list order (vector first) so ties are bit for bit those of the desktop.
 */
object ReciprocalRankFusion {

    /** Every id of [lists], best first: higher score, then better single rank, then smaller id. */
    fun fuse(lists: List<List<Long>>, k: Int = RagParameters.RRF_K): List<FusedCandidate> {
        val scores = LinkedHashMap<Long, Double>()
        val ranks = HashMap<Long, Array<Int?>>()
        lists.forEachIndexed { list, ranked ->
            ranked.forEachIndexed { position, id ->
                val rank = position + 1
                scores[id] = (scores[id] ?: 0.0) + 1.0 / (k + rank)
                val perList = ranks.getOrPut(id) { arrayOfNulls(lists.size) }
                if (perList[list] == null) perList[list] = rank
            }
        }
        return scores.map { (id, score) ->
            val perList = ranks.getValue(id).toList()
            FusedCandidate(id, score, perList.filterNotNull().min(), perList)
        }.sortedWith(compareByDescending<FusedCandidate> { it.score }.thenBy { it.bestRank }.thenBy { it.id })
    }
}

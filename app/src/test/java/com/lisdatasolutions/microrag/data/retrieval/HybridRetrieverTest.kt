package com.lisdatasolutions.microrag.data.retrieval

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.rag.retrieval.ContextSelector
import com.lisdatasolutions.microrag.rag.retrieval.ReciprocalRankFusion
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F10 T024 (research R6, R8): the main path of a retrieval, against the desktop algorithm. */
class HybridRetrieverTest {

    private val index = FakeRagIndex()
    private val embedder = FakeQueryEmbedder()
    private val logger = RecordingLogger()
    private val retriever = HybridRetriever(index, embedder, logger)

    /** Documents of [CHUNKS] chunks each; returns their chunk ids in document and ordinal order. */
    private suspend fun documents(vararg names: String, status: DocumentStatus = DocumentStatus.DONE, included: Boolean = true): List<Long> =
        names.flatMap { name ->
            val id = index.addDocument(NewDocument(displayName = "$name.txt", contentHash = name))
            index.insertChunks(id, (0 until CHUNKS).map {
                ChunkInput(it, "$name › Sección $it", page = it + 1, content = "$name, fragmento $it.", tokenEstimate = 100 + it, embedding = FloatArray(0))
            })
            index.updateStatus(id, status)
            if (!included) index.setIncluded(id, false)
            (0 until CHUNKS).map { index.chunkId(id, it) }
        }

    private fun program(vector: List<Long>, text: List<Long>) {
        index.vectorResults = { vector.mapIndexed { i, id -> ScoredChunk(id, 0.1 + i / 1000.0) } }
        index.textResults = { text }
    }

    private fun blocksOf(result: RetrievalResult) = (result.outcome as RetrievalOutcome.Blocks)

    @Test
    fun aQuestionGetsTheDesktopFusionAndSelection() = runTest {
        val ids = documents("Bombas", "Aceite", "Filtros")
        val vector = ids.shuffled(kotlin.random.Random(1)).take(60)
        val text = ids.shuffled(kotlin.random.Random(2)).take(60)
        program(vector, text)

        val result = retriever.retrieve(RetrievalRequest(QUESTION))

        assertThat(index.calls).containsAtLeast("vector:${RagParameters.CANDIDATES}", "text:${RagParameters.CANDIDATES}:$QUESTION")
        val fused = ReciprocalRankFusion.fuse(listOf(vector.take(50), text.take(50))).take(RagParameters.FUSED_LIMIT)
        assertThat(index.calls).contains("load:searchable:" + fused.joinToString(",") { it.id.toString() })

        val outcome = blocksOf(result)
        val views = index.loadChunks(fused.map { it.id })
        val expected = ContextSelector().select(fused.map { views.getValue(it.id) }) { _, _ -> null }
        assertThat(outcome.blocks).isEqualTo(expected)
        assertThat(outcome.blocks).isNotEmpty()
        val first = outcome.blocks.first()
        val firstChunk = views.getValue(first.chunkIds.first())
        assertThat(first.documentName).isEqualTo(firstChunk.documentName)
        assertThat(first.headingPath).isEqualTo(firstChunk.headingPath)
        assertThat(first.page).isEqualTo(firstChunk.page)
        assertThat(first.text).startsWith(firstChunk.content)

        assertThat(outcome.candidates.map { it.chunkId }).containsExactlyElementsIn(fused.map { it.id }).inOrder()
        val selected = outcome.blocks.flatMap { it.chunkIds }.toSet()
        outcome.candidates.forEachIndexed { i, candidate ->
            val fusedCandidate = fused[i]
            assertThat(candidate.fusedRank).isEqualTo(i + 1)
            assertThat(candidate.rrfScore).isEqualTo(fusedCandidate.score)
            assertThat(candidate.vectorRank).isEqualTo(fusedCandidate.ranks[0])
            assertThat(candidate.textRank).isEqualTo(fusedCandidate.ranks[1])
            assertThat(candidate.vectorDistance).isEqualTo(candidate.vectorRank?.let { 0.1 + (it - 1) / 1000.0 })
            assertThat(candidate.selected).isEqualTo(candidate.chunkId in selected)
        }
        assertThat(result.searchText).isEqualTo(QUESTION)
        assertThat(result.followUp).isFalse()
        assertThat(result.embeddingCuts).isEqualTo(0)
    }

    @Test
    fun timingsAreMeasured_andTheFirstRetrievalLoadsTheEngine() = runTest {
        val ids = documents("Bombas")
        program(ids, ids.reversed())

        val first = retriever.retrieve(RetrievalRequest(QUESTION)).timings
        val second = retriever.retrieve(RetrievalRequest(QUESTION)).timings

        assertThat(first.coldEngine).isTrue()
        assertThat(second.coldEngine).isFalse()
        listOf(first.embeddingMs, first.vectorMs, first.textMs, first.selectionMs, first.totalMs).forEach { assertThat(it).isAtLeast(0.0) }
        assertThat(first.totalMs).isAtLeast(first.embeddingMs)
    }

    @Test
    fun theLexicalSearchStartsBeforeTheEmbeddingEnds() = runBlocking<Unit> {
        val ids = documents("Bombas")
        program(ids, ids)
        var sawTextSearch = false
        embedder.engine.beforeEmbedding = {
            val deadline = System.nanoTime() + 5_000_000_000
            while (index.calls.none { it.startsWith("text:") } && System.nanoTime() < deadline) Thread.sleep(5)
            sawTextSearch = index.calls.any { it.startsWith("text:") }
        }

        val result = retriever.retrieve(RetrievalRequest(QUESTION))

        assertThat(sawTextSearch).isTrue()
        assertThat(result.outcome).isInstanceOf(RetrievalOutcome.Blocks::class.java)
        assertThat(index.calls.indexOfFirst { it.startsWith("vector:") }).isGreaterThan(index.calls.indexOfFirst { it.startsWith("text:") })
    }

    @Test
    fun aQuestionOfStopwordsOnly_isDecidedByTheVectorSearch() = runTest {
        val ids = documents("Bombas", "Aceite")
        program(vector = ids, text = emptyList())

        val outcome = blocksOf(retriever.retrieve(RetrievalRequest("¿Y de la que?")))

        assertThat(outcome.candidates.map { it.chunkId }).containsExactlyElementsIn(ids.take(RagParameters.FUSED_LIMIT)).inOrder()
        assertThat(outcome.candidates.all { it.textRank == null }).isTrue()
        assertThat(outcome.blocks.flatMap { it.chunkIds }).contains(ids.first())
    }

    @Test
    fun withoutSearchableChunks_thereAreNoResults() = runTest {
        val excluded = documents("Excluido", included = false)
        val pending = documents("Pendiente", status = DocumentStatus.EMBEDDING)
        program(excluded + pending, pending)
        assertThat(retriever.retrieve(RetrievalRequest(QUESTION)).outcome).isEqualTo(RetrievalOutcome.NoResults)

        program(emptyList(), emptyList())
        assertThat(retriever.retrieve(RetrievalRequest(QUESTION)).outcome).isEqualTo(RetrievalOutcome.NoResults)
    }

    // F10 T028 (FR-007, FR-008, rule Q8): follow-ups.

    @Test
    fun aFollowUp_searchesWithThePreviousQuestion() = runTest {
        val ids = documents("Bombas")
        program(ids, ids)

        val result = retriever.retrieve(RetrievalRequest("¿Y el aceite?", previousQuestion = PREVIOUS))

        assertThat(result.followUp).isTrue()
        assertThat(result.searchText).isEqualTo("$PREVIOUS ¿Y el aceite?")
        assertThat(index.calls).contains("text:${RagParameters.CANDIDATES}:$PREVIOUS ¿Y el aceite?")
        assertThat(embedder.engine.embedded).containsExactly(EmbeddingPrefixes.query("$PREVIOUS ¿Y el aceite?"))
    }

    @Test
    fun aLongQuestionWithoutConnector_orAFollowUpWithoutPrevious_isSearchedAlone() = runTest {
        val ids = documents("Bombas")
        program(ids, ids)

        val long = retriever.retrieve(RetrievalRequest(LONG_QUESTION, previousQuestion = PREVIOUS))
        val alone = retriever.retrieve(RetrievalRequest("¿Y el aceite?", previousQuestion = null))
        val blank = retriever.retrieve(RetrievalRequest("¿Y el aceite?", previousQuestion = "   "))

        assertThat(long.followUp).isFalse()
        assertThat(long.searchText).isEqualTo(LONG_QUESTION)
        assertThat(alone.followUp).isFalse()
        assertThat(alone.searchText).isEqualTo("¿Y el aceite?")
        assertThat(blank.searchText).isEqualTo("¿Y el aceite?")
        assertThat(embedder.engine.embedded).containsExactly(
            EmbeddingPrefixes.query(LONG_QUESTION), EmbeddingPrefixes.query("¿Y el aceite?"), EmbeddingPrefixes.query("¿Y el aceite?"),
        ).inOrder()
    }

    @Test
    fun aQueryTooLongForTheModel_losesThePreviousQuestionFirst_butTheLexicalSearchGetsItWhole() = runTest {
        val ids = documents("Bombas")
        program(ids, ids)
        val previous = "Antes pregunté por las bombas hidráulicas de la central y por su mantenimiento anual completo"
        embedder.engine.maxInputChars = EmbeddingPrefixes.query("Antes pregunté por las bombas ¿Y el aceite?").length

        val result = retriever.retrieve(RetrievalRequest("¿Y el aceite?", previousQuestion = previous))

        assertThat(result.embeddingCuts).isGreaterThan(0)
        assertThat(result.searchText).isEqualTo("$previous ¿Y el aceite?")
        assertThat(index.calls).contains("text:${RagParameters.CANDIDATES}:$previous ¿Y el aceite?")
        val embedded = embedder.engine.embedded.single()
        assertThat(embedded).endsWith(" ¿Y el aceite?")
        assertThat(embedded).startsWith(EmbeddingPrefixes.query("Antes pregunté"))
        assertThat(result.outcome).isInstanceOf(RetrievalOutcome.Blocks::class.java)
    }

    private companion object {
        const val PREVIOUS = "¿Cada cuánto se revisa la bomba?"

        /** Nine normalized terms and no connector: never a follow-up. */
        const val LONG_QUESTION =
            "¿Cuál es el plazo para presentar la declaración trimestral del impuesto sobre el valor añadido de las empresas exportadoras?"
        const val CHUNKS = 20
        const val QUESTION = "¿Cada cuánto se revisan las bombas hidráulicas del sistema principal?"
    }
}

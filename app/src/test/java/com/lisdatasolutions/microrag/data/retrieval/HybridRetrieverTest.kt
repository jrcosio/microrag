package com.lisdatasolutions.microrag.data.retrieval

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.FakeTextEmbedder
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.rag.retrieval.ContextSelector
import com.lisdatasolutions.microrag.rag.retrieval.ReciprocalRankFusion
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.rag.runtime.QueryEmbedding
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    // F10 T030 (rules Q1, Q9–Q11): order of the checks, failures, races and the log.

    @Test
    fun anEmptyQuestion_touchesNeitherTheIndexNorTheEngine() = runTest {
        listOf("", "   \n ").forEach { question ->
            val result = retriever.retrieve(RetrievalRequest(question, previousQuestion = PREVIOUS))
            assertThat(result.outcome).isEqualTo(RetrievalOutcome.EmptyQuestion)
            assertThat(result.searchText).isEqualTo(question)
            assertThat(result.followUp).isFalse()
            assertThat(result.timings.embeddingMs).isEqualTo(0.0)
            // Seen on the phone (T051): the engine was not loaded, and the diagnostic said it was.
            assertThat(result.timings.coldEngine).isTrue()
        }
        assertThat(index.calls).isEmpty()
        assertThat(embedder.readyCalls.get()).isEqualTo(0)
        assertThat(embedder.embedCalls.get()).isEqualTo(0)
    }

    @Test
    fun anIndexThatCannotBeUsed_givesItsState_withoutAskingForTheModel() = runTest {
        val incompatible = IndexCompatibility.Incompatible("embedding_dims", "256", "768")
        listOf(incompatible, IndexCompatibility.Unreadable("not a database")).forEach { state ->
            index.setCompatibility(state)
            assertThat(retriever.retrieve(RetrievalRequest(QUESTION)).outcome).isEqualTo(RetrievalOutcome.IndexUnavailable(state))
        }
        assertThat(index.calls).containsExactly("open", "open")
        assertThat(embedder.readyCalls.get()).isEqualTo(0)
        assertThat(embedder.embedCalls.get()).isEqualTo(0)
    }

    @Test
    fun modelsNotReady_searchNothing_andLoadNothing() = runTest {
        embedder.isReady = false

        val result = retriever.retrieve(RetrievalRequest(QUESTION))

        assertThat(result.outcome).isEqualTo(RetrievalOutcome.ModelsNotReady)
        assertThat(result.timings.coldEngine).isTrue()
        assertThat(index.calls).containsExactly("open")
        assertThat(embedder.embedCalls.get()).isEqualTo(0)
        assertThat(embedder.loaded).isFalse()
    }

    @Test
    fun indexFailuresDuringTheSearch_areIndexUnavailable() = runTest {
        documents("Bombas")
        val closed = IndexCompatibility.Unreadable("closed meanwhile")
        index.searchError = IndexUnavailableException(closed)
        assertThat(retriever.retrieve(RetrievalRequest(QUESTION)).outcome).isEqualTo(RetrievalOutcome.IndexUnavailable(closed))

        index.searchError = IllegalStateException("disk I/O error")
        assertThat(retriever.retrieve(RetrievalRequest(QUESTION)).outcome)
            .isEqualTo(RetrievalOutcome.IndexUnavailable(IndexCompatibility.Unreadable("search: IllegalStateException")))
    }

    @Test
    fun engineFailures_areEmbeddingFailed() = runTest {
        documents("Bombas")
        embedder.engine.failEngineTimes = 1
        assertThat(retriever.retrieve(RetrievalRequest(QUESTION)).outcome).isEqualTo(RetrievalOutcome.EmbeddingFailed)

        val cannotLoad = object : QueryEmbedder {
            override suspend fun ready() = true
            override suspend fun embed(question: String, previous: String?): QueryEmbedding = throw IllegalStateException("cannot load")
            override val loaded = false
        }
        val result = HybridRetriever(index, cannotLoad, logger).retrieve(RetrievalRequest(QUESTION))
        assertThat(result.outcome).isEqualTo(RetrievalOutcome.EmbeddingFailed)
        assertThat(result.timings.coldEngine).isTrue()
    }

    @Test
    fun cancellationIsNotAnOutcome() = runTest {
        val cancelled = object : QueryEmbedder {
            override suspend fun ready() = true
            override suspend fun embed(question: String, previous: String?): QueryEmbedding = throw CancellationException("screen left")
            override val loaded = true
        }

        val thrown = runCatching { HybridRetriever(index, cancelled, logger).retrieve(RetrievalRequest(QUESTION)) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun chunksThatStopBeingSearchableAfterTheSearch_neverReachTheBlocks() = runTest {
        val bombas = documents("Bombas")
        val aceite = documents("Aceite")
        val aceiteId = index.documentIdByHash("Aceite")!!
        index.vectorResults = { (aceite + bombas).map { ScoredChunk(it, 0.2) } }
        // The user excludes «Aceite» while the searches run.
        index.textResults = {
            index.docs[aceiteId] = index.docs.getValue(aceiteId).copy(included = false)
            aceite
        }

        val outcome = blocksOf(retriever.retrieve(RetrievalRequest(QUESTION)))

        assertThat(outcome.blocks.map { it.documentId }.toSet()).containsExactly(index.documentIdByHash("Bombas"))
        assertThat(outcome.candidates.filter { it.selected }.map { it.chunkId }).containsNoneIn(aceite)
    }

    @Test
    fun twoRetrievalsAtOnce_getEachTheirOwnContext() = runBlocking<Unit> {
        val bombas = documents("Bombas")
        val aceite = documents("Aceite")
        val first = "Pregunta larga sobre el mantenimiento preventivo de las bombas hidráulicas principales"
        val second = "Pregunta larga sobre los cambios periódicos del aceite lubricante del motor diésel"
        index.vectorResults = { query ->
            val ids = if (query.contentEquals(FakeTextEmbedder.vectorOf(EmbeddingPrefixes.query(first)))) bombas else aceite
            ids.map { ScoredChunk(it, 0.2) }
        }
        index.textResults = { query -> if (query == first) bombas else aceite }
        val bothEmbedding = CountDownLatch(2)
        embedder.engine.beforeEmbedding = {
            bothEmbedding.countDown()
            bothEmbedding.await(5, TimeUnit.SECONDS)
        }

        val results = listOf(first, second).map { question ->
            async(Dispatchers.Default) { retriever.retrieve(RetrievalRequest(question)) }
        }.awaitAll()

        assertThat(bothEmbedding.count).isEqualTo(0)
        assertThat(results.map { it.searchText }).containsExactly(first, second).inOrder()
        assertThat(blocksOf(results[0]).blocks.flatMap { it.chunkIds }).containsNoneIn(aceite)
        assertThat(blocksOf(results[1]).blocks.flatMap { it.chunkIds }).containsNoneIn(bombas)
    }

    @Test
    fun oneLogLinePerRetrieval_withNumbersOnly() = runTest {
        val ids = documents("Manual de bombas")
        program(ids, ids)

        val blocks = retriever.retrieve(RetrievalRequest("¿Y el aceite?", previousQuestion = PREVIOUS))
        retriever.retrieve(RetrievalRequest(""))
        embedder.isReady = false
        retriever.retrieve(RetrievalRequest(QUESTION))

        val lines = logger.lines.filter { it.startsWith("retrieval ") }
        assertThat(lines).hasSize(3)
        assertThat(lines[0]).matches(
            "retrieval outcome=Blocks follow_up=true cuts=0 vector=\\d+ text=\\d+ fused=\\d+ blocks=\\d+ tokens=\\d+ cold=true " +
                "embed_ms=\\d+\\.\\d vector_ms=\\d+\\.\\d text_ms=\\d+\\.\\d select_ms=\\d+\\.\\d total_ms=\\d+\\.\\d",
        )
        assertThat(lines[1]).startsWith("retrieval outcome=EmptyQuestion")
        assertThat(lines[2]).startsWith("retrieval outcome=ModelsNotReady")
        val texts = listOf("aceite", "bomba", PREVIOUS, QUESTION, "Manual", "fragmento") +
            (blocksOf(blocks).blocks.map { it.text })
        logger.lines.forEach { line -> texts.forEach { text -> assertThat(line.lowercase()).doesNotContain(text.lowercase()) } }
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

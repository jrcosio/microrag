package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** F04 US2: exact vector search, weighted BM25, chunk loading and neighbors (T023). */
@RunWith(AndroidJUnit4::class)
class RagIndexSearchTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    /** Published at once: searches only see done documents (F07 rule N7). */
    private suspend fun addDoc(name: String) =
        index.addDocument(NewDocument(displayName = name, contentHash = name)).also { index.updateStatus(it, DocumentStatus.DONE) }

    private fun idsByOrdinal(documentId: Long): Map<Int, Long> = temp.withRaw { db ->
        db.query("SELECT ordinal, id FROM chunks WHERE document_id = ?", documentId) { it.getLong(0).toInt() to it.getLong(1) }.toMap()
    }

    @Test
    fun vectorSearch_returnsTheIdenticalVectorFirst() = runBlocking<Unit> {
        val doc = addDoc("Vectores")
        index.insertChunks(doc, (0 until 20).map { chunk(it, vector = unitVector(100 + it)) })
        val ids = idsByOrdinal(doc)

        // SC-003: the stored vector used as query comes first in 100 % of the cases.
        (0 until 20).forEach { ordinal ->
            val hits = index.vectorSearch(unitVector(100 + ordinal), k = 5)

            assertThat(hits).hasSize(5)
            assertThat(hits.first().id).isEqualTo(ids.getValue(ordinal))
            assertThat(hits.first().distance).isWithin(1e-4).of(0.0)
            assertThat(hits.map { it.distance }).isInOrder()
        }
        assertThat(index.vectorSearch(unitVector(100))).hasSize(20)
    }

    @Test
    fun vectorSearch_rejectsVectorsOutsideTheSpace() = runBlocking<Unit> {
        addDoc("Vectores")
        val short = FloatArray(255).also { it[0] = 1f }
        val notUnit = unitVector(1).map { it * 2f }.toFloatArray()

        assertThat(runCatching { index.vectorSearch(short) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { index.vectorSearch(notUnit) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun textSearch_weighsTitleAboveBody() = runBlocking<Unit> {
        val inTitle = addDoc("Guía")
        val inBody = addDoc("Notas")
        index.insertChunks(inTitle, listOf(chunk(0, content = "motores bombas válvulas filtros", headingPath = "Turbinas")))
        index.insertChunks(inBody, listOf(chunk(0, content = "turbinas bombas válvulas filtros")))

        val hits = index.textSearch("turbinas")

        assertThat(hits).containsExactly(idsByOrdinal(inTitle).getValue(0), idsByOrdinal(inBody).getValue(0)).inOrder()
    }

    @Test
    fun textSearch_handlesStopwordsQuotesAndCodes() = runBlocking<Unit> {
        val doc = addDoc("Repuestos")
        index.insertChunks(doc, listOf(chunk(0, content = "Pieza R-1234 del equipo"), chunk(1, content = "Otro texto")))

        assertThat(index.textSearch("de la que")).isEmpty()
        assertThat(index.textSearch("R-1234 \"manual\"")).containsExactly(idsByOrdinal(doc).getValue(0))
        assertThat(index.textSearch("x", k = 1)).isEmpty()
    }

    @Test
    fun loadChunks_andNeighbor_returnTheStoredFields() = runBlocking<Unit> {
        val doc = addDoc("Manual de motores")
        index.insertChunks(
            doc,
            listOf(
                chunk(0, content = "Contenido original", headingPath = "Cap. 1 › Sec. 2", page = 7),
                chunk(1, content = "Siguiente bloque"),
            ),
        )
        val ids = idsByOrdinal(doc)

        val loaded = index.loadChunks(listOf(ids.getValue(0), 9_999L))

        assertThat(loaded.keys).containsExactly(ids.getValue(0))
        assertThat(loaded.getValue(ids.getValue(0))).isEqualTo(
            ChunkView(
                id = ids.getValue(0),
                documentId = doc,
                ordinal = 0,
                headingPath = "Cap. 1 › Sec. 2",
                page = 7,
                content = "Contenido original",
                tokenEstimate = "Contenido original".length / 4 + 1,
                documentName = "Manual de motores",
            ),
        )
        assertThat(index.neighbor(doc, 1)?.id).isEqualTo(ids.getValue(1))
        assertThat(index.neighbor(doc, 1)?.content).isEqualTo("Siguiente bloque")
        assertThat(index.neighbor(doc, 2)).isNull()
    }

    @Test
    fun emptyIndex_returnsEmptyResults() = runBlocking<Unit> {
        assertThat(index.vectorSearch(unitVector(1))).isEmpty()
        assertThat(index.textSearch("bombas")).isEmpty()
        assertThat(index.loadChunks(listOf(1L))).isEmpty()
        assertThat(index.neighbor(1L, 0)).isNull()
    }

    @Test
    fun reopen_vectorSearchStillWorks() = runBlocking<Unit> {
        val doc = addDoc("Vectores")
        index.insertChunks(doc, (0 until 5).map { chunk(it) })
        index.close()

        // vector_init runs again on the new connection (US1, scenario 6).
        val hits = index.vectorSearch(unitVector(3), k = 1)

        assertThat(hits.single().id).isEqualTo(idsByOrdinal(doc).getValue(3))
    }

    @Test
    fun logs_neverContainTextsQueriesOrNames() = runBlocking<Unit> {
        val doc = addDoc("Documento Secreto Zeta")
        index.insertChunks(doc, listOf(chunk(0, content = "contenido confidencial omega", headingPath = "Anexo reservado")))
        index.textSearch("consulta privada sigma")
        index.vectorSearch(unitVector(0))
        index.loadChunks(idsByOrdinal(doc).values)
        index.deleteDocument(doc)

        val log = temp.logger.lines.joinToString("\n").lowercase()
        assertThat(temp.logger.lines).isNotEmpty()
        listOf("secreto", "zeta", "confidencial", "omega", "reservado", "privada", "sigma").forEach {
            assertThat(log).doesNotContain(it)
        }
    }
}

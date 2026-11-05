package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.ChatLibraryState
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.domain.rag.PageSpace
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F19 T007 (contract §1, E1–E3 and P1–P9): the pages of the multimodal mode and the text embedder the index
 * records, on the real schema and extension.
 */
@RunWith(AndroidJUnit4::class)
class RagIndexPagesTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private fun page(documentId: Long, number: Int, seed: Int = number) = PageInput(
        page = number,
        fileName = "$documentId-$number.jpg",
        widthPx = 543,
        heightPx = 768,
        sizeBytes = 90_000L,
        embedding = unitVector(seed),
    )

    private suspend fun doc(name: String, mode: IndexMode, status: DocumentStatus = DocumentStatus.DONE, chunks: Int = 0): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, pageCount = 3, indexMode = mode))
        if (chunks > 0) index.insertChunks(id, List(chunks) { chunk(it, vector = unitVector(100 + id.toInt() * 10 + it)) })
        index.updateStatus(id, status, chunkCount = if (status == DocumentStatus.DONE) chunks else null)
        return id
    }

    private fun pageKeys(): Map<String, String> = temp.withRaw { db ->
        db.query("SELECT key, value FROM rag_meta WHERE key LIKE 'page_%'") { it.getText(0) to it.getText(1) }.toMap()
    }

    private fun metaSha(): String? = temp.withRaw { db ->
        db.queryOne("SELECT value FROM rag_meta WHERE key = ?", RagMetaKeys.EMBEDDING_MODEL_SHA256) { it.getText(0) }
    }

    @Test
    fun insertPage_isResumableAndWritesThePageKeysWithTheFirstPage() = runBlocking<Unit> {
        index.open()
        val id = doc("Plano.pdf", IndexMode.VISUAL, DocumentStatus.EMBEDDING)
        assertThat(index.storedPages(id)).isEmpty()
        assertThat(pageKeys()).isEmpty()
        assertThat(index.pagesUsable.value).isTrue()

        assertThat(index.insertPage(id, page(id, 1))).isTrue()
        assertThat(pageKeys()).isEqualTo(PageSpace.CURRENT.toMeta())
        assertThat(index.insertPage(id, page(id, 1, seed = 9))).isFalse()
        assertThat(index.insertPage(id, page(id, 2))).isTrue()

        assertThat(index.storedPages(id)).containsExactly(1, 2)
        assertThat(index.document(id)!!.pagesStored).isEqualTo(2)
        assertThat(index.pageFiles(id)).containsExactly("$id-1.jpg", "$id-2.jpg")
        assertThat(index.allPageFiles()).containsExactly("$id-1.jpg", "$id-2.jpg")

        // A run killed after page 2 resumes from what the file holds.
        index.close()
        assertThat(index.open()).isEqualTo(IndexCompatibility.Compatible)
        assertThat(index.storedPages(id)).containsExactly(1, 2)
        assertThat(index.pagesUsable.value).isTrue()
        assertThat(index.insertPage(id, page(id, 3))).isTrue()
        assertThat(index.storedPages(id)).containsExactly(1, 2, 3)
    }

    @Test
    fun insertPage_rejectsAnUnknownDocumentOrAVectorThatIsNotUnit() = runBlocking<Unit> {
        index.open()
        val id = doc("Plano.pdf", IndexMode.VISUAL, DocumentStatus.EMBEDDING)

        assertThrows(IllegalArgumentException::class.java) { runBlocking { index.insertPage(9_999, page(9_999, 1)) } }
        val raw = FloatArray(EmbeddingMath.DIMENSIONS) { 1f }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { index.insertPage(id, PageInput(1, "x.jpg", 1, 1, 1, raw)) }
        }
        assertThat(index.storedPages(id)).isEmpty()
        assertThat(pageKeys()).isEmpty()
    }

    @Test
    fun otherPageKeys_doNotBlockTheOpeningButMakeThePagesUnusable() = runBlocking<Unit> {
        index.open()
        val id = doc("Plano.pdf", IndexMode.VISUAL, DocumentStatus.EMBEDDING)
        index.insertPage(id, page(id, 1))
        index.close()
        temp.withRaw { db -> db.exec("UPDATE rag_meta SET value = '280' WHERE key = ?", RagMetaKeys.PAGE_TOKENS_PER_IMAGE) }

        assertThat(index.open()).isEqualTo(IndexCompatibility.Compatible)
        assertThat(index.pagesUsable.value).isFalse()
        assertThrows(IllegalStateException::class.java) { runBlocking { index.insertPage(id, page(id, 2)) } }
        assertThat(index.storedPages(id)).containsExactly(1)

        // Once those pages are gone, the next first page writes this app's keys again.
        index.deleteDocument(id)
        val next = doc("Otro.pdf", IndexMode.VISUAL, DocumentStatus.EMBEDDING)
        assertThat(index.insertPage(next, page(next, 1))).isTrue()
        assertThat(pageKeys()).isEqualTo(PageSpace.CURRENT.toMeta())
        assertThat(index.pagesUsable.value).isTrue()
    }

    @Test
    fun textEmbedder_followsRagMeta_andTheSwitchKeepsEveryChunk() = runBlocking<Unit> {
        assertThat(index.textEmbedder.value).isEqualTo(ModelId.EG2_270M)
        index.open()
        val id = doc("Manual.txt", IndexMode.TEXT, chunks = 2)
        assertThat(metaSha()).isEqualTo(ModelCatalog.byId(ModelId.EG2_270M).sha256)
        val before = index.counts()

        assertThat(index.switchTextEmbedder(ModelId.EG2_440M)).isTrue()
        assertThat(index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
        assertThat(metaSha()).isEqualTo(ModelCatalog.byId(ModelId.EG2_440M).sha256)
        assertThat(index.switchTextEmbedder(ModelId.EG2_440M)).isFalse()

        assertThat(index.counts()).isEqualTo(before)
        val hit = index.vectorSearch(unitVector(100 + id.toInt() * 10), k = 1).single()
        assertThat(hit.distance).isWithin(1e-4).of(0.0)
        assertThat(index.textSearch("fragmento")).hasSize(2)

        // The file says it after reopening, and only the two text embedders are accepted.
        index.close()
        assertThat(index.open()).isEqualTo(IndexCompatibility.Compatible)
        assertThat(index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { index.switchTextEmbedder(ModelId.GEMMA4_E2B) } }
        assertThat(index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
    }

    @Test
    fun beforeOpening_theMirrorSays_andTheFileCorrectsIt() = runBlocking<Unit> {
        // The index was built with the 270M, but the mirror already says 440M: the opening corrects it (analyze H3).
        index.open()
        index.close()
        TempRagIndex(dbFile = temp.dbFile, initialTextEmbedder = ModelId.EG2_440M).use { other ->
            assertThat(other.index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
            assertThat(other.index.open()).isEqualTo(IndexCompatibility.Compatible)
            assertThat(other.index.textEmbedder.value).isEqualTo(ModelId.EG2_270M)
        }

        // A new file, once the 270M is gone, records the 440M.
        TempRagIndex(initialTextEmbedder = ModelId.EG2_440M).use { fresh ->
            assertThat(fresh.index.open()).isEqualTo(IndexCompatibility.Compatible)
            assertThat(fresh.index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
            assertThat(fresh.index.meta()[RagMetaKeys.EMBEDDING_MODEL_SHA256]).isEqualTo(ModelCatalog.byId(ModelId.EG2_440M).sha256)
        }
    }

    @Test
    fun removeFromIndex_andDeleteDocument_takeThePagesWithThem() = runBlocking<Unit> {
        index.open()
        val removed = doc("Ambos.pdf", IndexMode.BOTH, chunks = 1)
        index.insertPage(removed, page(removed, 1))
        index.insertPage(removed, page(removed, 2))
        val deleted = doc("Plano.pdf", IndexMode.VISUAL)
        index.insertPage(deleted, page(deleted, 1, seed = 5))
        val shadow = index.addReindexShadow(deleted)
        index.insertPage(shadow, page(shadow, 1, seed = 6))

        assertThat(index.pageFiles(removed)).containsExactly("$removed-1.jpg", "$removed-2.jpg")
        assertThat(index.removeFromIndex(removed)).isEqualTo(1)
        assertThat(index.storedPages(removed)).isEmpty()
        assertThat(index.document(removed)!!.pagesStored).isEqualTo(0)
        assertThat(index.document(removed)!!.indexMode).isEqualTo(IndexMode.BOTH)

        // The files of the document and of its shadow, read before the rows go.
        assertThat(index.pageFiles(deleted)).containsExactly("$deleted-1.jpg", "$shadow-1.jpg")
        index.deleteDocument(deleted)
        assertThat(index.pageFiles(deleted)).isEmpty()
        assertThat(index.allPageFiles()).isEmpty()
        assertThat(temp.withRaw { db -> db.queryOne("SELECT count(*) FROM pages") { it.getLong(0) } }).isEqualTo(0L)
    }

    @Test
    fun publishReindex_putsTheShadowsPagesAndModeInPlace() = runBlocking<Unit> {
        index.open()
        val original = doc("Catálogo.pdf", IndexMode.VISUAL)
        index.insertPage(original, page(original, 1))
        index.insertPage(original, page(original, 2))
        val shadow = index.addReindexShadow(original)
        assertThat(index.shadowOf(original)!!.indexMode).isEqualTo(IndexMode.VISUAL)
        index.setIndexMode(shadow, IndexMode.BOTH)
        index.insertChunks(shadow, listOf(chunk(0, vector = unitVector(40))))
        index.insertPage(shadow, page(shadow, 1, seed = 41))
        index.insertPage(shadow, page(shadow, 2, seed = 42))
        index.insertPage(shadow, page(shadow, 3, seed = 43))
        index.updateStatus(shadow, DocumentStatus.DONE, chunkCount = 1)
        val before = index.pageFiles(original).toSet()

        assertThat(index.publishReindex(shadow)).isEqualTo(1)

        val published = index.document(original)!!
        assertThat(published.indexMode).isEqualTo(IndexMode.BOTH)
        assertThat(published.pagesStored).isEqualTo(3)
        assertThat(index.storedPages(original)).containsExactly(1, 2, 3)
        assertThat(index.document(shadow)).isNull()
        val after = index.pageFiles(original).toSet()
        assertThat(after).containsExactly("$shadow-1.jpg", "$shadow-2.jpg", "$shadow-3.jpg")
        // What the caller deletes: the original's old images.
        assertThat(before - after).containsExactly("$original-1.jpg", "$original-2.jpg")
        assertThat(index.allPageFiles()).isEqualTo(after)
    }

    @Test
    fun setIndexMode_changesAnErroredDocumentBeforeItsRetry() = runBlocking<Unit> {
        index.open()
        val id = doc("Escaneado.pdf", IndexMode.TEXT, DocumentStatus.ERROR)
        index.setIndexMode(id, IndexMode.VISUAL)
        assertThat(index.document(id)!!.indexMode).isEqualTo(IndexMode.VISUAL)
    }

    @Test
    fun chatLibraryState_countsTheDocumentsWithOnlyPagesApart() = runBlocking<Unit> {
        index.open()
        doc("Texto.txt", IndexMode.TEXT, chunks = 1)
        doc("Ambos.pdf", IndexMode.BOTH, chunks = 1)
        val visual = doc("Plano.pdf", IndexMode.VISUAL)
        index.insertPage(visual, page(visual, 1))
        doc("Escaneado en Ambos.pdf", IndexMode.BOTH)
        doc("Pendiente.pdf", IndexMode.VISUAL, DocumentStatus.PENDING)

        assertThat(index.chatLibraryState())
            // F20 (research R5): withPages counts the searchable documents with any page, Plano.pdf here.
            .isEqualTo(ChatLibraryState(documents = 5, searchable = 2, pendingIncluded = 1, visualOnly = 2, withPages = 1))
        // Pages are never in the text searches.
        assertThat(index.vectorSearch(unitVector(1), k = 10).map { it.id }).hasSize(2)
    }

    @Test
    fun everyConnection_initialisesThePagesVectors() = runBlocking<Unit> {
        index.open()
        val id = doc("Plano.pdf", IndexMode.VISUAL)
        index.insertPage(id, page(id, 1, seed = 77))
        index.close()

        temp.withRaw { db ->
            db.initVector(EmbeddingMath.DIMENSIONS)
            val nearest = db.queryOne(
                "SELECT p.page FROM vector_full_scan('pages', 'embedding', vector_as_f32(?), 1) v JOIN pages p ON p.id = v.rowid",
                unitVector(77).toLeBlob(),
            ) { it.getLong(0) }
            assertThat(nearest).isEqualTo(1L)
        }
        assertThat(index.open()).isEqualTo(IndexCompatibility.Compatible)
    }
}

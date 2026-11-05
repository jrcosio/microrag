package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.domain.rag.PageKey
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** F20 T004 (contract P1–P4, research R2): the page search on the real schema and extension. */
@RunWith(AndroidJUnit4::class)
class RagIndexPageSearchTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private fun page(documentId: Long, number: Int, seed: Int) = PageInput(
        page = number, fileName = "$documentId-$number.jpg", widthPx = 543, heightPx = 768, sizeBytes = 90_000L, embedding = unitVector(seed),
    )

    private suspend fun doc(
        name: String,
        mode: IndexMode = IndexMode.VISUAL,
        status: DocumentStatus = DocumentStatus.DONE,
        folderId: Long? = null,
        mime: String = "application/pdf",
        included: Boolean = true,
    ): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = mime, pageCount = 3, folderId = folderId, indexMode = mode))
        index.updateStatus(id, status, chunkCount = if (status == DocumentStatus.DONE) 0 else null)
        if (!included) index.setIncluded(id, false)
        return id
    }

    @Test
    fun thePagesComeByDistance_upToK_withWhatTheChatShows() = runBlocking<Unit> {
        index.open()
        val plan = doc("Plano.pdf")
        val poster = doc("Cartel.jpg", mime = "image/jpeg")
        index.insertPage(plan, page(plan, 1, seed = 1))
        index.insertPage(plan, page(plan, 2, seed = 2))
        index.insertPage(poster, page(poster, 1, seed = 3))

        val found = index.pageSearch(unitVector(2), k = 2)

        assertThat(found).hasSize(2)
        assertThat(found.first().let { Triple(it.documentId, it.page, it.documentName) }).isEqualTo(Triple(plan, 2, "Plano.pdf"))
        assertThat(found.first().distance).isWithin(1e-4).of(0.0)
        assertThat(found.first().fileName).isEqualTo("$plan-2.jpg")
        assertThat(found.zipWithNext().all { (a, b) -> a.distance <= b.distance }).isTrue()
        val image = index.pageSearch(unitVector(3), k = 1).single()
        assertThat(image.image).isTrue()
        assertThat(found.first().image).isFalse()
    }

    @Test
    fun onlySearchableDocuments_andOnlyInTheScope() = runBlocking<Unit> {
        index.open()
        val legal = checkNotNull(index.createFolder("Legal", "legal"))
        val inLegal = doc("Contrato.pdf", folderId = legal)
        val general = doc("Manual.pdf")
        val pending = doc("Borrador.pdf", status = DocumentStatus.EMBEDDING)
        val excluded = doc("Excluido.pdf", included = false)
        listOf(inLegal, general, pending, excluded).forEachIndexed { i, id -> index.insertPage(id, page(id, 1, seed = 10 + i)) }

        assertThat(index.pageSearch(unitVector(10), k = 5).map { it.documentId }).containsExactly(inLegal, general)
        assertThat(index.pageSearch(unitVector(10), k = 5, scope = SearchScope.Only(setOf(legal), general = false, documentIds = emptySet())).map { it.documentId })
            .containsExactly(inLegal)
        assertThat(index.pageSearch(unitVector(10), k = 5, scope = SearchScope.Only(emptySet(), general = true, documentIds = emptySet())).map { it.documentId })
            .containsExactly(general)
        assertThat(index.pageSearch(unitVector(10), k = 5, scope = SearchScope.Only(emptySet(), general = false, documentIds = setOf(general))).map { it.documentId })
            .containsExactly(general)
    }

    @Test
    fun withOtherPageKeys_noPageIsSearched() = runBlocking<Unit> {
        index.open()
        val id = doc("Plano.pdf")
        index.insertPage(id, page(id, 1, seed = 1))
        index.close()
        temp.withRaw { db -> db.exec("UPDATE rag_meta SET value = '280' WHERE key = ?", RagMetaKeys.PAGE_TOKENS_PER_IMAGE) }
        index.open()

        assertThat(index.pagesUsable.value).isFalse()
        assertThat(index.pageSearch(unitVector(1))).isEmpty()
    }

    /** Analyze G2: while a document is reindexed, its shadow's pages are not searchable; the published ones are. */
    @Test
    fun aReindexShadow_doesNotReplaceThePublishedPagesUntilItIsPublished() = runBlocking<Unit> {
        index.open()
        val original = doc("Catálogo.pdf")
        index.insertPage(original, page(original, 1, seed = 20))
        val shadow = index.addReindexShadow(original)
        index.setIndexMode(shadow, IndexMode.BOTH)
        index.insertPage(shadow, page(shadow, 1, seed = 21))

        assertThat(index.pageSearch(unitVector(21), k = 5).map { it.documentId }).containsExactly(original)

        index.insertChunks(shadow, listOf(chunk(0, vector = unitVector(40))))
        index.updateStatus(shadow, DocumentStatus.DONE, chunkCount = 1)
        index.publishReindex(shadow)

        val after = index.pageSearch(unitVector(21), k = 5).single()
        assertThat(after.documentId).isEqualTo(original)
        assertThat(after.distance).isWithin(1e-4).of(0.0)
    }

    @Test
    fun existingPages_andPageFile_followDeletionsAndReindexing() = runBlocking<Unit> {
        index.open()
        val kept = doc("Plano.pdf")
        val deleted = doc("Viejo.pdf")
        index.insertPage(kept, page(kept, 1, seed = 1))
        index.insertPage(kept, page(kept, 2, seed = 2))
        index.insertPage(deleted, page(deleted, 1, seed = 3))
        index.deleteDocument(deleted)

        assertThat(index.existingPages(listOf(PageKey(kept, 1), PageKey(kept, 3), PageKey(deleted, 1)))).containsExactly(PageKey(kept, 1))
        assertThat(index.pageFile(kept, 2)).isEqualTo("$kept-2.jpg")
        assertThat(index.pageFile(kept, 9)).isNull()

        // Reindexed in Text: the published document has no pages any more.
        val shadow = index.addReindexShadow(kept)
        index.setIndexMode(shadow, IndexMode.TEXT)
        index.insertChunks(shadow, listOf(chunk(0, vector = unitVector(50))))
        index.updateStatus(shadow, DocumentStatus.DONE, chunkCount = 1)
        index.publishReindex(shadow)

        assertThat(index.existingPages(listOf(PageKey(kept, 1), PageKey(kept, 2)))).isEmpty()
    }

    @Test
    fun theChatStateCountsTheDocumentsWithPages() = runBlocking<Unit> {
        index.open()
        val visual = doc("Plano.pdf")
        val both = index.addDocument(NewDocument(displayName = "Informe.pdf", contentHash = "Informe.pdf", indexMode = IndexMode.BOTH))
        index.insertChunks(both, listOf(chunk(0, vector = unitVector(60))))
        index.updateStatus(both, DocumentStatus.DONE, chunkCount = 1)
        doc("Texto.pdf", mode = IndexMode.TEXT)
        index.insertPage(visual, page(visual, 1, seed = 1))
        index.insertPage(both, page(both, 1, seed = 2))

        val state = index.chatLibraryState()

        assertThat(state.visualOnly).isEqualTo(1)
        assertThat(state.withPages).isEqualTo(2)
    }
}

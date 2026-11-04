package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentNames
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** F17 T013 (contract F1–F3, F5; research R1, R4): folders in a real index. */
@RunWith(AndroidJUnit4::class)
class RagIndexFoldersTest {

    private val temp = TempRagIndex()
    private val index = temp.index
    private val every = DocumentStatus.entries.toSet()

    @After
    fun tearDown() = temp.close()

    private suspend fun create(name: String): Long = checkNotNull(index.createFolder(name, DocumentNames.fold(name)))

    private suspend fun doc(name: String, folderId: Long?, status: DocumentStatus = DocumentStatus.DONE, chunks: Int = 3): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        index.insertChunks(id, (0 until chunks).map { chunk(it, content = "La bomba $name $it") })
        index.updateStatus(id, status, chunkCount = chunks)
        temp.withRaw { db -> db.exec("UPDATE documents SET folder_id = ? WHERE id = ?", folderId, id) }
        return id
    }

    @Test
    fun aFoldedDuplicateIsRefused() = runBlocking<Unit> {
        val legal = create("Legal")

        assertThat(index.createFolder("LÉGAL", DocumentNames.fold("LÉGAL"))).isNull()
        assertThat(index.folders().folders.map { it.id to it.name }).containsExactly(legal to "Legal")
    }

    @Test
    fun renameKeepsTheIdAndAllowsOnlyChangingCase() = runBlocking<Unit> {
        val legal = create("Legal")
        val motors = create("Motores")

        assertThat(index.renameFolder(legal, "LEGAL", "legal")).isTrue()
        assertThat(index.renameFolder(legal, "Motores", "motores")).isNull()
        assertThat(index.renameFolder(999, "Otra", "otra")).isFalse()

        assertThat(index.folders().folders.map { it.id to it.name }).containsExactly(legal to "LEGAL", motors to "Motores").inOrder()
    }

    @Test
    fun theFoldersAreByFoldedNameWithAggregateCountsWithoutShadows() = runBlocking<Unit> {
        val motors = create("motores")
        val tree = create("Árbol")
        val legal = create("Legal")
        val indexed = doc("Contrato.txt", legal)
        doc("Anexo.txt", legal, DocumentStatus.PENDING)
        doc("Bomba.txt", motors)
        doc("Manual.txt", null)
        // A reindex shadow is never counted, in its folder or in «General» (F08 rule L2).
        index.addReindexShadow(indexed)

        val list = index.folders()

        assertThat(list.folders.map { it.name }).containsExactly("Árbol", "Legal", "motores").inOrder()
        assertThat(list.folders.associate { it.id to it.documentCount }).containsExactly(tree, 0, legal, 2, motors, 1)
        assertThat(list.unfiledCount).isEqualTo(1)
        assertThat(list.folders.all { it.createdAtMs > 0 }).isTrue()
    }

    @Test
    fun thePageAndCountFilterByFolderOrGeneral_andCarryTheFolderName() = runBlocking<Unit> {
        val legal = create("Legal")
        val contract = doc("Contrato.txt", legal)
        val annex = doc("Anexo.txt", legal, DocumentStatus.PENDING)
        val manual = doc("Manual.txt", null)

        val inLegal = index.libraryPage("", every, 50, LibraryFolderFilter.Folder(legal))
        assertThat(inLegal.map { it.document.id }).containsExactly(annex, contract).inOrder()
        assertThat(inLegal.map { it.document.folderName }.toSet()).containsExactly("Legal")
        assertThat(index.libraryCount("", every, LibraryFolderFilter.Folder(legal))).isEqualTo(2)
        assertThat(index.libraryPage("", setOf(DocumentStatus.DONE), 50, LibraryFolderFilter.Folder(legal)).map { it.document.id })
            .containsExactly(contract)

        val general = index.libraryPage("", every, 50, LibraryFolderFilter.Unfiled)
        assertThat(general.map { it.document.id }).containsExactly(manual)
        assertThat(general.single().document.folderName).isNull()
        assertThat(index.libraryCount("", every, LibraryFolderFilter.Unfiled)).isEqualTo(1)

        // The root's search looks in every folder.
        assertThat(index.libraryPage("contrato", every, 50).map { it.document.id }).containsExactly(contract)
        assertThat(index.libraryCount("", every)).isEqualTo(3)
        assertThat(index.document(contract)!!.folderId).isEqualTo(legal)
        assertThat(index.document(contract)!!.folderName).isEqualTo("Legal")
    }

    @Test
    fun deletingAndMovingLeavesEveryDocumentInGeneral() = runBlocking<Unit> {
        val legal = create("Legal")
        val contract = doc("Contrato.txt", legal)
        val annex = doc("Anexo.txt", legal)

        assertThat(index.deleteFolder(legal, moveDocuments = true)).isTrue()

        assertThat(index.folders().folders).isEmpty()
        assertThat(index.folders().unfiledCount).isEqualTo(2)
        assertThat(index.document(contract)!!.folderId).isNull()
        assertThat(index.document(annex)!!.status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.counts().chunks).isEqualTo(6)
    }

    /** M4: the use case deletes each document first; the folder only goes once it is empty. */
    @Test
    fun aFolderWithDocumentsLeftIsKept_andGoesOnceEmpty() = runBlocking<Unit> {
        val legal = create("Legal")
        val contract = doc("Contrato.txt", legal)
        val annex = doc("Anexo.txt", legal)
        val manual = doc("Manual.txt", null)

        assertThat(index.deleteFolder(legal, moveDocuments = false)).isFalse()
        index.deleteDocument(contract)
        assertThat(index.deleteFolder(legal, moveDocuments = false)).isFalse()
        index.deleteDocument(annex)
        assertThat(index.deleteFolder(legal, moveDocuments = false)).isTrue()

        assertThat(index.folders().folders).isEmpty()
        val counts = index.counts()
        assertThat(counts.documents).isEqualTo(1)
        assertThat(counts.chunks).isEqualTo(3)
        assertThat(counts.ftsRows).isEqualTo(3)
        assertThat(index.document(manual)).isNotNull()
        assertThat(index.deleteFolder(legal, moveDocuments = true)).isFalse()
    }

    @Test
    fun folderDocumentRefsGiveEveryDocumentOfAFolderOrOfGeneral() = runBlocking<Unit> {
        val legal = create("Legal")
        val contract = doc("Contrato.txt", legal)
        val annex = doc("Anexo.txt", legal, DocumentStatus.ERROR)
        val manual = doc("Manual.txt", null)
        index.addReindexShadow(contract)

        fun created(id: Long) = DocumentRef(id, checkNotNull(runBlocking { index.document(id) }).createdAtMs)

        assertThat(index.folderDocumentRefs(legal)).containsExactly(created(annex), created(contract)).inOrder()
        assertThat(index.folderDocumentRefs(null)).containsExactly(created(manual))
        assertThat(index.folderDocumentRefs(999)).isEmpty()
    }

    /** Research R1: AUTOINCREMENT, so a scope that kept a deleted folder never matches a new one. */
    @Test
    fun theIdOfADeletedFolderIsNeverReused() = runBlocking<Unit> {
        val first = create("Legal")
        index.deleteFolder(first, moveDocuments = true)

        val second = create("Legal")

        assertThat(second).isGreaterThan(first)
    }

    @Test
    fun folderWritesTellTheReaders() = runBlocking<Unit> {
        val before = index.documentChanges.value
        val legal = create("Legal")
        index.renameFolder(legal, "Contratos", "contratos")
        index.deleteFolder(legal, moveDocuments = true)

        assertThat(index.documentChanges.value).isEqualTo(before + 3)
        // FR-025: ids only.
        temp.logger.lines.filter { "folder" in it }.forEach {
            assertThat(it).doesNotContain("Legal")
            assertThat(it).doesNotContain("Contratos")
        }
    }
}

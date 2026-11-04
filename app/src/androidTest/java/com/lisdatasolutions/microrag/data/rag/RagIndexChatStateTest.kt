package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.ChatLibraryState
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.testing.TempRagIndex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** F17 T043 (contract F8, research R8): the chat's library counts within a scope, in one query. */
@RunWith(AndroidJUnit4::class)
class RagIndexChatStateTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private suspend fun doc(name: String, folderId: Long?, status: DocumentStatus, included: Boolean = true): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, folderId = folderId))
        index.updateStatus(id, status)
        if (!included) index.setIncluded(id, false)
        return id
    }

    @Test
    fun theCountsAreThoseOfTheScope_butTheLibraryIsWhole() = runBlocking<Unit> {
        index.open()
        val legal = checkNotNull(index.createFolder("Legal", "legal"))
        val motors = checkNotNull(index.createFolder("Motores", "motores"))
        doc("Contrato.txt", legal, DocumentStatus.DONE)
        doc("Anexo.txt", legal, DocumentStatus.PENDING)
        doc("Excluido.txt", legal, DocumentStatus.DONE, included = false)
        val bomb = doc("Bomba.txt", motors, DocumentStatus.EMBEDDING)
        doc("Manual.txt", null, DocumentStatus.DONE)

        assertThat(index.chatLibraryState()).isEqualTo(ChatLibraryState(documents = 5, searchable = 2, pendingIncluded = 2))
        assertThat(index.chatLibraryState(SearchScope.Only(setOf(legal), general = false, documentIds = emptySet())))
            .isEqualTo(ChatLibraryState(documents = 5, searchable = 1, pendingIncluded = 1))
        // K9: nothing ready in Motores, although the library has documents ready.
        assertThat(index.chatLibraryState(SearchScope.Only(setOf(motors), general = false, documentIds = emptySet())))
            .isEqualTo(ChatLibraryState(documents = 5, searchable = 0, pendingIncluded = 1))
        assertThat(index.chatLibraryState(SearchScope.Only(emptySet(), general = true, documentIds = setOf(bomb))))
            .isEqualTo(ChatLibraryState(documents = 5, searchable = 1, pendingIncluded = 1))
    }
}

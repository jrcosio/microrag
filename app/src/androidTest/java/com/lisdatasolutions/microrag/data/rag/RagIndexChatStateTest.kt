package com.lisdatasolutions.microrag.data.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.ChatLibraryState
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempRagIndex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F11 T012 (research R11): the library as the chat sees it, in one query. Shadows never count; the
 * searchable documents are done and included; a pending, parsed, embedding or paused included document
 * is still to come, while an error or one out of the index is not.
 */
@RunWith(AndroidJUnit4::class)
class RagIndexChatStateTest {

    private val temp = TempRagIndex()
    private val index = temp.index

    @After
    fun tearDown() = temp.close()

    private suspend fun doc(name: String, status: DocumentStatus, included: Boolean = true): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        index.updateStatus(id, status)
        if (!included) index.setIncluded(id, false)
        return id
    }

    @Test
    fun anEmptyIndexHasNothing() = runBlocking<Unit> {
        assertThat(index.chatLibraryState()).isEqualTo(ChatLibraryState(documents = 0, searchable = 0, pendingIncluded = 0))
    }

    @Test
    fun countsEachStateAsTheChatNeedsIt() = runBlocking<Unit> {
        doc("Pendiente.txt", DocumentStatus.PENDING)
        doc("Analizado.txt", DocumentStatus.PARSED)
        doc("Embebiendo.txt", DocumentStatus.EMBEDDING)
        doc("En pausa.txt", DocumentStatus.PAUSED)
        doc("Pendiente excluido.txt", DocumentStatus.PENDING, included = false)
        val done = doc("Hecho.txt", DocumentStatus.DONE)
        doc("Excluido.txt", DocumentStatus.DONE, included = false)
        doc("Con error.txt", DocumentStatus.ERROR)
        doc("Fuera.txt", DocumentStatus.OUT_OF_INDEX)
        index.addReindexShadow(done)

        assertThat(index.chatLibraryState()).isEqualTo(ChatLibraryState(documents = 9, searchable = 1, pendingIncluded = 4))
    }
}

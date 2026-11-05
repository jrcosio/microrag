package com.lisdatasolutions.microrag.data.library

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.ingest.PageFileStore
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F20 T014 (contract I1, I2): the stored JPEG of a page, or null when its row or its file is gone. */
class DefaultPageImageRepositoryTest {

    private val dir: File = Files.createTempDirectory("pages").toFile()
    private val index = FakeRagIndex()
    private val files = PageFileStore(dir, Dispatchers.Unconfined)
    private val logger = RecordingLogger()
    private val repository = DefaultPageImageRepository(index, files, Dispatchers.Unconfined, logger)

    private suspend fun document(): Long = index.addDocument(
        NewDocument(displayName = "informe-secreto.pdf", contentHash = "h", mimeType = "application/pdf", sizeBytes = 1, pageCount = 2, localPath = null),
    )

    @Test
    fun theStoredImageIsReturned() = runTest {
        val id = document()
        files.write("h-p1.jpg", byteArrayOf(-1, -40, 7)) { index.insertPage(id, PageInput(1, "h-p1.jpg", 10, 10, 3, FloatArray(256))) }

        assertThat(repository.image(id, 1)).isEqualTo(byteArrayOf(-1, -40, 7))
    }

    @Test
    fun aMissingRowOrFile_isNull_andTheLogHasNoName() = runTest {
        val id = document()
        files.write("h-p2.jpg", byteArrayOf(1)) { index.insertPage(id, PageInput(2, "h-p2.jpg", 10, 10, 1, FloatArray(256))) }
        File(dir, "h-p2.jpg").delete()

        assertThat(repository.image(id, 1)).isNull()
        assertThat(repository.image(id, 2)).isNull()
        assertThat(logger.lines).containsExactly("page image missing=row", "page image missing=file").inOrder()
        assertThat(logger.lines.joinToString()).doesNotContain("h-p")
    }
}

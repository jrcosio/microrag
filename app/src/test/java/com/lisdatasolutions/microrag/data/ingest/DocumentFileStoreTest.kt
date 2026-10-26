package com.lisdatasolutions.microrag.data.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Test

/** F06 T011: private copies of the imported documents (data-model §2). */
class DocumentFileStoreTest {

    private val dir = Files.createTempDirectory("documents").toFile()
    private val store = DocumentFileStore(dir)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun tempFilesAreHiddenAndUnique() {
        val first = store.newTempFile()
        val second = store.newTempFile()

        assertThat(first.parentFile).isEqualTo(dir)
        assertThat(first.name).matches("\\.import-[0-9a-f-]{36}\\.tmp")
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun promoteRenamesToTheHashAndReplacesAnOrphan() {
        val sha = "a".repeat(64)
        File(dir, "$sha.pdf").writeText("old orphan")
        val temp = store.newTempFile().apply { writeText("copy") }

        val final = store.promote(temp, sha, DocumentFormat.PDF)

        assertThat(final).isEqualTo(File(dir, "$sha.pdf"))
        assertThat(final.readText()).isEqualTo("copy")
        assertThat(temp.exists()).isFalse()
        assertThat(store.finalFile(sha, DocumentFormat.MARKDOWN).name).isEqualTo("$sha.md")
        assertThat(store.finalFile(sha, DocumentFormat.TXT).name).isEqualTo("$sha.txt")
    }

    @Test
    fun cleanOrphansKeepsOnlyReferencedFiles() {
        val kept = File(dir, "b".repeat(64) + ".txt").apply { writeText("kept") }
        val orphan = File(dir, "c".repeat(64) + ".md").apply { writeText("orphan") }
        val temp = store.newTempFile().apply { writeText("partial") }

        val removed = store.cleanOrphans(setOf(kept.absolutePath))

        assertThat(removed).isEqualTo(2)
        assertThat(kept.exists()).isTrue()
        assertThat(orphan.exists()).isFalse()
        assertThat(temp.exists()).isFalse()
    }

    @Test
    fun deleteIgnoresMissingAndForeignPaths() {
        val file = File(dir, "d".repeat(64) + ".pdf").apply { writeText("x") }
        val outside = Files.createTempFile("outside", ".pdf").toFile()

        store.delete(file.absolutePath)
        store.delete(File(dir, "missing.pdf").absolutePath)
        store.delete(null)
        store.delete(outside.absolutePath)

        assertThat(file.exists()).isFalse()
        assertThat(outside.exists()).isTrue()
        outside.delete()
    }
}

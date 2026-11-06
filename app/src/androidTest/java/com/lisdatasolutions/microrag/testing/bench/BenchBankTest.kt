package com.lisdatasolutions.microrag.testing.bench

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** F14 T009 (research R2, contract B1): a bank from the desktop, verified and loaded with the app's index. */
@RunWith(AndroidJUnit4::class)
class BenchBankTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "bench-bank-test/tiny")

    @After
    fun tearDown() {
        File(context.cacheDir, "bench-bank-test").deleteRecursively()
        File(context.cacheDir, BenchBank.CACHE).listFiles { f -> f.name.startsWith("tiny") }?.forEach { it.delete() }
    }

    private fun copyTinyBank() {
        dir.mkdirs()
        instrumentation.context.assets.list("bench/tiny")!!.forEach { name ->
            instrumentation.context.assets.open("bench/tiny/$name").use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
        }
    }

    @Test
    fun aVerifiedBank_loadsItsChunksAndFolders_andIsReusedWhileTheManifestStays() = runBlocking<Unit> {
        copyTinyBank()
        val bank = BenchBank(context, dir)

        assertThat(bank.verify()).isTrue()
        assertThat(bank.questions().map { it.id }).containsExactly("q1", "q2").inOrder()
        assertThat(bank.questions()[1].previous).isEqualTo("¿Qué plazo hay para presentar el recurso?")

        bank.open().use { opened ->
            assertThat(opened.reused).isFalse()
            val hits = opened.index.vectorSearch(bank.queryVectors().first(), k = 5, scope = SearchScope.All)
            assertThat(opened.chunkCount()).isEqualTo(bank.size.toLong())
            assertThat(hits).isNotEmpty()
            assertThat(opened.index.folders().folders.size).isEqualTo(2)
        }
        bank.open().use { again -> assertThat(again.reused).isTrue() }
    }

    @Test
    fun aChangedFile_failsTheVerification() {
        copyTinyBank()
        File(dir, "queries.f32").appendBytes(byteArrayOf(1))

        assertThat(BenchBank(context, dir).verify()).isFalse()
    }

    @Test
    fun aSubset_takesTheFirstWholeDocuments() = runBlocking<Unit> {
        copyTinyBank()

        BenchBank(context, dir).open(limit = BenchBank.CHUNKS_PER_DOCUMENT).use { opened ->
            assertThat(opened.dbFile.name).isEqualTo("tiny-${BenchBank.CHUNKS_PER_DOCUMENT}.db")
            assertThat(opened.chunkCount()).isEqualTo(BenchBank.CHUNKS_PER_DOCUMENT.toLong())
        }
    }
}

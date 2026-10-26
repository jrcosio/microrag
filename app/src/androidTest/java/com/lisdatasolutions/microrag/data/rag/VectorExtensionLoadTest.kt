package com.lisdatasolutions.microrag.data.rag

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Early check of F04 (plan, risk 1): the installed app has `vector.so` extracted next to its other
 * native libraries, and the bundled SQLite loads it and has FTS5.
 */
@RunWith(AndroidJUnit4::class)
class VectorExtensionLoadTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = File(context.cacheDir, "vector-load-test.db")

    @After
    fun tearDown() {
        listOf("", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
    }

    @Test
    fun vectorExtension_isExtractedAndLoads() {
        val extension = File(context.applicationInfo.nativeLibraryDir, "vector.so")
        assertThat(extension.isFile).isTrue()

        val driver = BundledSQLiteDriver().apply { addExtension(extension.path, "sqlite3_vector_init") }
        driver.open(dbFile.path, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE).use { connection ->
            val version = connection.prepare("SELECT vector_version()").use { statement ->
                statement.step()
                statement.getText(0)
            }
            val hasFts5 = connection.prepare("SELECT sqlite_compileoption_used('ENABLE_FTS5')").use { statement ->
                statement.step()
                statement.getLong(0)
            }

            assertThat(version).isEqualTo("1.1.2")
            assertThat(hasFts5).isEqualTo(1L)
        }
    }
}

package com.lisdatasolutions.microrag.testing

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.rag.DefaultRagIndex
import com.lisdatasolutions.microrag.data.rag.RagConnection
import com.lisdatasolutions.microrag.data.rag.RagMigration
import com.lisdatasolutions.microrag.data.rag.RagMigrations
import com.lisdatasolutions.microrag.data.rag.RagSchema
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

/**
 * A [DefaultRagIndex] on a temporary file in `cacheDir`, with the real schema and extension, for
 * F04 instrumented tests. The app index (`no_backup/index/rag.db`) is never touched.
 */
class TempRagIndex(
    migrations: List<RagMigration> = RagMigrations.all,
    val extensionPath: String = defaultExtensionPath(),
    val dbFile: File = File(context.cacheDir, "rag-test-${UUID.randomUUID()}.db"),
) : Closeable {
    /** Value returned by the index clock; tests move it to check timestamps. */
    @Volatile
    var now: Long = 1_000L

    val logger = CapturingLogger()
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "rag-test-db") }.asCoroutineDispatcher()

    val index = DefaultRagIndex(
        dbFile = dbFile,
        schema = { RagSchema.load(context.assets) },
        extensionPath = extensionPath,
        config = RagIndexConfig.current(),
        dispatcher = dispatcher,
        logger = logger,
        migrations = migrations,
        clock = { now },
    )

    /** A separate connection with the extension, to inspect or tamper with the file; the caller closes it. */
    fun raw(): RagConnection = RagConnection.open(dbFile, defaultExtensionPath())

    fun <T> withRaw(block: (RagConnection) -> T): T = raw().use(block)

    /** SHA-256 of the database file and its WAL (missing counts as empty). */
    fun fileDigest(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(dbFile, File(dbFile.path + "-wal")).forEach { if (it.isFile) digest.update(it.readBytes()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        runBlocking { index.close() }
        dispatcher.close()
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    companion object {
        val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

        fun defaultExtensionPath(): String = File(context.applicationInfo.nativeLibraryDir, "vector.so").path

        /** A random unit vector of 256 components; the same [seed] gives the same vector. */
        fun unitVector(seed: Int): FloatArray {
            val random = Random(seed)
            val raw = FloatArray(EmbeddingMath.DIMENSIONS) { random.nextFloat() * 2f - 1f }
            return EmbeddingMath.truncateAndNormalize(raw)
        }

        fun chunk(
            ordinal: Int,
            content: String = "Fragmento $ordinal del documento de prueba.",
            headingPath: String? = null,
            page: Int? = null,
            vector: FloatArray = unitVector(ordinal),
        ) = ChunkInput(
            ordinal = ordinal,
            headingPath = headingPath,
            page = page,
            content = content,
            tokenEstimate = content.length / 4 + 1,
            embedding = vector,
        )
    }
}

/** Keeps every log line so tests can check what the index writes to the log (rule A10). */
class CapturingLogger : AppLogger {
    val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun d(tag: String, message: () -> String) {
        lines += message()
    }

    override fun i(tag: String, message: () -> String) {
        lines += message()
    }

    override fun w(tag: String, throwable: Throwable?, message: () -> String) {
        lines += message() + (throwable?.let { " / ${it.message}" } ?: "")
    }

    override fun e(tag: String, throwable: Throwable?, message: () -> String) {
        lines += message() + (throwable?.let { " / ${it.message}" } ?: "")
    }
}

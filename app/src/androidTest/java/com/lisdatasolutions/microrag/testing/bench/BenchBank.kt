package com.lisdatasolutions.microrag.testing.bench

import android.content.Context
import com.lisdatasolutions.microrag.data.rag.DefaultRagIndex
import com.lisdatasolutions.microrag.data.rag.RagConnection
import com.lisdatasolutions.microrag.data.rag.RagMigrations
import com.lisdatasolutions.microrag.data.rag.RagSchema
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import kotlin.math.ceil
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * F14 (research R2, contract B1, data-model §3): a bank built on the desktop (`run_eval.py bench-bank`) and loaded
 * into its own database in `cacheDir/bench` with the app's [DefaultRagIndex]: the same schema, normalizer and
 * triggers. The database is reused while the manifest does not change. The user's index is never opened.
 */
class BenchBank(private val context: Context, val dir: File) {

    data class Question(val id: String, val question: String, val previous: String?)

    val manifest: JsonObject = Json.parseToJsonElement(File(dir, MANIFEST).readText()).jsonObject
    val size: Int get() = manifest.getValue("n").jsonPrimitive.int

    /** Every file of the manifest with its SHA-256. */
    fun verify(): Boolean = manifest.getValue("files").jsonObject.all { (name, digest) ->
        val file = File(dir, name)
        file.isFile && DevFiles.sha256(file) == digest.jsonPrimitive.content
    }

    fun questions(): List<Question> = File(dir, "questions.jsonl").readLines().filter { it.isNotBlank() }.map { line ->
        val o = Json.parseToJsonElement(line).jsonObject
        Question(o.getValue("id").jsonPrimitive.content, o.getValue("question").jsonPrimitive.content,
            o["previous"]?.jsonPrimitive?.takeIf { it.isString }?.content)
    }

    /** The desktop's query vectors, in the order of [questions]; for the benches without a model. */
    fun queryVectors(): List<FloatArray> = vectors(File(dir, "queries.f32"), questions().size)

    /** The bank's database with its first [limit] chunks (whole documents), built now or reused. */
    fun open(limit: Int = size, int8Candidates: Int = RagParameters.INT8_CANDIDATES): Opened {
        require(limit % CHUNKS_PER_DOCUMENT == 0 && limit <= size) { "limit $limit" }
        val folder = File(context.cacheDir, CACHE).apply { mkdirs() }
        val name = dir.name + if (limit == size) "" else "-$limit"
        val db = File(folder, "$name.db")
        val stamp = File(folder, "$name.manifest")
        val manifestText = File(dir, MANIFEST).readText()
        val reuse = db.isFile && stamp.isFile && stamp.readText() == manifestText
        if (!reuse) {
            listOf("", "-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
            stamp.delete()
        }
        val opened = Opened(db, reused = reuse, int8Candidates = int8Candidates)
        if (!reuse) {
            runBlocking { load(opened.index, limit) }
            stamp.writeText(manifestText)
        }
        return opened
    }

    private suspend fun load(index: DefaultRagIndex, limit: Int) {
        val vectors = RandomAccessFile(File(dir, "vectors.f32"), "r")
        val folders = HashMap<Int, Long>()
        try {
            var current = -1
            var documentId = -1L
            val pending = mutableListOf<ChunkInput>()
            var excluded = false
            suspend fun finish() {
                if (documentId < 0) return
                if (pending.isNotEmpty()) index.insertChunks(documentId, pending.toList())
                pending.clear()
                index.updateStatus(documentId, DocumentStatus.DONE, chunkCount = CHUNKS_PER_DOCUMENT)
                if (excluded) index.setIncluded(documentId, false)
            }
            // Gzipped from the desktop; plain in the androidTest asset, which the packaging decompresses.
            val gz = File(dir, "chunks.jsonl.gz")
            val input = if (gz.isFile) GZIPInputStream(gz.inputStream()) else File(dir, "chunks.jsonl").inputStream()
            input.bufferedReader().useLines { lines ->
                var row = 0
                for (line in lines) {
                    if (row >= limit) break
                    val o = Json.parseToJsonElement(line).jsonObject
                    val doc = o.getValue("doc").jsonPrimitive.int
                    if (doc != current) {
                        finish()
                        current = doc
                        val folderIndex = o.getValue("folder").jsonPrimitive.int
                        val folderId = folders.getOrPut(folderIndex) {
                            checkNotNull(index.createFolder("Bench $folderIndex", "bench $folderIndex"))
                        }
                        documentId = index.addDocument(
                            NewDocument(displayName = o.getValue("name").jsonPrimitive.content, contentHash = "${dir.name}-$doc",
                                folderId = folderId),
                        )
                        excluded = o.getValue("excluded").jsonPrimitive.boolean
                    }
                    val content = o.getValue("text").jsonPrimitive.content
                    pending += ChunkInput(
                        ordinal = o.getValue("ordinal").jsonPrimitive.int,
                        headingPath = o["heading_path"]?.jsonPrimitive?.takeIf { it.isString }?.content,
                        page = null,
                        content = content,
                        tokenEstimate = ceil(content.codePointCount(0, content.length) / RagParameters.CHARS_PER_TOKEN).toInt(),
                        embedding = readVector(vectors, row),
                    )
                    if (pending.size == BATCH) {
                        index.insertChunks(documentId, pending.toList())
                        pending.clear()
                    }
                    row++
                }
            }
            finish()
        } finally {
            vectors.close()
        }
    }

    /** An open bank database on its own single-thread dispatcher; closing keeps the file for the next bench. */
    inner class Opened(val dbFile: File, val reused: Boolean, int8Candidates: Int) : Closeable {
        val logger = CapturingLogger()
        private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "bench-db") }.asCoroutineDispatcher()

        val index = DefaultRagIndex(
            dbFile = dbFile,
            schema = { RagSchema.load(context.assets) },
            extensionPath = TempRagIndex.defaultExtensionPath(),
            config = RagIndexConfig.current(),
            dispatcher = dispatcher,
            logger = logger,
            migrations = RagMigrations.all,
            clock = { System.currentTimeMillis() },
            int8Candidates = int8Candidates,
        )

        /** A separate connection with the extension, for the SQL the benches time directly; the caller's block closes it. */
        fun <T> raw(block: (RagConnection) -> T): T = RagConnection.open(dbFile, TempRagIndex.defaultExtensionPath()).use(block)

        fun chunkCount(): Long = raw { db -> db.queryOne("SELECT COUNT(*) FROM chunks") { it.getLong(0) } ?: 0L }

        /** SQLite-Vector's version, for the reports. */
        fun vectorVersion(): String = raw { db -> db.queryOne("SELECT vector_version()") { it.getText(0) } ?: "?" }

        /** The file with its WAL, in bytes. */
        fun bytes(): Long = listOf(dbFile, File(dbFile.path + "-wal")).filter { it.isFile }.sumOf { it.length() }

        override fun close() {
            runBlocking { index.close() }
            dispatcher.close()
        }
    }

    companion object {
        const val MANIFEST = "manifest.json"
        const val CACHE = "bench"
        const val CHUNKS_PER_DOCUMENT = 25
        private const val BATCH = 64
        private const val DIMENSIONS = 256

        /** [count] little-endian float32 vectors of 256 components. */
        fun vectors(file: File, count: Int): List<FloatArray> = RandomAccessFile(file, "r").use { raf -> (0 until count).map { readVector(raf, it) } }

        private fun readVector(raf: RandomAccessFile, row: Int): FloatArray {
            val bytes = ByteArray(DIMENSIONS * Float.SIZE_BYTES)
            raf.seek(row.toLong() * bytes.size)
            raf.readFully(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            return FloatArray(DIMENSIONS).also { buffer.get(it) }
        }

        /** The bank in `dev-bench`, or null when it is not there (the benches skip). */
        fun inDevBench(context: Context, name: String): BenchBank? =
            File(DevFiles.benchDir(context), name).takeIf { File(it, MANIFEST).isFile }?.let { BenchBank(context, it) }
    }
}

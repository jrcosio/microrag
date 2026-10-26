package com.lisdatasolutions.microrag.data.models

import android.os.StatFs
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [ModelFileRepository] over `noBackupFilesDir/models` (constitution I and V). Verified files are
 * recorded in [records] so later launches can trust them without re-hashing (F03 FR-022).
 */
class DefaultModelFileRepository(
    private val modelsDir: File,
    private val catalog: List<ModelFile> = ModelCatalog.all,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
    private val freeBytes: (File) -> Long = { StatFs(it.path).availableBytes },
    private val records: VerificationRecordStore = VerificationRecordStore(File(modelsDir, RECORD_FILE)),
    private val now: () -> Instant = Instant::now,
) : ModelFileRepository {

    private val mutex = Mutex()
    private val _states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        catalog.associate { it.id to ModelFileState.Missing },
    )
    private val _preparedBackends = MutableStateFlow<Map<ModelId, ComputeBackend>>(emptyMap())

    override val states: StateFlow<Map<ModelId, ModelFileState>> = _states.asStateFlow()
    override val preparedBackends: StateFlow<Map<ModelId, ComputeBackend>> = _preparedBackends.asStateFlow()

    override suspend fun refresh() = withContext(ioDispatcher) {
        mutex.withLock {
            // Leftovers of an interrupted copy; partial downloads live in a subfolder and are kept.
            modelsDir.listFiles { f -> f.isFile && f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
            val recorded = records.read()
            catalog.forEach { file ->
                val target = target(file)
                val current = _states.value[file.id]
                val next = when {
                    !target.isFile -> ModelFileState.Missing
                    current is ModelFileState.Verified && target.length() == file.sizeBytes -> current
                    recorded[file.fileName]?.matches(file, target) == true -> ModelFileState.Verified(target)
                    else -> ModelFileState.Unverified(target)
                }
                set(file.id, next)
            }
            publishPrepared(recorded)
        }
    }

    override suspend fun installVerified(
        id: ModelId,
        source: File,
        deleteSourceOnSuccess: Boolean,
    ): ModelFileState = withContext(ioDispatcher) {
        mutex.withLock {
            val file = file(id)
            val result = if (!source.isFile) {
                ModelFileState.Failed("source not found")
            } else {
                spaceProblem(file) ?: source.inputStream().use { copyVerified(file, it, coroutineContext[Job]) }
            }
            if (result is ModelFileState.Verified && deleteSourceOnSuccess) source.delete()
            finish(file, "install", result)
        }
    }

    override suspend fun installFromStream(id: ModelId, input: InputStream): ModelFileState = withContext(ioDispatcher) {
        mutex.withLock {
            val file = file(id)
            val result = spaceProblem(file) ?: copyVerified(file, input, coroutineContext[Job])
            finish(file, "import", result)
        }
    }

    override suspend fun adopt(id: ModelId, file: File): ModelFileState = withContext(ioDispatcher) {
        mutex.withLock {
            val model = file(id)
            val target = target(model)
            val result = when {
                !file.isFile -> ModelFileState.Failed("download not found")
                !modelsDir.isDirectory && !modelsDir.mkdirs() -> ModelFileState.Failed("cannot create models dir")
                else -> {
                    val actual = file.inputStream().use { hash(model, it, coroutineContext[Job]) }
                    if (actual != model.sha256) {
                        file.delete()
                        ModelFileState.Mismatch(model.sha256, actual)
                    } else {
                        // Same volume, so the rename moves 2.6 GB without copying it (research R4).
                        target.delete()
                        if (file.renameTo(target)) {
                            ModelFileState.Verified(target)
                        } else {
                            ModelFileState.Failed("cannot move ${model.fileName} into place")
                        }
                    }
                }
            }
            finish(model, "adopt", result)
        }
    }

    override suspend fun verify(id: ModelId): ModelFileState = withContext(ioDispatcher) {
        mutex.withLock {
            val file = file(id)
            val target = target(file)
            val result = if (!target.isFile) {
                ModelFileState.Missing
            } else {
                val actual = target.inputStream().use { hash(file, it, coroutineContext[Job]) }
                if (actual == file.sha256) {
                    ModelFileState.Verified(target)
                } else {
                    target.delete()
                    ModelFileState.Mismatch(file.sha256, actual)
                }
            }
            finish(file, "verify", result)
        }
    }

    override suspend fun markPrepared(id: ModelId, backend: ComputeBackend) = withContext(ioDispatcher) {
        mutex.withLock {
            val file = file(id)
            val entry = records.read()[file.fileName]
            if (_states.value[id] !is ModelFileState.Verified || entry == null) {
                logger.w(TAG) { "prepare $id ignored: file not verified" }
                return@withLock
            }
            saveRecord(entry.copy(preparedBackend = backend.name, preparedAt = now().toString()))
            logger.i(TAG) { "prepared $id on $backend" }
        }
    }

    override fun pathOf(id: ModelId): File? = (_states.value[id] as? ModelFileState.Verified)?.path

    private fun finish(file: ModelFile, operation: String, result: ModelFileState): ModelFileState {
        when (result) {
            is ModelFileState.Verified -> recordVerified(file, result.path)
            is ModelFileState.Mismatch, ModelFileState.Missing -> forgetRecord(file)
            else -> Unit
        }
        logger.i(TAG) { "$operation ${file.id}: ${result::class.simpleName}" }
        set(file.id, result)
        publishPrepared(records.read())
        return result
    }

    private fun spaceProblem(file: ModelFile): ModelFileState? = when {
        !modelsDir.isDirectory && !modelsDir.mkdirs() -> ModelFileState.Failed("cannot create models dir")
        freeBytes(modelsDir) < file.sizeBytes + SPACE_MARGIN_BYTES ->
            ModelFileState.Failed("not enough space: ${(file.sizeBytes + SPACE_MARGIN_BYTES) / MB} MB needed")
        else -> null
    }

    private fun copyVerified(file: ModelFile, input: InputStream, job: Job?): ModelFileState {
        val part = File(modelsDir, file.fileName + PART_SUFFIX)
        return try {
            val actual = part.outputStream().use { output -> hash(file, CopyingInputStream(input, output), job) }
            when {
                actual != file.sha256 -> {
                    part.delete()
                    ModelFileState.Mismatch(file.sha256, actual)
                }
                else -> {
                    val target = target(file)
                    target.delete()
                    if (part.renameTo(target)) {
                        ModelFileState.Verified(target)
                    } else {
                        part.delete()
                        ModelFileState.Failed("cannot move ${file.fileName} into place")
                    }
                }
            }
        } catch (e: IOException) {
            part.delete()
            ModelFileState.Failed(e.message ?: e::class.simpleName.orEmpty())
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    /** Hashes [input], publishing progress about every 0.5 % and honoring cancellation. */
    private fun hash(file: ModelFile, input: InputStream, job: Job?): String {
        val total = file.sizeBytes
        val step = maxOf(total / PROGRESS_STEPS, 1L)
        var reported = -1L
        set(file.id, ModelFileState.Verifying(0, total))
        return Sha256.of(input) { bytes ->
            job?.ensureActive()
            if (bytes - reported >= step || bytes == total) {
                reported = bytes
                set(file.id, ModelFileState.Verifying(bytes, total))
            }
        }
    }

    /** Keeps the prepared backend when the very same file is verified again. */
    private fun recordVerified(file: ModelFile, target: File) {
        val previous = records.read()[file.fileName]
        val fresh = VerificationEntry(
            fileName = file.fileName,
            sha256 = file.sha256,
            sizeBytes = target.length(),
            lastModified = target.lastModified(),
            verifiedAt = now().toString(),
        )
        val sameFile = previous != null && previous.matches(file, target)
        saveRecord(if (sameFile) fresh.copy(preparedBackend = previous.preparedBackend, preparedAt = previous.preparedAt) else fresh)
    }

    private fun forgetRecord(file: ModelFile) {
        val entries = records.read()
        if (file.fileName in entries) write(entries - file.fileName)
    }

    private fun saveRecord(entry: VerificationEntry) = write(records.read() + (entry.fileName to entry))

    private fun write(entries: Map<String, VerificationEntry>) {
        try {
            records.write(entries.values)
        } catch (e: IOException) {
            // Losing the record only costs a re-hash on the next launch.
            logger.w(TAG, e) { "cannot write verification record" }
        }
        publishPrepared(entries)
    }

    private fun publishPrepared(entries: Map<String, VerificationEntry>) {
        _preparedBackends.value = catalog.mapNotNull { file ->
            val state = _states.value[file.id] as? ModelFileState.Verified ?: return@mapNotNull null
            val entry = entries[file.fileName]?.takeIf { it.matches(file, state.path) } ?: return@mapNotNull null
            val backend = entry.preparedBackend?.let { name -> ComputeBackend.entries.firstOrNull { it.name == name } }
            backend?.let { file.id to it }
        }.toMap()
    }

    private fun VerificationEntry.matches(file: ModelFile, target: File): Boolean =
        sha256 == file.sha256 && sizeBytes == file.sizeBytes && target.length() == file.sizeBytes &&
            lastModified == target.lastModified()

    private fun set(id: ModelId, state: ModelFileState) = _states.update { it + (id to state) }

    private fun file(id: ModelId): ModelFile = catalog.first { it.id == id }

    private fun target(file: ModelFile) = File(modelsDir, file.fileName)

    /** Writes everything read from [input] to [output], so one pass both copies and hashes. */
    private class CopyingInputStream(input: InputStream, private val output: OutputStream) :
        FilterInputStream(input) {
        override fun read(): Int = super.read().also { if (it >= 0) output.write(it) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) output.write(b, off, it) }
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val PART_SUFFIX = ".part"
        const val RECORD_FILE = "verification.json"
        const val MB = 1024L * 1024L
        const val SPACE_MARGIN_BYTES = 64 * MB
        const val PROGRESS_STEPS = 200L
    }
}

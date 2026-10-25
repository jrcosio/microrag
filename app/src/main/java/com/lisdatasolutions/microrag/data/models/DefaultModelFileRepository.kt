package com.lisdatasolutions.microrag.data.models

import android.os.StatFs
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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

/** [ModelFileRepository] over `noBackupFilesDir/models` (constitution I and V). */
class DefaultModelFileRepository(
    private val modelsDir: File,
    private val catalog: List<ModelFile> = ModelCatalog.all,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
    private val freeBytes: (File) -> Long = { StatFs(it.path).availableBytes },
) : ModelFileRepository {

    private val mutex = Mutex()
    private val _states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        catalog.associate { it.id to ModelFileState.Missing },
    )

    override val states: StateFlow<Map<ModelId, ModelFileState>> = _states.asStateFlow()

    override suspend fun refresh() = withContext(ioDispatcher) {
        mutex.withLock {
            modelsDir.listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
            catalog.forEach { file ->
                val target = target(file)
                val current = _states.value[file.id]
                val next = when {
                    !target.isFile -> ModelFileState.Missing
                    current is ModelFileState.Verified && target.length() == file.sizeBytes -> current
                    else -> ModelFileState.Unverified(target)
                }
                set(file.id, next)
            }
        }
    }

    override suspend fun installVerified(
        id: ModelId,
        source: File,
        deleteSourceOnSuccess: Boolean,
    ): ModelFileState = withContext(ioDispatcher) {
        mutex.withLock {
            val file = file(id)
            val job = coroutineContext[Job]
            val result = when {
                !source.isFile -> ModelFileState.Failed("source not found")
                !modelsDir.isDirectory && !modelsDir.mkdirs() -> ModelFileState.Failed("cannot create models dir")
                freeBytes(modelsDir) < file.sizeBytes + SPACE_MARGIN_BYTES ->
                    ModelFileState.Failed("not enough space: ${(file.sizeBytes + SPACE_MARGIN_BYTES) / MB} MB needed")
                else -> copyVerified(file, source, job)
            }
            if (result is ModelFileState.Verified && deleteSourceOnSuccess) source.delete()
            logger.i(TAG) { "install ${file.id}: ${result::class.simpleName}" }
            set(id, result)
            result
        }
    }

    override suspend fun verify(id: ModelId): ModelFileState = withContext(ioDispatcher) {
        mutex.withLock {
            val file = file(id)
            val target = target(file)
            val result = if (!target.isFile) {
                ModelFileState.Missing
            } else {
                val job = coroutineContext[Job]
                val actual = target.inputStream().use { hash(file, it, job) }
                if (actual == file.sha256) {
                    ModelFileState.Verified(target)
                } else {
                    target.delete()
                    ModelFileState.Mismatch(file.sha256, actual)
                }
            }
            logger.i(TAG) { "verify ${file.id}: ${result::class.simpleName}" }
            set(id, result)
            result
        }
    }

    override fun pathOf(id: ModelId): File? = (_states.value[id] as? ModelFileState.Verified)?.path

    private fun copyVerified(file: ModelFile, source: File, job: Job?): ModelFileState {
        val part = File(modelsDir, file.fileName + PART_SUFFIX)
        return try {
            val actual = source.inputStream().use { input ->
                part.outputStream().use { output -> hash(file, CopyingInputStream(input, output), job) }
            }
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
        const val MB = 1024L * 1024L
        const val SPACE_MARGIN_BYTES = 64 * MB
        const val PROGRESS_STEPS = 200L
    }
}

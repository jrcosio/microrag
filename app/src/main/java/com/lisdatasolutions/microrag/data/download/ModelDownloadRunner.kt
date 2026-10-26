package com.lisdatasolutions.microrag.data.download

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.onboarding.ModelSetupPolicy
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** End of one [ModelDownloadRunner.runQueue]; [retryLater] asks the scheduler to run again when online. */
data class DownloadRunResult(val outcome: DownloadOutcome?, val retryLater: Boolean)

/**
 * Downloads the requested models one by one (F03 FR-014): space check, download with retries, then
 * hash-and-move through the repository. Shared by the UIDT job and the WorkManager worker, and
 * observed by the UI through [progress]. Logs carry model ids, codes and sizes only (FR-028).
 */
class ModelDownloadRunner(
    private val downloader: FileDownloader,
    private val repository: ModelFileRepository,
    private val store: OnboardingStore,
    private val dirs: AppDirs,
    private val freeBytes: () -> Long,
    private val hasInternet: () -> Boolean,
    private val logger: AppLogger,
    private val required: List<ModelFile> = ModelCatalog.required,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    /** Forgets the previous results before a new run is scheduled (retry, resume). */
    fun resetOutcomes() = _progress.update { it.copy(outcomes = emptyMap()) }

    suspend fun runQueue(): DownloadRunResult = mutex.withLock {
        repository.refresh()
        val requested = store.downloadRequested.first()
        var last: DownloadOutcome? = null
        for (file in required) {
            if (file.id !in requested || repository.states.value[file.id] is ModelFileState.Verified) continue
            val outcome = download(file)
            last = outcome
            _progress.update { it.copy(active = null, outcomes = it.outcomes + (file.id to outcome)) }
            logger.i(TAG) { "download ${file.id}: ${outcome::class.simpleName}" }
            if (outcome is DownloadOutcome.Failed && outcome.error is DownloadError.Network && !hasInternet()) {
                // Lost the network: not a failure, the system runs the job again once online.
                _progress.update { it.copy(outcomes = it.outcomes - file.id) }
                return DownloadRunResult(null, retryLater = true)
            }
            if (outcome !is DownloadOutcome.Completed) return DownloadRunResult(outcome, retryLater = false)
        }
        DownloadRunResult(last, retryLater = false)
    }

    private suspend fun download(file: ModelFile): DownloadOutcome {
        val partial = dirs.partial(file)
        var failures = 0
        while (true) {
            val before = partial.length()
            missingSpace(file, before)?.let { return DownloadOutcome.NoSpace(file.id, it) }
            publish(file, before, force = true)
            val result = try {
                downloader.download(file, partial) { bytes -> publish(file, bytes, force = false) }
            } catch (e: CancellationException) {
                _progress.update { it.copy(active = null, outcomes = it.outcomes + (file.id to DownloadOutcome.Cancelled(file.id))) }
                throw e
            }
            when (result) {
                DownloadResult.Complete -> {
                    _progress.update { it.copy(active = null) }
                    return when (val state = repository.adopt(file.id, partial)) {
                        is ModelFileState.Verified -> DownloadOutcome.Completed(file.id)
                        is ModelFileState.Mismatch -> DownloadOutcome.Failed(file.id, DownloadError.HashMismatch)
                        else -> DownloadOutcome.Failed(file.id, DownloadError.Io((state as? ModelFileState.Failed)?.reason ?: "adopt"))
                    }
                }
                is DownloadResult.Failed -> {
                    val error = result.error
                    if (error is DownloadError.Io) missingSpace(file, partial.length())?.let { return DownloadOutcome.NoSpace(file.id, it) }
                    if (error.isPermanent() || (error is DownloadError.Network && !hasInternet())) {
                        return DownloadOutcome.Failed(file.id, error)
                    }
                    failures = if (partial.length() - before >= PROGRESS_RESETS_RETRIES_BYTES) 1 else failures + 1
                    logger.w(TAG) { "download ${file.id} attempt failed ($failures/$MAX_FAILURES): $error" }
                    if (failures >= MAX_FAILURES) return DownloadOutcome.Failed(file.id, error)
                    sleep(BASE_BACKOFF_MS shl (failures - 1))
                }
            }
        }
    }

    /** Bytes missing when free space does not cover the rest of the file plus the margin (FR-016). */
    private fun missingSpace(file: ModelFile, bytesOnDisk: Long): Long? {
        val needed = file.sizeBytes - bytesOnDisk + ModelSetupPolicy.SPACE_MARGIN_BYTES
        return (needed - freeBytes()).takeIf { it > 0 }
    }

    private var lastPublishedBytes = 0L
    private var lastPublishedAt = 0L

    /** Throttled to every 0.5 % or 500 ms so the UI and the notification are not flooded (FR-017). */
    private fun publish(file: ModelFile, bytes: Long, force: Boolean) {
        val now = nowMs()
        val step = file.sizeBytes / PROGRESS_STEPS
        if (!force && bytes != file.sizeBytes && bytes - lastPublishedBytes < step && now - lastPublishedAt < PROGRESS_INTERVAL_MS) return
        lastPublishedBytes = bytes
        lastPublishedAt = now
        _progress.update { it.copy(active = file.id, bytesDone = bytes, totalBytes = file.sizeBytes) }
    }

    private fun DownloadError.isPermanent(): Boolean =
        this is DownloadError.ForbiddenHost || this == DownloadError.InsecureScheme || this == DownloadError.TooManyRedirects ||
            (this is DownloadError.Http && code in 400..499 && code != 408 && code != 429)

    private companion object {
        const val TAG = "MicroRAG"
        const val MAX_FAILURES = 5
        const val BASE_BACKOFF_MS = 2_000L
        const val PROGRESS_RESETS_RETRIES_BYTES = 1L * 1024 * 1024
        const val PROGRESS_STEPS = 200L
        const val PROGRESS_INTERVAL_MS = 500L
    }
}

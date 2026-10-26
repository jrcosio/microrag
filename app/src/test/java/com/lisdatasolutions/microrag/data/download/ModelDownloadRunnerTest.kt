package com.lisdatasolutions.microrag.data.download

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.ModelSetupPolicy
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Queue, space, retries and progress of the shared download runner (T028, T055). */
class ModelDownloadRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val eg = ModelCatalog.byId(ModelId.EG2_270M)
    private val gemma = ModelCatalog.byId(ModelId.GEMMA4_E2B)
    private val store = FakeOnboardingStore()
    private val logger = RecordingLogger()
    private val sleeps = mutableListOf<Long>()
    private var free = Long.MAX_VALUE
    private var online = true

    /** Scripted downloader: each call takes the next step for that model. */
    private class ScriptedDownloader : FileDownloader {
        val calls = mutableListOf<ModelId>()
        val steps = mutableMapOf<ModelId, ArrayDeque<suspend (File, (Long) -> Unit) -> DownloadResult>>()

        fun on(id: ModelId, step: suspend (File, (Long) -> Unit) -> DownloadResult) {
            steps.getOrPut(id) { ArrayDeque() }.addLast(step)
        }

        override suspend fun download(file: ModelFile, partial: File, onProgress: (Long) -> Unit): DownloadResult {
            calls += file.id
            return steps.getValue(file.id).removeFirst()(partial, onProgress)
        }
    }

    private val downloader = ScriptedDownloader()

    private fun runner(
        repository: InMemoryModelFileRepository = InMemoryModelFileRepository(),
        required: List<ModelFile> = ModelCatalog.required,
    ) = ModelDownloadRunner(
        downloader = downloader,
        repository = repository,
        store = store,
        dirs = AppDirs(tmp.root),
        freeBytes = { free },
        hasInternet = { online },
        logger = logger,
        required = required,
        sleep = { sleeps += it },
        nowMs = { 0L },
    )

    /** Writes [bytes] to the partial file, as a real download would. */
    private fun writes(bytes: Long, result: DownloadResult = DownloadResult.Complete): suspend (File, (Long) -> Unit) -> DownloadResult =
        { partial, progress ->
            partial.parentFile!!.mkdirs()
            partial.appendBytes(ByteArray(bytes.toInt()))
            progress(partial.length())
            result
        }

    @Test
    fun queue_downloadsTheEmbeddingModelFirstAndSkipsVerifiedOnes() = runTest {
        store.requestDownload(setOf(ModelId.GEMMA4_E2B, ModelId.EG2_270M))
        downloader.on(ModelId.EG2_270M, writes(10))
        downloader.on(ModelId.GEMMA4_E2B, writes(10))
        val repository = InMemoryModelFileRepository()

        val result = runner(repository).runQueue()

        assertThat(downloader.calls).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
        assertThat(repository.adopted).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
        assertThat(result).isEqualTo(DownloadRunResult(DownloadOutcome.Completed(ModelId.GEMMA4_E2B), retryLater = false))

        downloader.calls.clear()
        runner(InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))).runQueue()
        assertThat(downloader.calls).isEmpty()
    }

    @Test
    fun missingSpace_stopsBeforeDownloading() = runTest {
        store.requestDownload(setOf(ModelId.GEMMA4_E2B))
        free = gemma.sizeBytes // the margin is missing

        val result = runner().runQueue()

        assertThat(result.outcome).isEqualTo(DownloadOutcome.NoSpace(ModelId.GEMMA4_E2B, ModelSetupPolicy.SPACE_MARGIN_BYTES))
        assertThat(downloader.calls).isEmpty()
    }

    @Test
    fun diskFullWhileWriting_isNoSpaceWithoutRetries() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M))
        downloader.on(ModelId.EG2_270M) { partial, _ ->
            partial.parentFile!!.mkdirs()
            partial.writeBytes(ByteArray(100))
            free = 0
            DownloadResult.Failed(DownloadError.Io("ENOSPC"))
        }

        val result = runner().runQueue()

        assertThat(result.outcome).isInstanceOf(DownloadOutcome.NoSpace::class.java)
        assertThat(sleeps).isEmpty()
        assertThat(AppDirs(tmp.root).partial(eg).length()).isEqualTo(100)
    }

    @Test
    fun hashMismatch_failsAndStopsTheQueue() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))
        downloader.on(ModelId.EG2_270M, writes(10))

        val result = runner(InMemoryModelFileRepository(corrupt = setOf(ModelId.EG2_270M))).runQueue()

        assertThat(result.outcome).isEqualTo(DownloadOutcome.Failed(ModelId.EG2_270M, DownloadError.HashMismatch))
        assertThat(downloader.calls).containsExactly(ModelId.EG2_270M)
    }

    @Test
    fun fiveFailuresInARow_giveUpAfterGrowingWaits() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M))
        repeat(5) { downloader.on(ModelId.EG2_270M) { _, _ -> DownloadResult.Failed(DownloadError.Http(503)) } }

        val result = runner().runQueue()

        assertThat(result.outcome).isEqualTo(DownloadOutcome.Failed(ModelId.EG2_270M, DownloadError.Http(503)))
        assertThat(sleeps).containsExactly(2_000L, 4_000L, 8_000L, 16_000L).inOrder()
        assertThat(downloader.calls).hasSize(5)
    }

    @Test
    fun anAttemptThatMovesAMegabyte_resetsTheRetryCount() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M))
        repeat(4) { downloader.on(ModelId.EG2_270M) { _, _ -> DownloadResult.Failed(DownloadError.Network("reset")) } }
        downloader.on(ModelId.EG2_270M, writes(2L * 1024 * 1024, DownloadResult.Failed(DownloadError.Network("reset"))))
        downloader.on(ModelId.EG2_270M, writes(10))

        val result = runner().runQueue()

        assertThat(result.outcome).isEqualTo(DownloadOutcome.Completed(ModelId.EG2_270M))
        assertThat(sleeps).containsExactly(2_000L, 4_000L, 8_000L, 16_000L, 2_000L).inOrder()
    }

    @Test
    fun forbiddenOrigin_isNotRetried() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M))
        downloader.on(ModelId.EG2_270M) { _, _ -> DownloadResult.Failed(DownloadError.ForbiddenHost("x.com")) }

        assertThat(runner().runQueue().outcome).isEqualTo(DownloadOutcome.Failed(ModelId.EG2_270M, DownloadError.ForbiddenHost("x.com")))
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun lostNetwork_waitsForTheSchedulerInsteadOfFailing() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M))
        downloader.on(ModelId.EG2_270M) { _, _ ->
            online = false
            DownloadResult.Failed(DownloadError.Network("unreachable"))
        }
        val runner = runner()

        val result = runner.runQueue()

        assertThat(result).isEqualTo(DownloadRunResult(null, retryLater = true))
        assertThat(runner.progress.value.outcomes).isEmpty()
    }

    @Test
    fun progress_isRealAndNeverGoesBackWithinAnAttempt() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M))
        val small = eg.copy(sizeBytes = 5_000)
        val runner = runner(required = listOf(small))
        val seen = mutableListOf<Long>()
        downloader.on(ModelId.EG2_270M) { partial, progress ->
            partial.parentFile!!.mkdirs()
            repeat(5) {
                partial.appendBytes(ByteArray(1_000))
                progress(partial.length())
                seen += runner.progress.value.bytesDone
            }
            DownloadResult.Complete
        }

        runner.runQueue()

        assertThat(seen).isInOrder()
        assertThat(seen.last()).isEqualTo(5_000L)
        assertThat(runner.progress.value.active).isNull()
    }

    @Test
    fun cancellation_isRecordedAndKeepsThePartialFile() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M))
        val gate = CompletableDeferred<Unit>()
        downloader.on(ModelId.EG2_270M) { partial, _ ->
            partial.parentFile!!.mkdirs()
            partial.writeBytes(ByteArray(1234))
            gate.await()
            DownloadResult.Complete
        }
        val runner = runner()

        val run = async { runner.runQueue() }
        testScheduler.advanceUntilIdle()
        run.cancel()
        testScheduler.advanceUntilIdle()

        assertThat(runCatching { run.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(runner.progress.value.outcomes[ModelId.EG2_270M]).isEqualTo(DownloadOutcome.Cancelled(ModelId.EG2_270M))
        assertThat(AppDirs(tmp.root).partial(eg).length()).isEqualTo(1234)
    }

    @Test
    fun logs_carryNoUrlsOrPaths() = runTest {
        store.requestDownload(setOf(ModelId.EG2_270M))
        repeat(5) { downloader.on(ModelId.EG2_270M) { _, _ -> DownloadResult.Failed(DownloadError.Http(500)) } }

        runner().runQueue()

        assertThat(logger.lines).isNotEmpty()
        logger.lines.forEach { line ->
            assertThat(line).doesNotContain("http")
            assertThat(line).doesNotContain(tmp.root.path)
        }
    }
}

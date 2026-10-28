package com.lisdatasolutions.microrag.feature.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.DefaultIndexingRepository
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DefaultImportQueueRepository
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFiles
import com.lisdatasolutions.microrag.data.ingest.DocumentImporter
import com.lisdatasolutions.microrag.data.library.DefaultLibraryRepository
import com.lisdatasolutions.microrag.domain.library.DeleteDocumentUseCase
import com.lisdatasolutions.microrag.domain.ingest.ImportDocumentsUseCase
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.ObserveImportQueueUseCase
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.RemoveQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.RetryQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.StartIndexingUseCase
import com.lisdatasolutions.microrag.testing.FakeDocumentSource
import com.lisdatasolutions.microrag.testing.FakeIngestScheduler
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * F06 US4 (T058): the queue lives in the repository, in the application scope, so a recreated screen
 * (new ViewModel) shows the same rows, failed attempts included, and repeats no import (US4-1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngestRecreationTest {

    private val dispatcher = StandardTestDispatcher()
    private val dir = Files.createTempDirectory("documents").toFile()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    @Test
    fun aRecreatedScreenShowsTheSameQueueWithoutImportingAgain() = runTest(dispatcher) {
        val source = FakeDocumentSource().apply {
            add("content://ok", "bueno.txt", "texto".toByteArray(), "text/plain")
            add("content://bad", "hoja.xlsx", byteArrayOf(1), "application/vnd.ms-excel")
        }
        val index = FakeRagIndex()
        val pdf = FakePdfOpener()
        val files = DocumentFileStore(dir)
        val logger = RecordingLogger()
        val scheduler = FakeIngestScheduler()
        val runs = InMemoryIngestRunStore()
        val documentFiles = DocumentFiles(files, index, dispatcher, logger)
        val indexing = DefaultIndexingRepository(index, scheduler, runs, FakeThermalStatusSource(), dispatcher, logger)
        val repository = DefaultImportQueueRepository(
            DocumentImporter(source, files, pdf, index, { Long.MAX_VALUE / 2 }, logger),
            source, index, documentFiles, scheduler, pdf, indexing, runs,
            DeleteDocumentUseCase(DefaultLibraryRepository(index, documentFiles, runs, indexing, dispatcher, logger) { 0L }),
            backgroundScope, dispatcher, logger,
        ) { 0L }
        fun viewModel() = IngestViewModel(
            ObserveImportQueueUseCase(repository), ImportDocumentsUseCase(repository), StartIndexingUseCase(repository),
            RetryQueueItemUseCase(repository), RemoveQueueItemUseCase(repository), backgroundDispatcher = dispatcher,
        )

        val before = viewModel()
        before.onFilesPicked(listOf("content://ok", "content://bad"))
        advanceUntilIdle()
        val rows = before.state.value.items.map { it.displayName to it.state }

        val after = viewModel()
        advanceUntilIdle()

        assertThat(rows).containsExactly(
            "hoja.xlsx" to QueueItemState.ImportFailed(IngestError.UNSUPPORTED_FORMAT),
            "bueno.txt" to QueueItemState.Ready,
        ).inOrder()
        assertThat(after.state.value.items.map { it.displayName to it.state }).isEqualTo(rows)
        assertThat(source.opened).containsExactly("content://ok")
        assertThat(index.docs).hasSize(1)
    }
}

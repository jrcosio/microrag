package com.lisdatasolutions.microrag.feature.library

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.ObserveLibraryUseCase
import com.lisdatasolutions.microrag.domain.library.SetIncludedUseCase
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import java.io.IOException
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

/** F08 T016: the Library query lives in the SavedStateHandle and the page becomes the screen's content. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLibraryRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        LibraryViewModel(saved, ObserveLibraryUseCase(repository), SetIncludedUseCase(repository), dispatcher)

    private fun doc(id: Long, name: String, state: LibraryDocumentState = LibraryDocumentState.Indexed(10, null)) =
        LibraryDocument(id, name, DocumentFormat.PDF, state, included = true)

    private fun documents(vm: LibraryViewModel) = (vm.state.value.content as LibraryContent.Documents).documents

    @Test
    fun startsLoading_thenShowsTheDocuments() = runTest(dispatcher) {
        repository.pageReady.value = false
        repository.documents.value = listOf(doc(2, "Guía.pdf"), doc(1, "Manual.pdf"))
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.content).isEqualTo(LibraryContent.Loading)

        repository.pageReady.value = true
        advanceUntilIdle()

        val content = vm.state.value.content as LibraryContent.Documents
        assertThat(content.documents.map { it.id }).containsExactly(2L, 1L).inOrder()
        assertThat(content.total).isEqualTo(2)
        assertThat(content.hasMore).isFalse()
    }

    @Test
    fun theQueryLivesInTheSavedStateHandle() = runTest(dispatcher) {
        repository.documents.value = (1L..120L).map { doc(it, "Doc $it.pdf") }
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        advanceUntilIdle()

        vm.onTextChange("doc")
        vm.onFilterChange(LibraryFilter.INDEXED)
        advanceUntilIdle()
        vm.onLoadMore()
        advanceUntilIdle()

        // A new ViewModel on the same handle, as after process death, asks for the same query.
        val restored = viewModel(saved)
        advanceUntilIdle()
        assertThat(restored.state.value.text).isEqualTo("doc")
        assertThat(restored.state.value.filter).isEqualTo(LibraryFilter.INDEXED)
        assertThat(repository.queries.last()).isEqualTo(LibraryQuery("doc", LibraryFilter.INDEXED, 100))
    }

    @Test
    fun theTextIsSearchedWithoutCaseOrAccents() = runTest(dispatcher) {
        repository.documents.value = listOf(doc(2, "Guía de uso.pdf"), doc(1, "Manual.pdf"))
        val vm = viewModel()

        vm.onTextChange("GUIA")
        advanceUntilIdle()

        assertThat(documents(vm).map { it.displayName }).containsExactly("Guía de uso.pdf")
        assertThat(vm.state.value.text).isEqualTo("GUIA")
    }

    @Test
    fun loadMore_addsABlockOnlyWhenThereIsMore() = runTest(dispatcher) {
        repository.documents.value = (1L..120L).reversed().map { doc(it, "Doc $it.pdf") }
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(documents(vm)).hasSize(50)

        vm.onLoadMore()
        vm.onLoadMore() // Before the block arrives: ignored.
        advanceUntilIdle()
        assertThat(documents(vm)).hasSize(100)

        vm.onLoadMore()
        advanceUntilIdle()
        assertThat(documents(vm)).hasSize(120)
        assertThat((vm.state.value.content as LibraryContent.Documents).hasMore).isFalse()

        val asked = repository.queries.size
        vm.onLoadMore()
        advanceUntilIdle()
        assertThat(repository.queries).hasSize(asked)
    }

    @Test
    fun aNewSearchOrFilter_startsAgainFromTheFirstBlock() = runTest(dispatcher) {
        repository.documents.value = (1L..120L).map { doc(it, "Doc $it.pdf") }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onLoadMore()
        advanceUntilIdle()

        vm.onTextChange("doc")
        advanceUntilIdle()

        assertThat(repository.queries.last().limit).isEqualTo(LibraryQuery.PAGE)
    }

    @Test
    fun clearFilters_goesBackToAllWithoutText() = runTest(dispatcher) {
        repository.documents.value = listOf(doc(1, "Manual.pdf"))
        val vm = viewModel()
        vm.onTextChange("nada")
        vm.onFilterChange(LibraryFilter.PENDING)
        advanceUntilIdle()
        assertThat(vm.state.value.content).isEqualTo(LibraryContent.NoResults)

        vm.onClearFilters()
        advanceUntilIdle()

        assertThat(vm.state.value.text).isEmpty()
        assertThat(vm.state.value.filter).isEqualTo(LibraryFilter.ALL)
        assertThat(documents(vm)).hasSize(1)
    }

    @Test
    fun anEmptyLibrary_isEmpty_butAFilterWithoutMatches_isNoResults() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.content).isEqualTo(LibraryContent.Empty)

        repository.documents.value = listOf(doc(1, "Pendiente.pdf", LibraryDocumentState.Pending))
        vm.onFilterChange(LibraryFilter.INDEXED)
        advanceUntilIdle()
        assertThat(vm.state.value.content).isEqualTo(LibraryContent.NoResults)
    }

    @Test
    fun aReadError_showsTheErrorAndRetryLoadsAgain() = runTest(dispatcher) {
        repository.documents.value = listOf(doc(1, "Manual.pdf"))
        repository.pageError = IOException("disk")
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.content).isEqualTo(LibraryContent.Error)

        vm.onRetry()
        advanceUntilIdle()

        assertThat(documents(vm)).hasSize(1)
    }

    @Test
    fun anUnavailableIndex_isUnavailable() = runTest(dispatcher) {
        repository.unavailable = true
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.content).isEqualTo(LibraryContent.Unavailable)
    }
}

package com.lisdatasolutions.microrag.debug.feasibility

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FeasibilityViewModelModelsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repository: FakeModelFileRepository, devDir: File) = FeasibilityViewModel(
        repository = repository,
        importer = DevModelImporter(devDir, repository, StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun opening_refreshesAndVerifiesPresentFiles() = runTest(main) {
        val repository = FakeModelFileRepository(present = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B))

        val vm = viewModel(repository, tmp.newFolder("dev-models"))
        advanceUntilIdle()

        assertThat(repository.verified).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B)
        val rows = vm.state.value.models.associateBy { it.file.id }
        assertThat(rows.getValue(ModelId.EG2_270M).state).isInstanceOf(ModelFileState.Verified::class.java)
        assertThat(rows.getValue(ModelId.EG2_740M).state).isEqualTo(ModelFileState.Missing)
        assertThat(vm.state.value.models.map { it.file }).isEqualTo(ModelCatalog.all)
    }

    @Test
    fun rows_showPendingCopiesAndImportInstallsThem() = runTest(main) {
        val repository = FakeModelFileRepository()
        val devDir = tmp.newFolder("dev-models")
        File(devDir, ModelCatalog.byId(ModelId.EG2_740M).fileName).writeBytes(ByteArray(42))

        val vm = viewModel(repository, devDir)
        advanceUntilIdle()
        assertThat(vm.state.value.models.first { it.file.id == ModelId.EG2_740M }.devCopyBytes).isEqualTo(42L)
        assertThat(vm.state.value.canImport).isTrue()

        vm.importDevModels()
        advanceUntilIdle()

        val row = vm.state.value.models.first { it.file.id == ModelId.EG2_740M }
        assertThat(row.state).isInstanceOf(ModelFileState.Verified::class.java)
        assertThat(row.devCopyBytes).isNull()
        assertThat(vm.state.value.canImport).isFalse()
    }

    @Test
    fun importingACorruptCopy_keepsShowingTheMismatch() = runTest(main) {
        val repository = FakeModelFileRepository(corrupt = setOf(ModelId.EG2_270M))
        val devDir = tmp.newFolder("dev-models")
        File(devDir, ModelCatalog.byId(ModelId.EG2_270M).fileName).writeBytes(ByteArray(10))
        val vm = viewModel(repository, devDir)
        advanceUntilIdle()

        vm.importDevModels()
        advanceUntilIdle()

        val row = vm.state.value.models.first { it.file.id == ModelId.EG2_270M }
        assertThat(row.state).isInstanceOf(ModelFileState.Mismatch::class.java)
        assertThat(row.devCopyBytes).isEqualTo(10L)
    }

    @Test
    fun testsAreDisabledUntilAModelOfTheirFamilyIsVerified() = runTest(main) {
        val repository = FakeModelFileRepository(
            present = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B),
            corrupt = setOf(ModelId.GEMMA4_E2B),
        )

        val vm = viewModel(repository, tmp.newFolder("dev-models"))
        advanceUntilIdle()

        assertThat(vm.state.value.canRunEmbeddings).isTrue()
        assertThat(vm.state.value.canRunGeneration).isFalse()
        assertThat(vm.state.value.models.first { it.file.id == ModelId.GEMMA4_E2B }.state)
            .isInstanceOf(ModelFileState.Mismatch::class.java)
    }
}

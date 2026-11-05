package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.DevModelImporter
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReportWriter
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualStatus
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** F18 T011 (finding M8): the screen's state and actions, written before the screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class VisualFeasibilityViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val main = StandardTestDispatcher()
    private lateinit var fixture: VisualFixture
    private lateinit var visualDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        fixture = VisualFixture(main, tmp.newFolder("set"))
        visualDir = tmp.newFolder("dev-visual")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = VisualFeasibilityViewModel(
        repository = fixture.base.repository,
        importer = DevModelImporter(tmp.newFolder("dev-models"), fixture.base.repository, main),
        deviceSets = DeviceSetLoader(visualDir),
        suite = fixture.suite,
        coordinator = fixture.base.coordinator,
        reports = VisualReportWriter(tmp.newFolder("reports"), main),
        ioDispatcher = main,
    )

    private fun copySet() {
        val bytes = byteArrayOf(9, 9)
        File(visualDir, "images").mkdirs()
        File(visualDir, "images/a.png").writeBytes(bytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        File(visualDir, "manifest.json").writeText("""{"version": 1, "files": [{"path": "images/a.png", "sha256": "$sha", "kind": "image"}]}""")
    }

    @Test
    fun openingVerifiesTheModelsAndReadsTheDeviceSet() = runTest(main) {
        copySet()
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.models.map { it.file.id }).containsExactly(ModelId.GEMMA4_E2B, ModelId.EG2_440M, ModelId.EG2_270M).inOrder()
        assertThat(state.models.all { it.state is ModelFileState.Verified }).isTrue()
        assertThat(state.deviceSet.present).isTrue()
        assertThat(state.deviceSet.verified).isEqualTo(1)
        assertThat(state.canRun(VisualBlock.GEMMA_PAGES)).isTrue()
    }

    @Test
    fun withoutTheDeviceSetOnlyTheBlocksThatNeedNoSetCanRun() = runTest(main) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.deviceSet.present).isFalse()
        assertThat(VisualBlock.entries.filter(state::canRun)).containsExactly(VisualBlock.GEMMA_INFO, VisualBlock.EMBED_TEXT)
    }

    @Test
    fun aSetCopiedLaterIsReadOnReload() = runTest(main) {
        val vm = viewModel()
        advanceUntilIdle()
        copySet()

        vm.reloadDeviceSet()
        advanceUntilIdle()

        assertThat(vm.state.value.deviceSet.present).isTrue()
    }

    @Test
    fun onStopCancelsTheRunWaitsAndReleasesEveryEngine() = runTest(main) {
        val reached = CompletableDeferred<Unit>()
        fixture.onGenerate = { call -> if (call == 2) { reached.complete(Unit); CompletableDeferred<Unit>().await() } }
        val vm = viewModel()
        advanceUntilIdle()

        vm.run(setOf(VisualBlock.GEMMA_PAGES))
        reached.await()
        runCurrent()
        assertThat(vm.state.value.isRunning).isTrue()
        assertThat(vm.state.value.canRun(VisualBlock.RASTER)).isFalse()

        vm.onStop()
        advanceUntilIdle()

        assertThat(vm.state.value.isRunning).isFalse()
        assertThat(vm.state.value.results.last().status).isEqualTo(VisualStatus.CANCELLED)
        assertThat(fixture.base.coordinator.loaded.value).isEmpty()
    }

    @Test
    fun cancelStopsTheRun() = runTest(main) {
        val reached = CompletableDeferred<Unit>()
        fixture.onGenerate = { call -> if (call == 2) { reached.complete(Unit); CompletableDeferred<Unit>().await() } }
        val vm = viewModel()
        advanceUntilIdle()

        vm.run(setOf(VisualBlock.GEMMA_PAGES))
        reached.await()
        vm.cancelRun()
        advanceUntilIdle()

        assertThat(vm.state.value.isRunning).isFalse()
        assertThat(vm.state.value.results.last().status).isEqualTo(VisualStatus.CANCELLED)
    }
}

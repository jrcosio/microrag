package com.lisdatasolutions.microrag.debug.feasibility

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.report.ReportWriter
import com.lisdatasolutions.microrag.debug.feasibility.report.ResultStatus
import com.lisdatasolutions.microrag.domain.model.ModelId
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

@OptIn(ExperimentalCoroutinesApi::class)
class FeasibilityViewModelLifecycleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val main = StandardTestDispatcher()
    private val fixture = SuiteFixture(main, present = setOf(ModelId.EG2_270M))

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = FeasibilityViewModel(
        repository = fixture.repository,
        importer = DevModelImporter(tmp.newFolder("dev-models"), fixture.repository, main),
        suite = fixture.suite,
        coordinator = fixture.coordinator,
        reports = ReportWriter(tmp.newFolder("reports"), main),
    )

    @Test
    fun onStop_cancelsTheRunAndThenReleasesEveryEngine() = runTest(main) {
        val reached = CompletableDeferred<Unit>()
        fixture.onQuery = { call -> if (call == 5) { reached.complete(Unit); CompletableDeferred<Unit>().await() } }
        val vm = viewModel()
        advanceUntilIdle()

        vm.run(setOf(SuiteBlock.EMBEDDINGS))
        reached.await()
        runCurrent()
        assertThat(vm.state.value.isRunning).isTrue()
        assertThat(fixture.coordinator.loaded.value).isNotEmpty()

        vm.onStop()
        advanceUntilIdle()

        assertThat(vm.state.value.isRunning).isFalse()
        assertThat(vm.state.value.results.single().status).isEqualTo(ResultStatus.CANCELLED)
        assertThat(fixture.coordinator.loaded.value).isEmpty()
        assertThat(vm.state.value.lastReportPath).endsWith(".md")
    }

    @Test
    fun hotDevice_waitsForTheUserAnswer() = runTest(main) {
        fixture.thermalLevel = ThermalLevel.CRITICAL
        val vm = viewModel()
        advanceUntilIdle()

        vm.run(setOf(SuiteBlock.EMBEDDINGS))
        advanceUntilIdle()
        assertThat(vm.state.value.thermalPrompt).isEqualTo(ThermalLevel.CRITICAL)

        vm.answerThermal(false)
        advanceUntilIdle()
        vm.answerThermal(false)
        advanceUntilIdle()

        assertThat(vm.state.value.thermalPrompt).isNull()
        assertThat(vm.state.value.isRunning).isFalse()
        assertThat(vm.state.value.results.map { it.reason }).containsExactly("thermal", "thermal")
    }

    @Test
    fun runningDisablesModelActionsAndOtherRuns() = runTest(main) {
        val reached = CompletableDeferred<Unit>()
        fixture.onQuery = { if (it == 1) { reached.complete(Unit); CompletableDeferred<Unit>().await() } }
        val vm = viewModel()
        advanceUntilIdle()

        vm.run(setOf(SuiteBlock.EMBEDDINGS))
        reached.await()
        runCurrent()

        assertThat(vm.state.value.canRunAll).isFalse()
        assertThat(vm.state.value.canVerify).isFalse()
        vm.cancelRun()
        advanceUntilIdle()
        assertThat(vm.state.value.canRunAll).isTrue()
    }
}

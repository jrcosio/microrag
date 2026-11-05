package com.lisdatasolutions.microrag.debug.feasibility.visual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.debug.feasibility.DevModelImporter
import com.lisdatasolutions.microrag.debug.feasibility.ModelRow
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.DeviceSetSummary
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReportWriter
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State and actions of «Viabilidad visual» (F18, debug only). */
class VisualFeasibilityViewModel(
    private val repository: ModelFileRepository,
    private val importer: DevModelImporter,
    private val deviceSets: DeviceSetLoader,
    private val suite: VisualSuite,
    private val coordinator: EngineCoordinator,
    private val reports: VisualReportWriter,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private data class RunUi(
        val running: Boolean = false,
        val thermalPrompt: ThermalLevel? = null,
        val error: String? = null,
        val latestOnOpen: String? = null,
    )

    private val pendingCopies = MutableStateFlow<Map<ModelId, Long>>(emptyMap())
    private val modelsBusy = MutableStateFlow(false)
    private val deviceSet = MutableStateFlow(DeviceSetSummary(present = false, verified = 0, rejected = emptyList()))
    private val runUi = MutableStateFlow(RunUi())
    private var runJob: Job? = null
    private var thermalAnswer: CompletableDeferred<Boolean>? = null

    private val modelsState = combine(repository.states, pendingCopies, modelsBusy, deviceSet) { states, copies, busy, set ->
        VisualFeasibilityUiState(models = rows(states, copies), modelsBusy = busy, deviceSet = set)
    }

    val state: StateFlow<VisualFeasibilityUiState> = combine(
        modelsState,
        runUi,
        suite.progress,
        suite.results,
        suite.lastReport,
    ) { models, run, progress, results, lastReport ->
        models.copy(
            isRunning = run.running,
            progress = progress,
            results = results,
            lastReportPath = lastReport?.path?.replace(".json", ".md") ?: run.latestOnOpen,
            thermalPrompt = run.thermalPrompt,
            runError = run.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VisualFeasibilityUiState(models = rows(emptyMap(), emptyMap())))

    init {
        launchModelsWork {
            importer.ensureDir()
            withContext(ioDispatcher) { deviceSets.ensureDir() }
            refreshAndVerify()
            readDeviceSet()
        }
        viewModelScope.launch {
            val latest = reports.latest()?.path
            runUi.update { it.copy(latestOnOpen = latest) }
        }
    }

    fun importDevModels() = launchModelsWork {
        importer.importAll()
        pendingCopies.value = importer.pendingCopies()
    }

    /** Reads `dev-visual` again, for a set copied with adb while the screen was open. */
    fun reloadDeviceSet() = launchModelsWork { readDeviceSet() }

    fun run(blocks: Set<VisualBlock>, candidates: List<String> = emptyList()) {
        if (runUi.value.running || modelsBusy.value) return
        runUi.update { it.copy(running = true, error = null) }
        runJob = viewModelScope.launch {
            try {
                suite.run(blocks, ::askThermal, candidates)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runUi.update { it.copy(error = "${e::class.simpleName}: ${e.message.orEmpty()}") }
            } finally {
                runUi.update { it.copy(running = false, thermalPrompt = null) }
            }
        }
    }

    fun cancelRun() {
        runJob?.cancel()
    }

    fun answerThermal(proceed: Boolean) {
        thermalAnswer?.complete(proceed)
    }

    /** Screen stopped or left: the test in progress is cancelled and every engine released once it stops (FR-017). */
    fun onStop() {
        val job = runJob
        viewModelScope.launch {
            job?.cancel()
            job?.join()
            withContext(NonCancellable) { coordinator.releaseAll() }
        }
    }

    private suspend fun askThermal(level: ThermalLevel): Boolean {
        val answer = CompletableDeferred<Boolean>()
        thermalAnswer = answer
        runUi.update { it.copy(thermalPrompt = level) }
        return try {
            answer.await()
        } finally {
            thermalAnswer = null
            runUi.update { it.copy(thermalPrompt = null) }
        }
    }

    private suspend fun readDeviceSet() {
        val set = withContext(ioDispatcher) { deviceSets.load() }
        deviceSet.value = DeviceSetSummary(set.present, set.verified.size, set.rejected, set.parityPx)
    }

    private suspend fun refreshAndVerify() {
        repository.refresh()
        pendingCopies.value = importer.pendingCopies()
        repository.states.value
            .filter { (id, state) -> id in VISUAL_MODELS && state is ModelFileState.Unverified }
            .keys
            .forEach { repository.verify(it) }
    }

    private fun launchModelsWork(work: suspend () -> Unit) {
        if (modelsBusy.value || runUi.value.running) return
        modelsBusy.value = true
        viewModelScope.launch {
            try {
                work()
            } finally {
                modelsBusy.value = false
            }
        }
    }

    private fun rows(states: Map<ModelId, ModelFileState>, copies: Map<ModelId, Long>) =
        VISUAL_MODELS.map { id -> ModelRow(ModelCatalog.byId(id), states[id] ?: ModelFileState.Missing, copies[id]) }
}

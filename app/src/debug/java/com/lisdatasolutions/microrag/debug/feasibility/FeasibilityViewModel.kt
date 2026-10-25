package com.lisdatasolutions.microrag.debug.feasibility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.debug.feasibility.report.ReportWriter
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
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

/** State and actions of the on-device feasibility diagnostics (debug only). */
class FeasibilityViewModel(
    private val repository: ModelFileRepository,
    private val importer: DevModelImporter,
    private val suite: FeasibilitySuite,
    private val coordinator: EngineCoordinator,
    private val reports: ReportWriter,
) : ViewModel() {

    private data class RunUi(
        val running: Boolean = false,
        val thermalPrompt: ThermalLevel? = null,
        val error: String? = null,
        val latestOnOpen: String? = null,
    )

    private val pendingCopies = MutableStateFlow<Map<ModelId, Long>>(emptyMap())
    private val modelsBusy = MutableStateFlow(false)
    private val runUi = MutableStateFlow(RunUi())
    private var runJob: Job? = null
    private var thermalAnswer: CompletableDeferred<Boolean>? = null

    private val modelsState = combine(repository.states, pendingCopies, modelsBusy) { states, copies, busy ->
        FeasibilityUiState(models = rows(states, copies), modelsBusy = busy)
    }

    val state: StateFlow<FeasibilityUiState> = combine(
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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeasibilityUiState(models = rows(emptyMap(), emptyMap())))

    init {
        // US1 scenario 1: opening the tool verifies whatever is already installed.
        launchModelsWork {
            importer.ensureDir()
            refreshAndVerify(reverifyVerified = false)
        }
        viewModelScope.launch {
            val latest = reports.latest()?.path
            runUi.update { it.copy(latestOnOpen = latest) }
        }
    }

    fun importDevModels() = launchModelsWork {
        // No refresh afterwards: it would turn a just-reported mismatch back into "missing".
        importer.importAll()
        pendingCopies.value = importer.pendingCopies()
    }

    fun verifyAgain() = launchModelsWork { refreshAndVerify(reverifyVerified = true) }

    fun run(blocks: Set<SuiteBlock>) {
        if (runUi.value.running || modelsBusy.value) return
        runUi.update { it.copy(running = true, error = null) }
        runJob = viewModelScope.launch {
            try {
                suite.run(blocks, ::askThermal)
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

    /**
     * Screen stopped (app in background) or left: the test in progress is cancelled and every
     * engine released once it has stopped (FR-013).
     */
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

    private suspend fun refreshAndVerify(reverifyVerified: Boolean) {
        repository.refresh()
        pendingCopies.value = importer.pendingCopies()
        repository.states.value
            .filterValues { it is ModelFileState.Unverified || (reverifyVerified && it is ModelFileState.Verified) }
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
        ModelCatalog.all.map { file ->
            val state = states[file.id] ?: ModelFileState.Missing
            ModelRow(file, state, copies[file.id])
        }
}

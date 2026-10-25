package com.lisdatasolutions.microrag.debug.feasibility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State and actions of the on-device feasibility diagnostics (debug only). */
class FeasibilityViewModel(
    private val repository: ModelFileRepository,
    private val importer: DevModelImporter,
) : ViewModel() {

    private val pendingCopies = MutableStateFlow<Map<ModelId, Long>>(emptyMap())
    private val modelsBusy = MutableStateFlow(false)

    val state: StateFlow<FeasibilityUiState> = combine(
        repository.states,
        pendingCopies,
        modelsBusy,
    ) { states, copies, busy ->
        FeasibilityUiState(models = rows(states, copies), modelsBusy = busy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeasibilityUiState(models = rows(emptyMap(), emptyMap())))

    init {
        // US1 scenario 1: opening the tool verifies whatever is already installed.
        launchModelsWork {
            importer.ensureDir()
            refreshAndVerify(reverifyVerified = false)
        }
    }

    fun importDevModels() = launchModelsWork {
        importer.importAll()
        refreshAndVerify(reverifyVerified = false)
    }

    fun verifyAgain() = launchModelsWork { refreshAndVerify(reverifyVerified = true) }

    private suspend fun refreshAndVerify(reverifyVerified: Boolean) {
        repository.refresh()
        pendingCopies.value = importer.pendingCopies()
        repository.states.value
            .filterValues { it is ModelFileState.Unverified || (reverifyVerified && it is ModelFileState.Verified) }
            .keys
            .forEach { repository.verify(it) }
    }

    private fun launchModelsWork(work: suspend () -> Unit) {
        if (modelsBusy.value) return
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

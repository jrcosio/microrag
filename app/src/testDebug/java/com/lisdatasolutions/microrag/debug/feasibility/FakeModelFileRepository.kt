package com.lisdatasolutions.microrag.debug.feasibility

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory repository: files listed in [present] exist; [corrupt] ones fail verification. */
class FakeModelFileRepository(
    present: Set<ModelId> = emptySet(),
    private val corrupt: Set<ModelId> = emptySet(),
) : ModelFileRepository {

    private val onDisk = present.toMutableSet()
    val installs = mutableListOf<Pair<ModelId, File>>()
    val verified = mutableListOf<ModelId>()

    override val states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        ModelCatalog.all.associate { it.id to ModelFileState.Missing },
    )

    override suspend fun refresh() {
        states.update { current ->
            ModelCatalog.all.associate { file ->
                file.id to when {
                    file.id !in onDisk -> ModelFileState.Missing
                    current[file.id] is ModelFileState.Verified -> current.getValue(file.id)
                    else -> ModelFileState.Unverified(path(file.id))
                }
            }
        }
    }

    override suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean): ModelFileState {
        installs += id to source
        val state = if (id in corrupt) {
            ModelFileState.Mismatch(ModelCatalog.byId(id).sha256, "0".repeat(64))
        } else {
            onDisk += id
            if (deleteSourceOnSuccess) source.delete()
            ModelFileState.Verified(path(id))
        }
        states.update { it + (id to state) }
        return state
    }

    override suspend fun verify(id: ModelId): ModelFileState {
        verified += id
        val state = when {
            id !in onDisk -> ModelFileState.Missing
            id in corrupt -> ModelFileState.Mismatch(ModelCatalog.byId(id).sha256, "0".repeat(64)).also { onDisk -= id }
            else -> ModelFileState.Verified(path(id))
        }
        states.update { it + (id to state) }
        return state
    }

    override fun pathOf(id: ModelId): File? = (states.value[id] as? ModelFileState.Verified)?.path

    private fun path(id: ModelId) = File("/models/${ModelCatalog.byId(id).fileName}")
}

package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.rules.ExternalResource
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/**
 * Replaces the model files with in-memory ones already verified and tested, so instrumented tests can
 * reach «Continuar» without multi-gigabyte downloads and without touching the device's real models.
 * Unloading an override drops the type altogether, so the app's own instance is put back afterwards:
 * later tests in the same process need it (Home since F09).
 */
class ReadyModelsRule : ExternalResource() {

    private val override = module { single<ModelFileRepository> { ReadyModelFileRepository() } }
    private var original: ModelFileRepository? = null

    override fun before() {
        original = GlobalContext.get().getOrNull<ModelFileRepository>()
        loadKoinModules(override)
    }

    override fun after() {
        unloadKoinModules(override)
        original?.let { repository -> loadKoinModules(module { single<ModelFileRepository> { repository } }) }
    }
}

private class ReadyModelFileRepository : ModelFileRepository {
    private fun path(id: ModelId) = File("/models/${ModelCatalog.byId(id).fileName}")

    override val states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        ModelCatalog.all.associate { it.id to if (it in ModelCatalog.required) ModelFileState.Verified(path(it.id)) else ModelFileState.Missing },
    )
    override val preparedBackends = MutableStateFlow(
        mapOf(ModelId.EG2_270M to ComputeBackend.CPU, ModelId.GEMMA4_E2B to ComputeBackend.GPU),
    )

    override suspend fun refresh() = Unit

    override suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean) = states.value.getValue(id)

    override suspend fun adopt(id: ModelId, file: File) = states.value.getValue(id)

    override suspend fun installFromStream(id: ModelId, input: InputStream) = states.value.getValue(id)

    override suspend fun verify(id: ModelId) = states.value.getValue(id)

    override suspend fun markPrepared(id: ModelId, backend: ComputeBackend) = Unit

    override fun pathOf(id: ModelId): File? = (states.value[id] as? ModelFileState.Verified)?.path
}

package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.ModelRequirements
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.ExternalResource
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/**
 * Replaces the model files with in-memory ones already verified and tested, so instrumented tests can
 * reach «Continuar» without multi-gigabyte downloads and without touching the device's real models.
 * The ready ones are those the app needs right now (F19 research R5): the 270M and Gemma, or the 440M and
 * Gemma on a device with the multimodal mode on, as the reference phone since F19.
 * Unloading an override drops the type altogether, so the app's own instance is put back afterwards:
 * later tests in the same process need it (Home since F09).
 */
class ReadyModelsRule : ExternalResource() {

    private var override = module { }
    private var original: ModelFileRepository? = null

    override fun before() {
        original = GlobalContext.get().getOrNull<ModelFileRepository>()
        val required = runBlocking { withTimeout(REQUIREMENTS_TIMEOUT_MS) { GlobalContext.get().get<ModelRequirements>().current() } }
        override = module { single<ModelFileRepository> { ReadyModelFileRepository(required.files.map { it.id }.toSet()) } }
        loadKoinModules(override)
    }

    override fun after() {
        unloadKoinModules(override)
        original?.let { repository -> loadKoinModules(module { single<ModelFileRepository> { repository } }) }
    }

    private companion object {
        const val REQUIREMENTS_TIMEOUT_MS = 10_000L
    }
}

private class ReadyModelFileRepository(ready: Set<ModelId>) : ModelFileRepository {
    private fun path(id: ModelId) = File("/models/${ModelCatalog.byId(id).fileName}")

    override val states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        ModelCatalog.all.associate { it.id to if (it.id in ready) ModelFileState.Verified(path(it.id)) else ModelFileState.Missing },
    )
    override val preparedBackends = MutableStateFlow(
        ready.associateWith { id -> if (id == ModelId.GEMMA4_E2B) ComputeBackend.GPU else ComputeBackend.CPU },
    )

    override suspend fun refresh() = Unit

    override suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean) = states.value.getValue(id)

    override suspend fun adopt(id: ModelId, file: File) = states.value.getValue(id)

    override suspend fun installFromStream(id: ModelId, input: InputStream) = states.value.getValue(id)

    override suspend fun verify(id: ModelId) = states.value.getValue(id)

    override suspend fun markPrepared(id: ModelId, backend: ComputeBackend) = Unit

    override fun pathOf(id: ModelId): File? = (states.value[id] as? ModelFileState.Verified)?.path

    override suspend fun delete(id: ModelId): Boolean {
        val present = states.value[id] is ModelFileState.Verified
        states.value = states.value + (id to ModelFileState.Missing)
        preparedBackends.value = preparedBackends.value - id
        return present
    }
}

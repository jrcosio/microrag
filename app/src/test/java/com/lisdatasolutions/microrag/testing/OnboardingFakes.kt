package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.NetworkKind
import com.lisdatasolutions.microrag.domain.onboarding.DeviceResources
import com.lisdatasolutions.microrag.domain.onboarding.ModelDownloadController
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory model files: [verified] ids are installed and pass verification; [corrupt] ones do not. */
class InMemoryModelFileRepository(
    verified: Set<ModelId> = emptySet(),
    private val corrupt: Set<ModelId> = emptySet(),
) : ModelFileRepository {
    val refreshes = mutableListOf<Unit>()
    val adopted = mutableListOf<ModelId>()
    val verifyCalls = mutableListOf<ModelId>()
    val streamed = mutableListOf<Pair<ModelId, Long>>()
    var unverifiedOnRefresh: Set<ModelId> = emptySet()

    override val states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        ModelCatalog.all.associate { it.id to if (it.id in verified) ModelFileState.Verified(path(it.id)) else ModelFileState.Missing },
    )
    override val preparedBackends = MutableStateFlow<Map<ModelId, ComputeBackend>>(emptyMap())

    override suspend fun refresh() {
        refreshes += Unit
        states.update { current ->
            current.mapValues { (id, state) ->
                when {
                    id in unverifiedOnRefresh -> ModelFileState.Unverified(path(id))
                    state is ModelFileState.Mismatch || state is ModelFileState.Failed -> ModelFileState.Missing
                    else -> state
                }
            }
        }
    }

    override suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean) = result(id)

    override suspend fun adopt(id: ModelId, file: File): ModelFileState {
        adopted += id
        return result(id).also { if (it is ModelFileState.Mismatch) file.delete() }
    }

    override suspend fun installFromStream(id: ModelId, input: InputStream): ModelFileState {
        streamed += id to input.readBytes().size.toLong()
        return result(id)
    }

    override suspend fun verify(id: ModelId): ModelFileState {
        verifyCalls += id
        return result(id)
    }

    override suspend fun markPrepared(id: ModelId, backend: ComputeBackend) {
        preparedBackends.update { it + (id to backend) }
    }

    override fun pathOf(id: ModelId): File? = (states.value[id] as? ModelFileState.Verified)?.path

    private fun result(id: ModelId): ModelFileState {
        val state = if (id in corrupt) ModelFileState.Mismatch(ModelCatalog.byId(id).sha256, "0".repeat(64)) else ModelFileState.Verified(path(id))
        states.update { it + (id to state) }
        return state
    }

    private fun path(id: ModelId) = File("/models/${ModelCatalog.byId(id).fileName}")
}

/** [inner], with refresh() held while [gate] is set and not completed: what Home shows during a check (F09). */
class GatedModelFileRepository(val inner: InMemoryModelFileRepository) : ModelFileRepository by inner {
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun refresh() {
        gate?.await()
        inner.refresh()
    }
}

class FakeDeviceResources(
    var free: Long = 100_000_000_000L,
    var ram: Long = 12_000_000_000L,
    var network: NetworkKind = NetworkKind.WIFI,
    var storageOk: Boolean = true,
) : DeviceResources {
    override val hasInternet = MutableStateFlow(true)

    override fun totalRamBytes() = ram

    override fun freeBytes() = free

    override fun currentNetwork() = network

    override fun prepareStorage() = storageOk
}

class FakeModelDownloadController(
    private val partials: MutableMap<ModelId, Long> = mutableMapOf(),
) : ModelDownloadController {
    override val progress = MutableStateFlow(DownloadProgress())
    val scheduled = mutableListOf<Long>()
    var cancels = 0
    var running = false
    var orphanCleanups = 0

    override fun schedule(pendingBytes: Long) {
        progress.update { it.copy(outcomes = emptyMap()) }
        scheduled += pendingBytes
        running = true
    }

    override fun cancel() {
        cancels++
        running = false
    }

    override suspend fun isScheduledOrRunning() = running

    override fun partialBytes(id: ModelId) = partials[id] ?: 0L

    override fun deleteOrphanPartials() {
        orphanCleanups++
    }
}

/** Keeps every log line, to check that nothing private is logged (FR-028). */
class RecordingLogger : AppLogger {
    val lines = mutableListOf<String>()

    override fun d(tag: String, message: () -> String) {
        lines += message()
    }

    override fun i(tag: String, message: () -> String) {
        lines += message()
    }

    override fun w(tag: String, throwable: Throwable?, message: () -> String) {
        lines += message()
    }

    override fun e(tag: String, throwable: Throwable?, message: () -> String) {
        lines += message()
    }
}

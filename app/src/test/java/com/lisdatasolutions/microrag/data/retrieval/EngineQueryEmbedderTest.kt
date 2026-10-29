package com.lisdatasolutions.microrag.data.retrieval

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.FakeTextEmbedder
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.CPU
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.GPU
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingEngineException
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.ModelMetadata
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F10 T013 (research R4, FR-018, FR-021): readiness by the F03 rule, a CPU engine loaded on demand and never held. */
class EngineQueryEmbedderTest {

    private val coordinator = EngineCoordinator(RecordingLogger())
    private val factory = FakeFactory()
    private val models = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M)).apply {
        preparedBackends.value = mapOf(ModelId.EG2_270M to GPU)
    }
    private val embedder = EngineQueryEmbedder(coordinator, factory, models)
    private val cpuKey = EngineKey(ModelId.EG2_270M, CPU)

    private class FakeFactory : EngineFactory {
        val created = mutableListOf<ComputeBackend>()
        val engines = mutableListOf<FakeTextEmbedder>()
        var loadFails = false
        var failEngineTimes = 0

        override fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder {
            created += backend
            if (loadFails) return object : TextEmbedder by FakeTextEmbedder(backend) {
                override suspend fun initialize() = throw IllegalStateException("cannot load")
            }
            return FakeTextEmbedder(backend).also {
                it.failEngineTimes = failEngineTimes
                engines += it
            }
        }

        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig): TextGenerator =
            error("unused")

        override fun metadata(path: File): ModelMetadata = error("unused")

        override fun clearCompiledCache() = Unit

        override var benchmarkStatsEnabled: Boolean = false
    }

    @Test
    fun ready_whenVerifiedAndPrepared_withoutHashing() = runTest {
        assertThat(embedder.ready()).isTrue()
        assertThat(models.refreshes).isEmpty()
        assertThat(models.verifyCalls).isEmpty()
    }

    @Test
    fun notReady_whenUnverifiedMissingOrNotPrepared() = runTest {
        models.preparedBackends.value = emptyMap()
        assertThat(embedder.ready()).isFalse()

        models.preparedBackends.value = mapOf(ModelId.EG2_270M to CPU)
        models.states.update { it + (ModelId.EG2_270M to ModelFileState.Unverified(File("eg2"))) }
        assertThat(embedder.ready()).isFalse()

        models.states.update { it + (ModelId.EG2_270M to ModelFileState.Missing) }
        assertThat(embedder.ready()).isFalse()
        assertThat(models.verifyCalls).isEmpty()
        assertThat(factory.created).isEmpty()
    }

    @Test
    fun anUnknownPath_refreshesTheFolderOnceAndLooksAgain() = runTest {
        val fresh = InMemoryModelFileRepository().apply { preparedBackends.value = mapOf(ModelId.EG2_270M to CPU) }
        val embedder = EngineQueryEmbedder(coordinator, factory, fresh)

        assertThat(embedder.ready()).isFalse()
        assertThat(fresh.refreshes).hasSize(1)

        fresh.states.update { it + (ModelId.EG2_270M to ModelFileState.Verified(File("eg2"))) }
        assertThat(embedder.ready()).isTrue()
        assertThat(fresh.refreshes).hasSize(1)
        assertThat(fresh.verifyCalls).isEmpty()
    }

    @Test
    fun embed_usesTheCpuEngine_loadedOnceAndReused() = runTest {
        assertThat(embedder.loaded).isFalse()

        val first = embedder.embed("¿Y el aceite?", "¿Cada cuánto se revisa la bomba?")
        assertThat(embedder.loaded).isTrue()
        val second = embedder.embed("¿Qué es una bomba?", null)

        assertThat(factory.created).containsExactly(CPU)
        assertThat(coordinator.loaded.value).containsExactly(cpuKey)
        assertThat(factory.engines.single().embedded).containsExactly(
            EmbeddingPrefixes.query("¿Cada cuánto se revisa la bomba? ¿Y el aceite?"),
            EmbeddingPrefixes.query("¿Qué es una bomba?"),
        ).inOrder()
        assertThat(first.cuts).isEqualTo(0)
        assertThat(second.vector).isEqualTo(FakeTextEmbedder.vectorOf(EmbeddingPrefixes.query("¿Qué es una bomba?")))
    }

    @Test
    fun theEngineIsNeverHeld_soMemoryCallbacksCloseIt_andTheNextQueryReloads() = runTest {
        embedder.embed("primera", null)

        coordinator.releaseUnheld()
        assertThat(factory.engines.single().closed).isTrue()
        assertThat(embedder.loaded).isFalse()

        embedder.embed("segunda", null)
        assertThat(factory.created).containsExactly(CPU, CPU)
        assertThat(embedder.loaded).isTrue()
        coordinator.releaseUnheld()
        assertThat(factory.engines.last().closed).isTrue()
    }

    @Test
    fun loadAndEngineFailures_propagate() = runTest {
        factory.loadFails = true
        val load = runCatching { embedder.embed("pregunta", null) }
        assertThat(load.exceptionOrNull()).hasMessageThat().isEqualTo("cannot load")
        assertThat(embedder.loaded).isFalse()

        factory.loadFails = false
        factory.failEngineTimes = 1
        val compute = runCatching { embedder.embed("pregunta", null) }
        assertThat(compute.exceptionOrNull()).isInstanceOf(EmbeddingEngineException::class.java)
    }
}

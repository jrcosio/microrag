package com.lisdatasolutions.microrag.data.indexing

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.CPU
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.GPU
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingEngineException
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.ManagedEngine
import com.lisdatasolutions.microrag.rag.runtime.ModelMetadata
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F07 T022 (research R4, rule N15): backend choice, GPU check, held engine and recovery. */
class ChunkEmbeddingSessionTest {

    private val logger = RecordingLogger()
    private val coordinator = EngineCoordinator(logger)
    private val runStore = InMemoryIngestRunStore()
    private val factory = FakeFactory()
    private val sha = ModelCatalog.byId(ModelId.EG2_270M).sha256

    private class FakeFactory : EngineFactory {
        val created = mutableListOf<ComputeBackend>()
        val engines = mutableListOf<FakeTextEmbedder>()
        var gpuLoadFails = false
        var gpuSalt = ""
        val failures = mutableMapOf<ComputeBackend, Int>()

        override fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder {
            created += backend
            if (backend == GPU && gpuLoadFails) return object : TextEmbedder by FakeTextEmbedder(GPU) {
                override suspend fun initialize() = throw IllegalStateException("GPU delegate unavailable")
            }
            return FakeTextEmbedder(backend, salt = if (backend == GPU) gpuSalt else "").also {
                it.failEngineTimes = failures.remove(backend) ?: 0
                engines += it
            }
        }

        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig): TextGenerator =
            error("unused")

        override fun metadata(path: File): ModelMetadata = error("unused")

        override fun clearCompiledCache() = Unit

        override var benchmarkStatsEnabled: Boolean = false
    }

    private fun session(isEmulator: Boolean = false, modelPath: File? = File("model.litertlm")) =
        ChunkEmbeddingSession(coordinator, factory, { modelPath }, runStore, appVersion = 7, logger = logger, isEmulator = isEmulator)

    @Test
    fun releasesTheLlmBeforeLoading() = runTest {
        val llm = EngineKey(ModelId.GEMMA4_E2B, CPU)
        coordinator.withEngine(llm, { object : ManagedEngine { override val backend = CPU; override fun close() = Unit } }) { }

        session().use { }

        assertThat(coordinator.loaded.value).doesNotContain(llm)
    }

    @Test
    fun onTheEmulator_neverTheGpu() = runTest {
        val backend = session(isEmulator = true).use { it.backend }

        assertThat(backend).isEqualTo(CPU)
        assertThat(factory.created).doesNotContain(GPU)
    }

    @Test
    fun firstSession_checksTheGpuAgainstTheCpuAndRemembers() = runTest {
        assertThat(session().use { it.backend }).isEqualTo(GPU)
        assertThat(runStore.gpuCheck()).isEqualTo("$sha|7|gpu")

        factory.created.clear()
        assertThat(session().use { it.backend }).isEqualTo(GPU)
        assertThat(factory.created).containsExactly(GPU)
    }

    @Test
    fun gpuWithOtherVectors_indexesOnTheCpu() = runTest {
        factory.gpuSalt = "different"

        assertThat(session().use { it.backend }).isEqualTo(CPU)
        assertThat(runStore.gpuCheck()).isEqualTo("$sha|7|cpu")
        assertThat(logger.lines.joinToString()).contains("gpu check min cosine")
    }

    @Test
    fun gpuThatDoesNotLoad_indexesOnTheCpu() = runTest {
        factory.gpuLoadFails = true

        assertThat(session().use { it.backend }).isEqualTo(CPU)
        assertThat(runStore.gpuCheck()).endsWith("|cpu")
    }

    @Test
    fun aCheckOfAnotherModelOrVersion_isRepeated() = runTest {
        runStore.setGpuCheck("$sha|6|cpu")

        assertThat(session().use { it.backend }).isEqualTo(GPU)
        assertThat(runStore.gpuCheck()).isEqualTo("$sha|7|gpu")
    }

    @Test
    fun theEngineIsHeldDuringTheBlockAndReleasedAfter() = runTest {
        session(isEmulator = true).use { embedder ->
            embedder.embed("texto", "Manual")
            assertThat(coordinator.loaded.value).containsExactly(EngineKey(ModelId.EG2_270M, CPU))
        }

        assertThat(coordinator.loaded.first()).isEmpty()
    }

    @Test
    fun aFailingEngine_isReloadedOnceThenTheGpuGivesWayToTheCpu() = runTest {
        runStore.setGpuCheck("$sha|7|gpu")
        factory.failures[GPU] = 1
        var runs = 0
        val backends = mutableListOf<ComputeBackend>()

        session().use { embedder ->
            runs++
            backends += embedder.backend
            embedder.embed("texto $runs", "Manual")
            if (runs < 3) {
                // The second GPU engine fails as well: the session goes on with the CPU.
                if (runs == 2) throw EmbeddingEngineException(IllegalStateException("second failure"))
            }
        }

        assertThat(backends).containsExactly(GPU, GPU, CPU).inOrder()
    }

    @Test
    fun aFailingCpu_endsTheSession() = runTest {
        factory.failures[CPU] = 1
        val result = runCatching {
            session(isEmulator = true).use { embedder ->
                embedder.embed("texto", "Manual")
                throw EmbeddingEngineException(IllegalStateException("still broken"))
            }
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(EmbeddingEngineException::class.java)
        assertThat(coordinator.loaded.value).isEmpty()
    }

    @Test
    fun withoutTheModel_itIsNotReady() {
        assertThat(session(modelPath = null).modelReady()).isFalse()
        assertThat(session().modelReady()).isTrue()
    }
}

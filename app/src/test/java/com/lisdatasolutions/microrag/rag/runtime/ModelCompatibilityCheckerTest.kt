package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** T048: real on-device test rules (research R8) with fake engines and the real coordinator. */
class ModelCompatibilityCheckerTest {

    private val path = File("/models/x.litertlm")
    private val eg = ModelCatalog.byId(ModelId.EG2_270M)
    private val gemma = ModelCatalog.byId(ModelId.GEMMA4_E2B)

    private class FakeFactory(
        private val declared: List<ComputeBackend> = ComputeBackend.entries,
        private val rawDimensions: Int = 768,
        private val vectorSize: Int = EmbeddingMath.DIMENSIONS,
        private val failingGenerators: Set<ComputeBackend> = emptySet(),
        private val silentGenerators: Set<ComputeBackend> = emptySet(),
        private val unreadable: Boolean = false,
    ) : EngineFactory {
        val opened = mutableListOf<Pair<ModelId, ComputeBackend>>()
        val closed = mutableListOf<Pair<ModelId, ComputeBackend>>()
        var maxOpenGpu = 0
        private var openGpu = 0
        override var benchmarkStatsEnabled = false

        override fun metadata(path: File): ModelMetadata {
            if (unreadable) throw IllegalStateException("not a litertlm file")
            return ModelMetadata(maxContextTokens = 8192, supportedBackends = declared)
        }

        override fun clearCompiledCache() = Unit

        private fun open(id: ModelId, backend: ComputeBackend) {
            opened += id to backend
            if (backend == ComputeBackend.GPU) maxOpenGpu = maxOf(maxOpenGpu, ++openGpu)
        }

        private fun close(id: ModelId, backend: ComputeBackend) {
            closed += id to backend
            if (backend == ComputeBackend.GPU) openGpu--
        }

        override fun embedder(file: ModelFile, path: File, backend: ComputeBackend) = object : TextEmbedder {
            override val model = file.id
            override val backend = backend
            override var rawDimensions: Int? = null
            override suspend fun initialize() = open(file.id, backend)
            override suspend fun embedQuery(text: String): FloatArray {
                rawDimensions = this@FakeFactory.rawDimensions
                return FloatArray(vectorSize).also { it[0] = 1f }
            }
            override suspend fun embedDocument(content: String, header: String) = embedQuery(content)
            override fun close() = close(file.id, backend)
        }

        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig) = object : TextGenerator {
            override val model = file.id
            override val backend = backend
            override suspend fun initialize() {
                open(file.id, backend)
                if (backend in failingGenerators) throw IllegalStateException("delegate unavailable")
            }
            override fun generate(prompt: String): Flow<GenerationEvent> = flow {
                if (backend !in silentGenerators) emit(GenerationEvent.Token("listo"))
            }
            override fun cancel() = Unit
            override fun close() = close(file.id, backend)
        }
    }

    private fun checker(factory: FakeFactory) = ModelCompatibilityChecker(factory, EngineCoordinator(RecordingLogger()), RecordingLogger())

    @Test
    fun embeddings_pass768To256UnitVectorsOnCpu() = runTest {
        val factory = FakeFactory()

        assertThat(checker(factory).check(eg, path)).isEqualTo(CompatibilityResult.Compatible(ComputeBackend.CPU))
        assertThat(factory.opened).containsExactly(ModelId.EG2_270M to ComputeBackend.CPU)
        assertThat(factory.closed).containsExactly(ModelId.EG2_270M to ComputeBackend.CPU)
    }

    @Test
    fun embeddings_withAnotherShape_areIncompatible() = runTest {
        assertThat(checker(FakeFactory(rawDimensions = 512)).check(eg, path)).isEqualTo(CompatibilityResult.Incompatible)
        assertThat(checker(FakeFactory(vectorSize = 128)).check(eg, path)).isEqualTo(CompatibilityResult.Incompatible)
        assertThat(checker(FakeFactory(declared = listOf(ComputeBackend.GPU))).check(eg, path)).isEqualTo(CompatibilityResult.Incompatible)
    }

    @Test
    fun gemma_prefersTheGpu() = runTest {
        val factory = FakeFactory()

        assertThat(checker(factory).check(gemma, path)).isEqualTo(CompatibilityResult.Compatible(ComputeBackend.GPU))
        assertThat(factory.closed).containsExactly(ModelId.GEMMA4_E2B to ComputeBackend.GPU)
    }

    @Test
    fun gemma_fallsBackToCpuWhenTheGpuFailsOrStaysSilent() = runTest {
        val failing = FakeFactory(failingGenerators = setOf(ComputeBackend.GPU))
        assertThat(checker(failing).check(gemma, path)).isEqualTo(CompatibilityResult.Compatible(ComputeBackend.CPU))
        assertThat(failing.closed).containsExactly(ModelId.GEMMA4_E2B to ComputeBackend.GPU, ModelId.GEMMA4_E2B to ComputeBackend.CPU).inOrder()

        val silent = FakeFactory(silentGenerators = setOf(ComputeBackend.GPU))
        assertThat(checker(silent).check(gemma, path)).isEqualTo(CompatibilityResult.Compatible(ComputeBackend.CPU))
        assertThat(silent.maxOpenGpu).isAtMost(1)
    }

    @Test
    fun gemma_failingEverywhere_orUnreadable_isIncompatible() = runTest {
        val nowhere = FakeFactory(failingGenerators = ComputeBackend.entries.toSet())
        assertThat(checker(nowhere).check(gemma, path)).isEqualTo(CompatibilityResult.Incompatible)
        assertThat(nowhere.closed).hasSize(2)

        assertThat(checker(FakeFactory(unreadable = true)).check(gemma, path)).isEqualTo(CompatibilityResult.Incompatible)
    }
}

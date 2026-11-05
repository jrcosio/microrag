package com.lisdatasolutions.microrag.data.multimodal

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.FakeTextEmbedder
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.ModelMetadata
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/** F19 T020 (contract M5, research R3): the identity of the two text embedders on this device. */
class IdentityValidatorTest {

    private val logger = RecordingLogger()
    private val coordinator = EngineCoordinator(logger)
    private val models = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B, ModelId.EG2_440M))
    private val texts = listOf(
        IdentityText("q01", "query", "¿Cada cuántas horas hay que revisar el motor de la bomba?"),
        IdentityText("d01", "doc", "El aceite del motor se cambia cada 500 horas de uso."),
    )

    /** The 440M's vectors are the 270M's tilted by [angle] radians towards a fixed axis. */
    private inner class Factory(private val tilt: Double) : EngineFactory {
        val loads = mutableListOf<Pair<ModelId, ComputeBackend>>()

        override fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder {
            loads += file.id to backend
            // Never two embedders loaded at once.
            assertThat(coordinator.loaded.value).isEmpty()
            val base = FakeTextEmbedder(backend, file.id)
            if (file.id == ModelId.EG2_270M) return base
            return object : TextEmbedder by base {
                override suspend fun embedQuery(text: String) = tilted(base.embedQuery(text))
                override suspend fun embedDocument(content: String, header: String) = tilted(base.embedDocument(content, header))
            }
        }

        private fun tilted(v: FloatArray): FloatArray {
            val axis = FloatArray(v.size).also { it[0] = 1f }
            val mixed = FloatArray(v.size) { (v[it] + tilt * axis[it]).toFloat() }
            val norm = sqrt(mixed.sumOf { (it * it).toDouble() }).toFloat()
            return FloatArray(v.size) { mixed[it] / norm }
        }

        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig): TextGenerator = error("unused")

        override fun metadata(path: File): ModelMetadata = error("unused")

        override fun clearCompiledCache() = Unit

        override var benchmarkStatsEnabled: Boolean = false
    }

    private fun validator(factory: EngineFactory) = IdentityValidator(coordinator, factory, models, { texts }, logger)

    @Test
    fun sameVectors_pass_withBothModelsOnTheCpuOneAfterTheOther() = runTest {
        val factory = Factory(tilt = 0.0)

        val check = validator(factory).check()

        assertThat(check.texts).isEqualTo(2)
        assertThat(check.minCosine).isWithin(1e-6).of(1.0)
        assertThat(check.passed).isTrue()
        assertThat(factory.loads).containsExactly(ModelId.EG2_270M to ComputeBackend.CPU, ModelId.EG2_440M to ComputeBackend.CPU).inOrder()
        assertThat(coordinator.loaded.value).isEmpty()
    }

    @Test
    fun theConstitutionsThreshold_decides() = runTest {
        // A tiny tilt stays above 0.9999; a larger one falls below it.
        val close = validator(Factory(tilt = 0.003)).check()
        assertThat(close.minCosine).isAtLeast(0.99999)
        assertThat(close.passed).isTrue()

        val far = validator(Factory(tilt = 0.02)).check()
        assertThat(far.minCosine).isLessThan(0.9999)
        assertThat(far.passed).isFalse()
        assertThat(coordinator.loaded.value).isEmpty()
    }

    @Test
    fun theLogCarriesTheFigure_neverAText() = runTest {
        validator(Factory(tilt = 0.0)).check()

        val log = logger.lines.joinToString("\n")
        assertThat(log).contains("min cosine")
        texts.forEach { assertThat(log).doesNotContain(it.text) }
    }

    @Test
    fun aMissing440M_throws_andLeavesNoEngine() = runTest {
        models.states.value = models.states.value + (ModelId.EG2_440M to ModelFileState.Missing)

        assertThrows(IllegalStateException::class.java) { runBlocking { validator(Factory(0.0)).check() } }
        assertThat(coordinator.loaded.value).isEmpty()
    }

    @Test
    fun theBundledSample_hasTwentyQueriesAndDocuments() {
        val sample = IdentityValidator.parse(File("src/main/assets/embedding/identity_texts.jsonl").readText())
        assertThat(sample).hasSize(20)
        assertThat(sample.map { it.kind }.toSet()).containsExactly("query", "doc")
        assertThat(EmbeddingMath.DIMENSIONS).isEqualTo(256)
    }
}

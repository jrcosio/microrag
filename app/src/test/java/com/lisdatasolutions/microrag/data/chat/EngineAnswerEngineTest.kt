package com.lisdatasolutions.microrag.data.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.chat.AnswerEvent
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.CPU
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.GPU
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationStats
import com.lisdatasolutions.microrag.rag.runtime.ModelMetadata
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/** F11 T022 (research R5): Gemma with the backend prepared in F03, the desktop config, loaded on demand and never held. */
class EngineAnswerEngineTest {

    private val coordinator = EngineCoordinator(RecordingLogger())
    private val factory = FakeFactory()
    private val models = InMemoryModelFileRepository(verified = setOf(ModelId.GEMMA4_E2B)).apply {
        preparedBackends.value = mapOf(ModelId.GEMMA4_E2B to GPU)
    }
    private val engine = EngineAnswerEngine(coordinator, factory, models)

    private class FakeFactory : EngineFactory {
        val created = mutableListOf<Pair<ComputeBackend, GenerationConfig>>()
        var initialized = 0
        var generations = 0
        var cancelled = 0
        var tokens = listOf("El plazo ", "es de 30 días [1].")
        var failure: Exception? = null

        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig): TextGenerator {
            created += backend to config
            return object : TextGenerator {
                override val model = file.id
                override val backend = backend
                override suspend fun initialize() {
                    initialized++
                }

                override fun generate(prompt: String): Flow<GenerationEvent> = flow {
                    generations++
                    try {
                        failure?.let { throw it }
                        tokens.forEach {
                            delay(10)
                            emit(GenerationEvent.Token(it))
                        }
                        emit(GenerationEvent.Finished(GenerationStats(null, null, 120, 900, null, null, null, 8.8)))
                    } catch (e: CancellationException) {
                        cancelled++
                        throw e
                    }
                }

                override fun cancel() = Unit
                override fun close() = Unit
            }
        }

        override fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder = error("unused")
        override fun metadata(path: File): ModelMetadata = error("unused")
        override fun clearCompiledCache() = Unit
        override var benchmarkStatsEnabled: Boolean = false
    }

    @Test
    fun ready_whenVerifiedAndPrepared_withoutHashing() = runTest {
        assertThat(engine.ready()).isTrue()
        assertThat(models.refreshes).isEmpty()
        assertThat(models.verifyCalls).isEmpty()
    }

    @Test
    fun notReady_whenNotPreparedOrUnverified() = runTest {
        models.preparedBackends.value = emptyMap()
        assertThat(engine.ready()).isFalse()
        assertThat(engine.preparedBackend().first()).isNull()

        models.preparedBackends.value = mapOf(ModelId.GEMMA4_E2B to GPU)
        models.states.update { it + (ModelId.GEMMA4_E2B to ModelFileState.Unverified(File("gemma"))) }
        assertThat(engine.ready()).isFalse()
        assertThat(engine.preparedBackend().first()).isNull()
    }

    @Test
    fun anUnknownPath_isRefreshedOnce() = runTest {
        models.states.update { it + (ModelId.GEMMA4_E2B to ModelFileState.Missing) }
        engine.ready()
        assertThat(models.refreshes).hasSize(1)
    }

    @Test
    fun generatesWithThePreparedBackendAndTheDesktopConfig() = runTest {
        val events = engine.generate("prompt").toList()

        assertThat(factory.created).containsExactly(GPU to GenerationConfig(maxNumTokens = 4096, maxOutputTokens = 512, greedy = true))
        assertThat(factory.initialized).isEqualTo(1)
        assertThat(events).containsExactly(
            AnswerEvent.Token("El plazo "), AnswerEvent.Token("es de 30 días [1]."),
            AnswerEvent.Finished(ttftMs = 120.0, totalMs = 900.0, decodeTokensPerSecond = 8.8),
        ).inOrder()
        assertThat(coordinator.loaded.value).containsExactly(EngineKey(ModelId.GEMMA4_E2B, GPU))
    }

    @Test
    fun gemmaPreparedOnCpu_runsOnCpu() = runTest {
        models.preparedBackends.value = mapOf(ModelId.GEMMA4_E2B to CPU)
        assertThat(engine.preparedBackend().first()).isEqualTo(CPU)

        engine.generate("prompt").toList()

        assertThat(factory.created.single().first).isEqualTo(CPU)
    }

    @Test
    fun warmUp_loadsWithoutGenerating_andTheEngineIsReused() = runTest {
        engine.warmUp()
        engine.warmUp()
        assertThat(factory.generations).isEqualTo(0)
        assertThat(coordinator.loaded.value).containsExactly(EngineKey(ModelId.GEMMA4_E2B, GPU))

        engine.generate("prompt").toList()
        assertThat(factory.created).hasSize(1)
    }

    @Test
    fun cancellingTheCollector_stopsTheEngine_andKeepsItLoaded() = runTest {
        val first = engine.generate("prompt").take(1).toList()

        assertThat(first).containsExactly(AnswerEvent.Token("El plazo "))
        assertThat(factory.cancelled).isEqualTo(1)
        assertThat(coordinator.loaded.value).containsExactly(EngineKey(ModelId.GEMMA4_E2B, GPU))
    }

    @Test
    fun anEngineFailure_propagates() = runTest {
        factory.failure = IllegalStateException("gpu lost")
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { engine.generate("prompt").toList() } }
    }
}

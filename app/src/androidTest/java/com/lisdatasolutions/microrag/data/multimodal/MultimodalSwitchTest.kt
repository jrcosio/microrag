package com.lisdatasolutions.microrag.data.multimodal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.ingest.IngestScheduler
import com.lisdatasolutions.microrag.data.ingest.IngestWork
import com.lisdatasolutions.microrag.data.models.DefaultModelFileRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.NetworkKind
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.multimodal.DefaultModelRequirements
import com.lisdatasolutions.microrag.domain.multimodal.IdentityCheck
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.ValidateIdentityUseCase
import com.lisdatasolutions.microrag.domain.onboarding.DeviceResources
import com.lisdatasolutions.microrag.domain.onboarding.LocalModelWorkTracker
import com.lisdatasolutions.microrag.domain.onboarding.ModelDownloadController
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.PrepareModelsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.rag.runtime.CompatibilityResult
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F19 T026 (research R4, SC-002): the switch on a real index with the 270M's rag_meta and model files that are
 * only names. `rag_meta` records the 440M, the 270M's file goes, the searches give the same ids, and a process
 * killed between the transaction and the deletion leaves an index that opens, searches and finishes at start.
 */
@RunWith(AndroidJUnit4::class)
class MultimodalSwitchTest {

    private val context = TempRagIndex.context
    private val root = File(context.cacheDir, "multimodal-switch-${UUID.randomUUID()}").apply { mkdirs() }
    private val modelsDir = File(root, "models").apply { mkdirs() }
    private val logger = CapturingLogger()
    private val scopes = mutableListOf<CoroutineScope>()
    private val temps = mutableListOf<TempRagIndex>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        temps.forEach { it.close() }
        root.deleteRecursively()
    }

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }

    private fun temp(dbFile: File? = null) = (if (dbFile == null) TempRagIndex() else TempRagIndex(dbFile = dbFile)).also { temps += it }

    private fun file270M() = File(modelsDir, ModelCatalog.byId(ModelId.EG2_270M).fileName)

    /** Three documents with chunks; returns what a vector and a text search find. */
    private suspend fun fill(temp: TempRagIndex): Pair<List<Long>, List<Long>> {
        temp.index.open()
        repeat(3) { n ->
            val id = temp.index.addDocument(NewDocument(displayName = "Manual $n.txt", contentHash = "hash-$n"))
            temp.index.insertChunks(id, List(2) { chunk(n * 10 + it, content = "Revisión del motor $n, paso $it.") })
            temp.index.updateStatus(id, DocumentStatus.DONE, chunkCount = 2)
        }
        return searches(temp)
    }

    private suspend fun searches(temp: TempRagIndex): Pair<List<Long>, List<Long>> =
        temp.index.vectorSearch(unitVector(11), k = 5).map { it.id } to temp.index.textSearch("motor", k = 5)

    private fun controller(temp: TempRagIndex): Pair<DefaultMultimodalController, DataStoreMultimodalStore> {
        val scope = scope()
        val store = DataStoreMultimodalStore.create(root, scope, logger)
        val models = DefaultModelFileRepository(modelsDir = modelsDir, ioDispatcher = Dispatchers.IO, logger = logger)
        val downloads = StubDownloads()
        val onboarding = StubOnboarding()
        val tracker = LocalModelWorkTracker()
        val requirements = DefaultModelRequirements(temp.index, store)
        val app = ApplicationScope(scope)
        return DefaultMultimodalController(
            store = store,
            index = temp.index,
            models = models,
            downloads = downloads,
            onboarding = onboarding,
            startDownload = StartModelDownloadUseCase(models, downloads, StubResources(), onboarding, tracker, requirements),
            prepare = PrepareModelsUseCase(models, { _: ModelFile, _: File -> CompatibilityResult.Compatible(ComputeBackend.CPU) }, tracker, app, requirements),
            tracker = tracker,
            requirements = requirements,
            validateIdentity = ValidateIdentityUseCase { IdentityCheck(20, 1.0, true) },
            coordinator = EngineCoordinator(logger),
            runStore = InMemoryIngestRunStore(),
            scheduler = StubScheduler(),
            indexing = StubIndexing(),
            applicationScope = app,
            logger = logger,
        ) to store
    }

    private suspend fun DataStoreMultimodalStore.awaitOn() = withTimeout(10_000) { state.first { it.stage == MultimodalStage.ON } }

    @Test
    fun theSwitch_recordsThe440M_deletesThe270M_andKeepsTheSearches() = runBlocking<Unit> {
        val temp = temp()
        val before = fill(temp)
        file270M().writeText("270M")
        val (controller, store) = controller(temp)
        store.write { it.copy(stage = MultimodalStage.SWITCHING) }

        controller.start()
        val on = store.awaitOn()

        assertThat(on.textEmbedder).isEqualTo(ModelId.EG2_440M)
        assertThat(temp.index.meta()[RagMetaKeys.EMBEDDING_MODEL_SHA256]).isEqualTo(ModelCatalog.byId(ModelId.EG2_440M).sha256)
        assertThat(temp.index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
        assertThat(file270M().exists()).isFalse()
        assertThat(searches(temp)).isEqualTo(before)
    }

    @Test
    fun killedBetweenTheTransactionAndTheDeletion_theIndexOpensSearchesAndFinishes() = runBlocking<Unit> {
        val first = temp()
        val before = fill(first)
        file270M().writeText("270M")
        // Step 2 done (rag_meta), steps 3 and 4 not: the mirror still says 270M and its file is there.
        first.index.switchTextEmbedder(ModelId.EG2_440M)
        first.index.close()

        // A new process: the index is opened with the mirror's 270M and corrected by rag_meta.
        val reopened = temp(dbFile = first.dbFile)
        assertThat(reopened.index.textEmbedder.value).isEqualTo(ModelId.EG2_270M)
        assertThat(reopened.index.open()).isEqualTo(IndexCompatibility.Compatible)
        assertThat(reopened.index.textEmbedder.value).isEqualTo(ModelId.EG2_440M)
        assertThat(searches(reopened)).isEqualTo(before)

        val (controller, store) = controller(reopened)
        store.write { it.copy(stage = MultimodalStage.SWITCHING) }
        controller.start()
        val on = store.awaitOn()

        assertThat(on.textEmbedder).isEqualTo(ModelId.EG2_440M)
        assertThat(file270M().exists()).isFalse()
        assertThat(searches(reopened)).isEqualTo(before)
    }

    private class StubDownloads : ModelDownloadController {
        override val progress = MutableStateFlow(DownloadProgress())
        override fun schedule(pendingBytes: Long) = Unit
        override fun cancel() = Unit
        override suspend fun isScheduledOrRunning() = false
        override fun partialBytes(id: ModelId) = 0L
        override fun deleteOrphanPartials(required: List<ModelFile>) = Unit
    }

    private class StubOnboarding : OnboardingStore {
        override val stage = MutableStateFlow(OnboardingStage.COMPLETED)
        override val downloadRequested = MutableStateFlow<Set<ModelId>>(emptySet())
        override val downloadPaused = MutableStateFlow(false)
        override val downloadConfirmed = MutableStateFlow(true)
        override suspend fun setStage(stage: OnboardingStage) = Unit
        override suspend fun requestDownload(ids: Set<ModelId>) = Unit
        override suspend fun setPaused(paused: Boolean) = Unit
        override suspend fun clearRequest(id: ModelId) = Unit
    }

    private class StubResources : DeviceResources {
        override fun totalRamBytes() = 12_000_000_000L
        override fun freeBytes() = 100_000_000_000L
        override fun currentNetwork() = NetworkKind.WIFI
        override val hasInternet = MutableStateFlow(true)
        override fun prepareStorage() = true
    }

    private class StubScheduler : IngestScheduler {
        override val activeDocumentIds = MutableStateFlow<Set<Long>>(emptySet())
        override val progress = MutableStateFlow<IngestProgress?>(null)
        override val work = MutableStateFlow(IngestWork.NONE)
        override suspend fun enqueue(documentIds: List<Long>) = Unit
        override suspend fun replace(documentIds: List<Long>) = Unit
        override suspend fun cancel() = Unit
    }

    private class StubIndexing : IndexingRepository {
        override val state = MutableStateFlow<IndexingState>(IndexingState.Idle(emptyList()))
        override suspend fun start() = 0
        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override suspend fun retry(documentId: Long) = Unit
        override suspend fun add(documentId: Long) = Unit
        override suspend fun reconcile() = Unit
    }
}

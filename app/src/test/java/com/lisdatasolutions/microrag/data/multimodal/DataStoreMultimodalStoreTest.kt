package com.lisdatasolutions.microrag.data.multimodal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** F19 T018 (contract M1, data-model §3): the mode's copy, its defaults and its failures. */
class DataStoreMultimodalStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logger = RecordingLogger()

    /** Opens a store over the same file; the scope must be cancelled before reopening it. */
    private fun withStore(block: suspend (DataStoreMultimodalStore) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            runBlocking { block(DataStoreMultimodalStore.create(tmp.root, scope, logger)) }
        } finally {
            scope.cancel()
        }
    }

    private val file get() = File(tmp.root, "datastore/multimodal.preferences_pb")

    @Test
    fun withoutACopy_theModeIsOffWithBothAndThe270M() = withStore { store ->
        assertThat(store.state.first()).isEqualTo(StoredMultimodal(MultimodalStage.OFF, null, IndexMode.BOTH, ModelId.EG2_270M))
    }

    @Test
    fun aWrittenState_survivesReopeningTheStore() {
        withStore { store ->
            assertThat(store.write { it.copy(stage = MultimodalStage.DOWNLOADING, error = MultimodalError.NO_SPACE) }).isTrue()
            assertThat(store.write { it.copy(defaultMode = IndexMode.VISUAL, textEmbedder = ModelId.EG2_440M) }).isTrue()
        }

        withStore { store ->
            assertThat(store.state.first()).isEqualTo(
                StoredMultimodal(MultimodalStage.DOWNLOADING, MultimodalError.NO_SPACE, IndexMode.VISUAL, ModelId.EG2_440M),
            )
            // Clearing the error removes it.
            store.write { it.copy(error = null) }
            assertThat(store.state.first().error).isNull()
        }
    }

    @Test
    fun unknownValues_readAsTheirDefaults() {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            runBlocking {
                DataStoreMultimodalStore.dataStore(tmp.root, scope).edit {
                    it[stringPreferencesKey("stage")] = "HALF_ON"
                    it[stringPreferencesKey("default_mode")] = "SEPIA"
                    // Gemma is never a text embedder.
                    it[stringPreferencesKey("text_embedder")] = ModelId.GEMMA4_E2B.name
                    // F20 T031 (contract M1): an unknown detail reads as Balanced.
                    it[stringPreferencesKey("page_detail")] = "ULTRA"
                }
            }
        } finally {
            scope.cancel()
        }

        withStore { store -> assertThat(store.state.first()).isEqualTo(StoredMultimodal()) }
    }

    @Test
    fun aCorruptCopy_readsAsTheDefaults_andTheNextWriteWorks() {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0x7F, 0x00, 0x13, 0x37, 0x42))

        withStore { store ->
            assertThat(store.state.first()).isEqualTo(StoredMultimodal())
            assertThat(store.write { it.copy(stage = MultimodalStage.ON) }).isTrue()
        }
        withStore { store -> assertThat(store.state.first().stage).isEqualTo(MultimodalStage.ON) }
    }

    @Test
    fun aFailingDisk_neverThrows_andLogsOnlyTheKindOfFailure() = runBlocking {
        val store = DataStoreMultimodalStore(FailingDataStore(), logger)

        assertThat(store.state.first()).isEqualTo(StoredMultimodal())
        assertThat(store.write { it.copy(stage = MultimodalStage.ON) }).isFalse()

        assertThat(logger.lines.joinToString()).contains("IOException")
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("disk") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
    }

    /** F20 T031 (contract M1, D-083): Balanced by default, and the detail written survives reopening. */
    @Test
    fun thePageDetail_isBalancedByDefault_andSurvivesReopening() {
        withStore { store ->
            assertThat(store.state.first().pageDetail).isEqualTo(PageDetail.BALANCED)
            assertThat(store.write { it.copy(pageDetail = PageDetail.DETAILED) }).isTrue()
        }
        withStore { store -> assertThat(store.state.first().pageDetail).isEqualTo(PageDetail.DETAILED) }
    }
}

package com.lisdatasolutions.microrag.data.onboarding

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreOnboardingStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Opens a store over the same file; the scope must be cancelled before reopening it. */
    private fun withStore(block: suspend (DataStoreOnboardingStore) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            runBlocking { block(DataStoreOnboardingStore(DataStoreOnboardingStore.create(tmp.root, scope))) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaults() = withStore { store ->
        assertThat(store.stage.first()).isEqualTo(OnboardingStage.NOT_STARTED)
        assertThat(store.downloadRequested.first()).isEmpty()
        assertThat(store.downloadPaused.first()).isFalse()
        assertThat(store.downloadConfirmed.first()).isFalse()
    }

    @Test
    fun requestDownload_addsConfirmsAndClearsThePause() = withStore { store ->
        store.setPaused(true)
        store.requestDownload(setOf(ModelId.EG2_270M))
        store.requestDownload(setOf(ModelId.GEMMA4_E2B))

        assertThat(store.downloadRequested.first()).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B)
        assertThat(store.downloadConfirmed.first()).isTrue()
        assertThat(store.downloadPaused.first()).isFalse()

        store.clearRequest(ModelId.EG2_270M)
        assertThat(store.downloadRequested.first()).containsExactly(ModelId.GEMMA4_E2B)
    }

    @Test
    fun everythingSurvivesReopeningTheStore() {
        withStore { store ->
            store.setStage(OnboardingStage.STARTED)
            store.requestDownload(setOf(ModelId.GEMMA4_E2B))
            store.setPaused(true)
        }

        withStore { store ->
            assertThat(store.stage.first()).isEqualTo(OnboardingStage.STARTED)
            assertThat(store.downloadRequested.first()).containsExactly(ModelId.GEMMA4_E2B)
            assertThat(store.downloadPaused.first()).isTrue()
        }
        assertThat(tmp.root.resolve("datastore/onboarding.preferences_pb").isFile).isTrue()
    }

    @Test
    fun stageMovesForwardToCompleted() = withStore { store ->
        store.setStage(OnboardingStage.STARTED)
        store.setStage(OnboardingStage.COMPLETED)

        assertThat(store.stage.first()).isEqualTo(OnboardingStage.COMPLETED)
    }
}

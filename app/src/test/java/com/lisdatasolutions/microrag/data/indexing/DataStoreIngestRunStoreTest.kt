package com.lisdatasolutions.microrag.data.indexing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** F07 T007: the indexing run in DataStore (data-model §2). */
class DataStoreIngestRunStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Opens a store over the same file; the scope must be cancelled before reopening it. */
    private fun withStore(block: suspend (DataStoreIngestRunStore) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            runBlocking { block(DataStoreIngestRunStore(DataStoreIngestRunStore.create(tmp.root, scope))) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startsEmptyAndRunning() = withStore { store ->
        val run = store.run.first()
        assertThat(run.documentIds).isEmpty()
        assertThat(run.paused).isFalse()
        assertThat(store.gpuCheck()).isNull()
    }

    @Test
    fun addsInOrderWithoutDuplicatesAndRemoves() = withStore { store ->
        store.edit { it.adding(listOf(3L, 1L)) }
        store.edit { it.adding(listOf(1L, 7L)) }
        assertThat(store.run.first().documentIds).containsExactly(3L, 1L, 7L).inOrder()

        store.edit { it.without(listOf(1L)) }
        assertThat(store.run.first().documentIds).containsExactly(3L, 7L).inOrder()
    }

    @Test
    fun dropsFinishedAndAddsInASingleEdit() = withStore { store ->
        store.edit { it.adding(listOf(1L, 2L, 3L)) }

        val result = store.edit { it.without(listOf(1L, 2L)).adding(listOf(4L)).resumed() }

        assertThat(result.documentIds).containsExactly(3L, 4L).inOrder()
        assertThat(store.run.first()).isEqualTo(result)
    }

    @Test
    fun pausesWithACauseAndResumes() = withStore { store ->
        store.edit { it.adding(listOf(1L)).pausedBy(RunPauseCause.NO_SPACE) }
        assertThat(store.run.first().pauseCause).isEqualTo(RunPauseCause.NO_SPACE)
        assertThat(store.run.first().paused).isTrue()

        store.edit { it.resumed() }
        assertThat(store.run.first().paused).isFalse()
        assertThat(store.run.first().documentIds).containsExactly(1L)
    }

    @Test
    fun emptiesTheRun() = withStore { store ->
        store.edit { it.adding(listOf(1L, 2L)) }
        store.edit { it.without(it.documentIds) }
        assertThat(store.run.first()).isEqualTo(IngestRun())
    }

    @Test
    fun persistsAcrossInstances() {
        withStore { store ->
            store.edit { it.adding(listOf(5L, 6L)).pausedBy(RunPauseCause.USER) }
            store.setGpuCheck("abc|42|gpu")
        }
        withStore { store ->
            assertThat(store.run.first()).isEqualTo(IngestRun(listOf(5L, 6L), RunPauseCause.USER))
            assertThat(store.gpuCheck()).isEqualTo("abc|42|gpu")
        }
    }
}

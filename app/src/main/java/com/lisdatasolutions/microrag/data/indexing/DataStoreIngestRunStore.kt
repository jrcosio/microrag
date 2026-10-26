package com.lisdatasolutions.microrag.data.indexing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** [IngestRunStore] over a Preferences DataStore in no-backup storage (F07 data-model §2). */
class DataStoreIngestRunStore(private val dataStore: DataStore<Preferences>) : IngestRunStore {

    override val run: Flow<IngestRun> = dataStore.data.map(::read).distinctUntilChanged()

    override suspend fun edit(transform: (IngestRun) -> IngestRun): IngestRun {
        var result = IngestRun()
        dataStore.edit { prefs ->
            result = transform(read(prefs))
            write(prefs, result)
        }
        return result
    }

    override suspend fun gpuCheck(): String? = dataStore.data.first()[GPU_CHECK]

    override suspend fun setGpuCheck(value: String) {
        dataStore.edit { it[GPU_CHECK] = value }
    }

    private fun read(prefs: Preferences) = IngestRun(
        documentIds = prefs[DOCUMENT_IDS].orEmpty().split(',').mapNotNull { it.toLongOrNull() },
        pauseCause = RunPauseCause.fromCode(prefs[PAUSE_CAUSE]),
    )

    private fun write(prefs: MutablePreferences, run: IngestRun) {
        if (run.documentIds.isEmpty()) prefs.remove(DOCUMENT_IDS) else prefs[DOCUMENT_IDS] = run.documentIds.joinToString(",")
        val cause = run.pauseCause
        if (cause == null) prefs.remove(PAUSE_CAUSE) else prefs[PAUSE_CAUSE] = cause.code
    }

    companion object {
        private val DOCUMENT_IDS = stringPreferencesKey("document_ids")
        private val PAUSE_CAUSE = stringPreferencesKey("pause_cause")
        private val GPU_CHECK = stringPreferencesKey("gpu_check")

        /** `noBackupFilesDir/datastore/ingest_run.preferences_pb`: private and never backed up. */
        fun create(noBackupDir: File, scope: CoroutineScope): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { File(noBackupDir, "datastore/ingest_run.preferences_pb") }
    }
}

package com.lisdatasolutions.microrag.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [OnboardingStore] over a Preferences DataStore in no-backup storage (F03 research R6). */
class DataStoreOnboardingStore(private val dataStore: DataStore<Preferences>) : OnboardingStore {

    override val stage: Flow<OnboardingStage> = dataStore.data.map { prefs ->
        prefs[STAGE]?.let { name -> OnboardingStage.entries.firstOrNull { it.name == name } } ?: OnboardingStage.NOT_STARTED
    }.distinctUntilChanged()

    override val downloadRequested: Flow<Set<ModelId>> = dataStore.data.map { prefs ->
        prefs[REQUESTED].orEmpty().mapNotNull { name -> ModelId.entries.firstOrNull { it.name == name } }.toSet()
    }.distinctUntilChanged()

    override val downloadPaused: Flow<Boolean> = dataStore.data.map { it[PAUSED] ?: false }.distinctUntilChanged()

    override val downloadConfirmed: Flow<Boolean> = dataStore.data.map { it[CONFIRMED] ?: false }.distinctUntilChanged()

    override suspend fun setStage(stage: OnboardingStage) {
        dataStore.edit { it[STAGE] = stage.name }
    }

    override suspend fun requestDownload(ids: Set<ModelId>) {
        dataStore.edit { prefs ->
            prefs[REQUESTED] = prefs[REQUESTED].orEmpty() + ids.map { it.name }
            prefs[CONFIRMED] = true
            prefs[PAUSED] = false
        }
    }

    override suspend fun setPaused(paused: Boolean) {
        dataStore.edit { it[PAUSED] = paused }
    }

    override suspend fun clearRequest(id: ModelId) {
        dataStore.edit { prefs -> prefs[REQUESTED] = prefs[REQUESTED].orEmpty() - id.name }
    }

    companion object {
        private val STAGE = stringPreferencesKey("onboarding_stage")
        private val REQUESTED = stringSetPreferencesKey("download_requested")
        private val PAUSED = booleanPreferencesKey("download_paused")
        private val CONFIRMED = booleanPreferencesKey("download_confirmed")

        /** `noBackupFilesDir/datastore/onboarding.preferences_pb`: private and never backed up. */
        fun create(noBackupDir: File, scope: CoroutineScope): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { File(noBackupDir, "datastore/onboarding.preferences_pb") }
    }
}

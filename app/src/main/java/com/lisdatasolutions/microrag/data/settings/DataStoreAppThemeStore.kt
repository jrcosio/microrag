package com.lisdatasolutions.microrag.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.settings.AppThemeStore
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import com.lisdatasolutions.microrag.domain.settings.ThemeSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

/**
 * [AppThemeStore] over a Preferences DataStore in no-backup storage (F16 research R2). A corrupt file is
 * replaced by an empty one and the session reads as failed until a save works (theme-api P1). Only the
 * kind of a failure is logged, never the theme (P7).
 */
class DataStoreAppThemeStore(
    private val dataStore: DataStore<Preferences>,
    private val logger: AppLogger,
    private val corruption: Corruption = Corruption(),
) : AppThemeStore {

    /** Set by the corruption handler of [create]; cleared by the first successful write. */
    class Corruption {
        @Volatile
        var found = false
    }

    override suspend fun read(): ThemeSettings = try {
        val stored = dataStore.data.first()[THEME]
        ThemeSettings(ThemePreference.fromName(stored), readFailed = corruption.found)
    } catch (e: IOException) {
        logger.w(TAG, e) { "theme copy not read: ${e.javaClass.simpleName}" }
        ThemeSettings(ThemePreference.SYSTEM, readFailed = true)
    }

    override suspend fun write(preference: ThemePreference): Boolean = try {
        dataStore.edit { it[THEME] = preference.name }
        corruption.found = false
        true
    } catch (e: IOException) {
        logger.w(TAG, e) { "theme copy not stored: ${e.javaClass.simpleName}" }
        false
    }

    companion object {
        private const val TAG = "MicroRAG"
        private val THEME = stringPreferencesKey("theme")

        /** The store over `noBackupFilesDir/datastore/appearance.preferences_pb`: private and never backed up. */
        fun create(noBackupDir: File, scope: CoroutineScope, logger: AppLogger): DataStoreAppThemeStore {
            val corruption = Corruption()
            val dataStore = dataStore(noBackupDir, scope) { e ->
                logger.w(TAG, e) { "theme copy corrupt, replaced: ${e.javaClass.simpleName}" }
                corruption.found = true
            }
            return DataStoreAppThemeStore(dataStore, logger, corruption)
        }

        internal fun dataStore(noBackupDir: File, scope: CoroutineScope, onCorrupt: (IOException) -> Unit = {}): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { e ->
                    onCorrupt(e)
                    emptyPreferences()
                },
                scope = scope,
            ) { File(noBackupDir, "datastore/appearance.preferences_pb") }
    }
}

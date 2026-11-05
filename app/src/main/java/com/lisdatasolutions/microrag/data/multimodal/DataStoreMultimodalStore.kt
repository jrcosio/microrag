package com.lisdatasolutions.microrag.data.multimodal

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStore
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [MultimodalStore] over a Preferences DataStore in no-backup storage (F19 data-model §3, contract M1). A corrupt
 * file is replaced by an empty one, so it reads as the defaults; unknown values read as their default too. Only
 * the kind of a failure is logged.
 */
class DataStoreMultimodalStore(
    private val dataStore: DataStore<Preferences>,
    private val logger: AppLogger,
) : MultimodalStore {

    override val state: Flow<StoredMultimodal> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            logger.w(TAG, e) { "multimodal copy not read: ${e.javaClass.simpleName}" }
            emit(emptyPreferences())
        }
        .map(::decode)
        .distinctUntilChanged()

    override suspend fun write(transform: (StoredMultimodal) -> StoredMultimodal): Boolean = try {
        dataStore.edit { prefs -> encode(transform(decode(prefs)), prefs) }
        true
    } catch (e: IOException) {
        logger.w(TAG, e) { "multimodal copy not stored: ${e.javaClass.simpleName}" }
        false
    }

    private fun decode(prefs: Preferences): StoredMultimodal {
        val defaults = StoredMultimodal()
        return StoredMultimodal(
            stage = MultimodalStage.entries.firstOrNull { it.name == prefs[STAGE] } ?: defaults.stage,
            error = MultimodalError.entries.firstOrNull { it.name == prefs[ERROR] },
            defaultMode = IndexMode.entries.firstOrNull { it.name == prefs[DEFAULT_MODE] } ?: defaults.defaultMode,
            textEmbedder = ModelId.entries.firstOrNull { it.name == prefs[TEXT_EMBEDDER] && it in RagIndexConfig.TEXT_EMBEDDERS }
                ?: defaults.textEmbedder,
        )
    }

    private fun encode(value: StoredMultimodal, prefs: MutablePreferences) {
        prefs[STAGE] = value.stage.name
        value.error?.let { prefs[ERROR] = it.name } ?: prefs.remove(ERROR)
        prefs[DEFAULT_MODE] = value.defaultMode.name
        prefs[TEXT_EMBEDDER] = value.textEmbedder.name
    }

    companion object {
        private const val TAG = "MicroRAG"
        private val STAGE = stringPreferencesKey("stage")
        private val ERROR = stringPreferencesKey("error")
        private val DEFAULT_MODE = stringPreferencesKey("default_mode")
        private val TEXT_EMBEDDER = stringPreferencesKey("text_embedder")

        /** The store over `noBackupFilesDir/datastore/multimodal.preferences_pb`: private and never backed up. */
        fun create(noBackupDir: File, scope: CoroutineScope, logger: AppLogger): DataStoreMultimodalStore =
            DataStoreMultimodalStore(dataStore(noBackupDir, scope) { e -> logger.w(TAG, e) { "multimodal copy corrupt, replaced: ${e.javaClass.simpleName}" } }, logger)

        internal fun dataStore(noBackupDir: File, scope: CoroutineScope, onCorrupt: (IOException) -> Unit = {}): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { e ->
                    onCorrupt(e)
                    emptyPreferences()
                },
                scope = scope,
            ) { File(noBackupDir, "datastore/multimodal.preferences_pb") }
    }
}

package com.lisdatasolutions.microrag.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import com.lisdatasolutions.microrag.domain.settings.ThemeSettings
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** F16 T007 (theme-api P1, P7; research R2): the copy of the theme, its defaults and its failures. */
class DataStoreAppThemeStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logger = RecordingLogger()

    /** Opens a store over the same file; the scope must be cancelled before reopening it. */
    private fun withStore(block: suspend (DataStoreAppThemeStore) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            runBlocking { block(DataStoreAppThemeStore.create(tmp.root, scope, logger)) }
        } finally {
            scope.cancel()
        }
    }

    private val file get() = File(tmp.root, "datastore/appearance.preferences_pb")

    @Test
    fun withoutACopy_itIsSystem() = withStore { store ->
        assertThat(store.read()).isEqualTo(ThemeSettings(ThemePreference.SYSTEM))
    }

    @Test
    fun aWrittenTheme_survivesReopeningTheStore() {
        withStore { store -> assertThat(store.write(ThemePreference.DARK)).isTrue() }

        withStore { store -> assertThat(store.read()).isEqualTo(ThemeSettings(ThemePreference.DARK)) }
    }

    @Test
    fun anUnknownName_isSystem() {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            runBlocking {
                DataStoreAppThemeStore.dataStore(tmp.root, scope).edit { it[stringPreferencesKey("theme")] = "SEPIA" }
            }
        } finally {
            scope.cancel()
        }

        withStore { store -> assertThat(store.read()).isEqualTo(ThemeSettings(ThemePreference.SYSTEM)) }
    }

    @Test
    fun aCorruptCopy_readsAsFailed_andTheNextSaveFixesIt() {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0x7F, 0x00, 0x13, 0x37, 0x42))

        withStore { store ->
            assertThat(store.read()).isEqualTo(ThemeSettings(ThemePreference.SYSTEM, readFailed = true))
            assertThat(store.write(ThemePreference.LIGHT)).isTrue()
            assertThat(store.read()).isEqualTo(ThemeSettings(ThemePreference.LIGHT))
        }
        withStore { store -> assertThat(store.read()).isEqualTo(ThemeSettings(ThemePreference.LIGHT)) }
    }

    @Test
    fun aFailingDisk_neverThrows_andLogsOnlyTheKindOfFailure() = runBlocking {
        val store = DataStoreAppThemeStore(FailingDataStore(), logger)

        assertThat(store.read()).isEqualTo(ThemeSettings(ThemePreference.SYSTEM, readFailed = true))
        assertThat(store.write(ThemePreference.DARK)).isFalse()

        assertThat(logger.lines).isNotEmpty()
        assertThat(logger.lines.joinToString()).contains("IOException")
        assertThat(logger.lines.joinToString().uppercase()).doesNotContain("DARK")
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("disk") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
    }
}

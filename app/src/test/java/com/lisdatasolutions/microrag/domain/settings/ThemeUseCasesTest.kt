package com.lisdatasolutions.microrag.domain.settings

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.testing.FakeAppThemeController
import com.lisdatasolutions.microrag.testing.FakeAppThemeStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F16 T006 (theme-api P1, P3, P5): what Tema reads, the order of a save, and the start-up reconciliation. */
class ThemeUseCasesTest {
    private val store = FakeAppThemeStore()
    private val controller = FakeAppThemeController()
    private val notices = SettingsNotices()
    private val save = SaveThemeSettingsUseCase(store, controller, notices)
    private val reconcile = ReconcileThemeUseCase(store, controller)

    @Test
    fun observing_givesTheStoredCopy_andAnUnreadableOne() = runTest {
        store.preference = ThemePreference.DARK
        assertThat(ObserveThemeSettingsUseCase(store)().first()).isEqualTo(ThemeSettings(ThemePreference.DARK))

        store.failRead = true
        assertThat(ObserveThemeSettingsUseCase(store)().first()).isEqualTo(ThemeSettings(ThemePreference.SYSTEM, readFailed = true))
    }

    @Test
    fun theSameTheme_isUnchanged_andNothingIsWrittenOrApplied() = runTest {
        assertThat(save(ThemePreference.SYSTEM)).isEqualTo(ThemeSaveResult.Unchanged)

        assertThat(store.written).isEmpty()
        assertThat(controller.applied).isEmpty()
        assertThat(notices.savedPending.value).isFalse()
    }

    @Test
    fun afterAFailedRead_evenSystemIsSaved_soASaveFixesIt() = runTest {
        store.failRead = true

        assertThat(save(ThemePreference.SYSTEM)).isEqualTo(ThemeSaveResult.Saved)

        assertThat(store.written).containsExactly(ThemePreference.SYSTEM)
        assertThat(controller.applied).containsExactly(ThemePreference.SYSTEM)
    }

    @Test
    fun aFailedWrite_appliesNothing() = runTest {
        store.failWrite = true

        assertThat(save(ThemePreference.DARK)).isEqualTo(ThemeSaveResult.Failed)

        assertThat(controller.applied).isEmpty()
        assertThat(store.preference).isEqualTo(ThemePreference.SYSTEM)
        assertThat(notices.savedPending.value).isFalse()
    }

    @Test
    fun aRefusedApply_restoresThePreviousCopy() = runTest {
        store.preference = ThemePreference.LIGHT
        controller.failApply = true

        assertThat(save(ThemePreference.DARK)).isEqualTo(ThemeSaveResult.Failed)

        assertThat(store.written).containsExactly(ThemePreference.DARK, ThemePreference.LIGHT).inOrder()
        assertThat(store.preference).isEqualTo(ThemePreference.LIGHT)
        assertThat(notices.savedPending.value).isFalse()
    }

    @Test
    fun aRefusedApply_afterAFailedRead_restoresSystem() = runTest {
        store.failRead = true
        controller.failApply = true

        assertThat(save(ThemePreference.DARK)).isEqualTo(ThemeSaveResult.Failed)

        assertThat(store.written).containsExactly(ThemePreference.DARK, ThemePreference.SYSTEM).inOrder()
        assertThat(store.preference).isEqualTo(ThemePreference.SYSTEM)
    }

    @Test
    fun aGoodSave_writesFirst_thenApplies_andLeavesTheNoticeWithItsMargin() = runTest {
        assertThat(save(ThemePreference.DARK)).isEqualTo(ThemeSaveResult.Saved)

        assertThat(store.preference).isEqualTo(ThemePreference.DARK)
        assertThat(controller.applied).containsExactly(ThemePreference.DARK)
        assertThat(notices.savedPending.value).isTrue()
        assertThat(notices.settlePending.value).isTrue()
    }

    @Test
    fun reconciling_appliesTheStoredCopy() = runTest {
        store.preference = ThemePreference.LIGHT

        reconcile()

        assertThat(controller.applied).containsExactly(ThemePreference.LIGHT)
        assertThat(store.written).isEmpty()
    }

    @Test
    fun reconciling_afterAFailedRead_appliesSystem() = runTest {
        store.preference = ThemePreference.DARK
        store.failRead = true

        reconcile()

        assertThat(controller.applied).containsExactly(ThemePreference.SYSTEM)
    }
}

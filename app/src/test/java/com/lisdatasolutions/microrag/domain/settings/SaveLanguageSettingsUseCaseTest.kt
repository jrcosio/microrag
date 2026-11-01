package com.lisdatasolutions.microrag.domain.settings

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.testing.FakeAppLanguageController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F12 T045 (P6, D-061: only the app language is saved). */
class SaveLanguageSettingsUseCaseTest {
    private val controller = FakeAppLanguageController()
    private val notices = SettingsNotices()
    private val save = SaveLanguageSettingsUseCase(controller, notices)

    @Test
    fun withoutChanges_nothingIsWritten() = runTest {
        assertThat(save(AppLanguage.SPANISH)).isEqualTo(SaveResult.Unchanged)

        assertThat(controller.applied).isEmpty()
        assertThat(notices.savedPending.value).isFalse()
    }

    @Test
    fun aFailedApply_changesNothing() = runTest {
        controller.failApply = true

        assertThat(save(AppLanguage.ENGLISH)).isEqualTo(SaveResult.Failed)

        assertThat(controller.current()).isEqualTo(AppLanguage.SPANISH)
        assertThat(notices.savedPending.value).isFalse()
    }

    @Test
    fun aNewLanguage_isApplied_andLeavesTheNotice() = runTest {
        assertThat(save(AppLanguage.ENGLISH)).isEqualTo(SaveResult.Saved(appLanguageChanged = true))

        assertThat(controller.applied).containsExactly(AppLanguage.ENGLISH)
        assertThat(controller.current()).isEqualTo(AppLanguage.ENGLISH)
        assertThat(notices.savedPending.value).isTrue()
    }

    @Test
    fun theScreenKnowsWhetherTheSystemRecreatesIt() {
        assertThat(save.recreatesItself).isTrue()
        controller.recreatesItself = false
        assertThat(save.recreatesItself).isFalse()
    }

    @Test
    fun observing_givesTheAppLanguage_andAnUnreadableChoice() = runTest {
        controller.language = AppLanguage.ENGLISH
        controller.readFailed = true

        assertThat(ObserveLanguageSettingsUseCase(controller)().first()).isEqualTo(LanguageSettings(AppLanguage.ENGLISH, readFailed = true))
    }
}

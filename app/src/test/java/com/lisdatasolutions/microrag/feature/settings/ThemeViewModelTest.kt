package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.settings.ObserveThemeSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveThemeSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SettingsNotices
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import com.lisdatasolutions.microrag.domain.settings.ThemeSettings
import com.lisdatasolutions.microrag.testing.FakeAppThemeController
import com.lisdatasolutions.microrag.testing.FakeAppThemeStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F16 T016 and T027: Tema (theme-ui U1–U4, A3–A5), shaped as Idiomas. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeAppThemeStore()
    private val controller = FakeAppThemeController()
    private val notices = SettingsNotices()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = ThemeViewModel(
        observe = ObserveThemeSettingsUseCase(store),
        save = SaveThemeSettingsUseCase(store, controller, notices),
        savedState = saved,
    )

    // --- US1 ------------------------------------------------------------------------------------------

    @Test
    fun opensWithTheSavedTheme() = runTest(dispatcher) {
        store.preference = ThemePreference.DARK
        val viewModel = viewModel()

        assertThat(viewModel.state.value.saved).isNull()
        advanceUntilIdle()

        assertThat(viewModel.state.value.saved).isEqualTo(ThemeSettings(ThemePreference.DARK))
        assertThat(viewModel.state.value.selected).isEqualTo(ThemePreference.DARK)
    }

    @Test
    fun marking_onlyChangesTheSelection_whichSurvivesInTheSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            advanceUntilIdle()
            onSelect(ThemePreference.LIGHT)
        }

        val restored = viewModel(saved)
        advanceUntilIdle()

        assertThat(restored.state.value.selected).isEqualTo(ThemePreference.LIGHT)
        assertThat(store.written).isEmpty()
        assertThat(controller.applied).isEmpty()
    }

    @Test
    fun anUntouchedSelection_followsANewRead_butAMarkedOneIsKept() = runTest(dispatcher) {
        val untouched = SavedStateHandle()
        viewModel(untouched)
        advanceUntilIdle()
        store.preference = ThemePreference.DARK
        viewModel(untouched).also { it.onStart() }.let { recreated ->
            advanceUntilIdle()
            assertThat(recreated.state.value.selected).isEqualTo(ThemePreference.DARK)
        }

        val touched = SavedStateHandle()
        viewModel(touched).apply {
            advanceUntilIdle()
            onSelect(ThemePreference.LIGHT)
        }
        store.preference = ThemePreference.SYSTEM
        val recreated = viewModel(touched).also { it.onStart() }
        advanceUntilIdle()
        assertThat(recreated.state.value.selected).isEqualTo(ThemePreference.LIGHT)
    }

    @Test
    fun savingShowsSaving_aSecondTapDoesNothing_andThenCloses() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>().also { store.gate = it }
        val viewModel = viewModel()
        advanceUntilIdle()
        val event = async { viewModel.events.first() }

        viewModel.onSelect(ThemePreference.DARK)
        viewModel.onSave()
        runCurrent()
        assertThat(viewModel.state.value.saving).isTrue()
        viewModel.onSave()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(store.written).containsExactly(ThemePreference.DARK)
        assertThat(controller.applied).containsExactly(ThemePreference.DARK)
        assertThat(viewModel.state.value.saving).isFalse()
        assertThat(viewModel.state.value.saved).isEqualTo(ThemeSettings(ThemePreference.DARK))
        assertThat(event.await()).isEqualTo(ThemeEvent.Close)
        assertThat(notices.savedPending.value).isTrue()
    }

    @Test
    fun withoutChanges_itClosesWithoutWriting() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val event = async { viewModel.events.first() }

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(event.await()).isEqualTo(ThemeEvent.Close)
        assertThat(store.written).isEmpty()
        assertThat(controller.applied).isEmpty()
        assertThat(notices.savedPending.value).isFalse()
    }

    // --- US4 ------------------------------------------------------------------------------------------

    @Test
    fun aFailedSave_staysOnTheScreen_keepsTheSelection_andSaysSo() = runTest(dispatcher) {
        controller.failApply = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSelect(ThemePreference.DARK)
        viewModel.onSave()
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertThat(saveFailed).isTrue()
            assertThat(saving).isFalse()
            assertThat(selected).isEqualTo(ThemePreference.DARK)
            assertThat(saved).isEqualTo(ThemeSettings(ThemePreference.SYSTEM))
        }
        assertThat(store.preference).isEqualTo(ThemePreference.SYSTEM)
        viewModel.onSaveFailureShown()
        advanceUntilIdle()
        assertThat(viewModel.state.value.saveFailed).isFalse()
    }

    @Test
    fun afterAFailedSave_tryingAgainWorks() = runTest(dispatcher) {
        store.failWrite = true
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSelect(ThemePreference.LIGHT)
        viewModel.onSave()
        advanceUntilIdle()
        assertThat(viewModel.state.value.saveFailed).isTrue()
        assertThat(controller.applied).isEmpty()

        store.failWrite = false
        val event = async { viewModel.events.first() }
        viewModel.onSave()
        advanceUntilIdle()

        assertThat(event.await()).isEqualTo(ThemeEvent.Close)
        assertThat(controller.applied).containsExactly(ThemePreference.LIGHT)
    }

    @Test
    fun anUnreadableCopy_opensWithSystem_andSaysSo() = runTest(dispatcher) {
        store.preference = ThemePreference.DARK
        store.failRead = true
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.saved).isEqualTo(ThemeSettings(ThemePreference.SYSTEM, readFailed = true))
        assertThat(viewModel.state.value.selected).isEqualTo(ThemePreference.SYSTEM)
    }
}

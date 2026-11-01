package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.LanguageSettings
import com.lisdatasolutions.microrag.domain.settings.ObserveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SettingsNotices
import com.lisdatasolutions.microrag.testing.FakeAppLanguageController
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

/** F12 T047: Idiomas (S7–S9, R4, P6, P19), with the app language only (D-061). */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguagesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val controller = FakeAppLanguageController()
    private val notices = SettingsNotices()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = LanguagesViewModel(
        observe = ObserveLanguageSettingsUseCase(controller),
        save = SaveLanguageSettingsUseCase(controller, notices),
        savedState = saved,
    )

    @Test
    fun opensWithTheStoredLanguage() = runTest(dispatcher) {
        controller.language = AppLanguage.ENGLISH
        val viewModel = viewModel()

        assertThat(viewModel.state.value.saved).isNull()
        advanceUntilIdle()

        assertThat(viewModel.state.value.saved).isEqualTo(LanguageSettings(AppLanguage.ENGLISH))
        assertThat(viewModel.state.value.selectedApp).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun theSelectionSurvivesInTheSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            advanceUntilIdle()
            onSelectApp(AppLanguage.ENGLISH)
        }

        val restored = viewModel(saved)
        advanceUntilIdle()

        assertThat(restored.state.value.selectedApp).isEqualTo(AppLanguage.ENGLISH)
        assertThat(controller.applied).isEmpty()
    }

    @Test
    fun savingShowsSaving_aSecondTapDoesNothing_andThenCloses() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>().also { controller.gate = it }
        val viewModel = viewModel()
        advanceUntilIdle()
        val event = async { viewModel.events.first() }

        viewModel.onSelectApp(AppLanguage.ENGLISH)
        viewModel.onSave()
        runCurrent()
        assertThat(viewModel.state.value.saving).isTrue()
        viewModel.onSave()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(controller.applied).containsExactly(AppLanguage.ENGLISH)
        assertThat(viewModel.state.value.saving).isFalse()
        assertThat(event.await()).isEqualTo(LanguagesEvent.Close(recreate = false))
        assertThat(notices.savedPending.value).isTrue()
    }

    @Test
    fun onAndroid12_theScreenCloses_andRecreatesItself() = runTest(dispatcher) {
        controller.recreatesItself = false
        val viewModel = viewModel()
        advanceUntilIdle()
        val event = async { viewModel.events.first() }

        viewModel.onSelectApp(AppLanguage.ENGLISH)
        viewModel.onSave()
        advanceUntilIdle()

        assertThat(event.await()).isEqualTo(LanguagesEvent.Close(recreate = true))
    }

    @Test
    fun withoutChanges_itClosesWithoutWriting() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val event = async { viewModel.events.first() }

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(event.await()).isEqualTo(LanguagesEvent.Close(recreate = false))
        assertThat(controller.applied).isEmpty()
        assertThat(notices.savedPending.value).isFalse()
    }

    @Test
    fun aFailedSave_staysOnTheScreen_keepsTheSelection_andSaysSo() = runTest(dispatcher) {
        controller.failApply = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSelectApp(AppLanguage.ENGLISH)
        viewModel.onSave()
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertThat(saveFailed).isTrue()
            assertThat(saving).isFalse()
            assertThat(selectedApp).isEqualTo(AppLanguage.ENGLISH)
            assertThat(saved).isEqualTo(LanguageSettings(AppLanguage.SPANISH))
        }
        viewModel.onSaveFailureShown()
        advanceUntilIdle()
        assertThat(viewModel.state.value.saveFailed).isFalse()
    }

    @Test
    fun p19_anUntouchedSelection_followsALanguageSetByTheSystem() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        controller.language = AppLanguage.ENGLISH
        val recreated = viewModel(saved)
        recreated.onStart()
        advanceUntilIdle()

        assertThat(recreated.state.value.selectedApp).isEqualTo(AppLanguage.ENGLISH)
        assertThat(recreated.state.value.saved).isEqualTo(LanguageSettings(AppLanguage.ENGLISH))
    }

    @Test
    fun p19_aSelectionTheUserMade_isKept() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            advanceUntilIdle()
            onSelectApp(AppLanguage.ENGLISH)
            onSelectApp(AppLanguage.SPANISH)
        }

        controller.language = AppLanguage.ENGLISH
        val recreated = viewModel(saved)
        recreated.onStart()
        advanceUntilIdle()

        assertThat(recreated.state.value.selectedApp).isEqualTo(AppLanguage.SPANISH)
    }
}

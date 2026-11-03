package com.lisdatasolutions.microrag.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEyebrow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataHeroCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataInfoCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataRadioRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.settings.AppLanguage

/** What every control of Idiomas asks for. */
data class LanguagesActions(
    val onBack: () -> Unit = {},
    val onSelectApp: (AppLanguage) -> Unit = {},
    val onSave: () -> Unit = {},
    val onSaveFailureShown: () -> Unit = {},
)

/** P10 (PNG 10 with the approved proposals S7–S12, contracts/settings-ui.md §2). */
@Composable
fun LanguagesScreen(
    state: LanguagesUiState,
    actions: LanguagesActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LisdataTopBar(title = stringResource(R.string.languages_title), onBack = actions.onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    LisdataEyebrow(text = stringResource(R.string.languages_eyebrow))
                    Text(
                        text = stringResource(R.string.languages_headline),
                        style = LisdataTheme.typography.material.headlineLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                LisdataHeroCard(
                    title = stringResource(R.string.languages_hero_title),
                    titleAccent = stringResource(R.string.languages_hero_accent),
                    decoration = false,
                    leadingIcon = LisdataIcons.Globe,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    LisdataSectionHeader(text = stringResource(R.string.languages_section_app))
                    LisdataCardGroup(modifier = Modifier.selectableGroup()) {
                        LanguageOption(AppLanguage.SPANISH, state.selectedApp, actions.onSelectApp)
                        LisdataDivider()
                        LanguageOption(AppLanguage.ENGLISH, state.selectedApp, actions.onSelectApp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    LisdataSectionHeader(text = stringResource(R.string.languages_section_answers))
                    // S12 (D-061): the chat always answers in Spanish; one option, selected, that saves nothing.
                    LisdataCardGroup(modifier = Modifier.selectableGroup()) {
                        LisdataRadioRow(title = stringResource(R.string.languages_answer_spanish), selected = true, onSelect = {})
                    }
                }
                // S8: the stored choice could not be read; Spanish is in use until a save works.
                if (state.saved?.readFailed == true) {
                    LisdataInfoCard(
                        icon = LisdataIcons.Warning,
                        title = stringResource(R.string.languages_read_failed),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LisdataInfoCard(
                    icon = LisdataIcons.Globe,
                    title = stringResource(R.string.languages_multilingual_title),
                    body = stringResource(R.string.languages_multilingual_body),
                    modifier = Modifier.fillMaxWidth(),
                )
                LisdataGradientButton(
                    text = stringResource(if (state.saving) R.string.languages_saving else R.string.languages_save),
                    onClick = actions.onSave,
                    enabled = !state.saving && state.selectedApp != null,
                    trailingIcon = LisdataIcons.Check,
                    // U7: «Guardando…» is read once, politely.
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(spacing.space4))
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(spacing.space3))
    }
    PreferencesSaveFailure(state.saveFailed, snackbarHostState, actions.onSaveFailureShown)
}

@Composable
private fun LanguageOption(language: AppLanguage, selected: AppLanguage?, onSelect: (AppLanguage) -> Unit) {
    LisdataRadioRow(title = language.displayName(), selected = selected == language, onSelect = { onSelect(language) })
}

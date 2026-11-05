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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataRadioRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.feature.ingest.INDEX_MODES
import com.lisdatasolutions.microrag.feature.ingest.subtitle
import com.lisdatasolutions.microrag.feature.ingest.title
import org.koin.androidx.compose.koinViewModel

data class ImportModeActions(
    val onBack: () -> Unit = {},
    val onSelect: (IndexMode) -> Unit = {},
    val onSave: () -> Unit = {},
    val onSaveFailureShown: () -> Unit = {},
)

/** Back discards the marked option without asking; a successful or unchanged save goes back to Ajustes. */
@Composable
fun ImportModeRoute(onBack: () -> Unit, viewModel: ImportModeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ImportModeEvent.Close -> onBack()
            }
        }
    }
    ImportModeScreen(
        state = state,
        actions = ImportModeActions(
            onBack = onBack,
            onSelect = viewModel::onSelect,
            onSave = viewModel::onSave,
            onSaveFailureShown = viewModel::onSaveFailureShown,
        ),
    )
}

/** Wireframe §4 (contract U6): the three modes as a radio group, the images note and «Guardar», as Tema. */
@Composable
fun ImportModeScreen(
    state: ImportModeUiState,
    actions: ImportModeActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LisdataTopBar(title = stringResource(R.string.import_mode_title), onBack = actions.onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Spacer(Modifier.height(spacing.space1))
                LisdataCardGroup(modifier = Modifier.selectableGroup()) {
                    INDEX_MODES.forEachIndexed { position, mode ->
                        if (position > 0) LisdataDivider()
                        LisdataRadioRow(
                            title = stringResource(mode.title()),
                            subtitle = stringResource(mode.subtitle()),
                            selected = state.selected == mode,
                            onSelect = { actions.onSelect(mode) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.import_mode_images_note),
                    style = LisdataTheme.typography.material.bodyMedium,
                    color = colors.textSecondary,
                )
                LisdataGradientButton(
                    text = stringResource(if (state.saving) R.string.languages_saving else R.string.import_mode_save),
                    onClick = actions.onSave,
                    enabled = !state.saving && state.selected != null,
                    trailingIcon = LisdataIcons.Check,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(spacing.space4))
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(spacing.space3))
    }
    PreferencesSaveFailure(state.saveFailed, snackbarHostState, actions.onSaveFailureShown)
}

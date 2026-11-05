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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import org.koin.androidx.compose.koinViewModel

data class PageDetailActions(
    val onBack: () -> Unit = {},
    val onSelect: (PageDetail) -> Unit = {},
    val onSave: () -> Unit = {},
    val onSaveFailureShown: () -> Unit = {},
)

/** Back discards the marked option without asking; a successful or unchanged save, or the mode off, goes back. */
@Composable
fun PageDetailRoute(onBack: () -> Unit, viewModel: PageDetailViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PageDetailEvent.Close -> onBack()
            }
        }
    }
    PageDetailScreen(
        state = state,
        actions = PageDetailActions(
            onBack = onBack,
            onSelect = viewModel::onSelect,
            onSave = viewModel::onSave,
            onSaveFailureShown = viewModel::onSaveFailureShown,
        ),
    )
}

/**
 * Wireframe §2 (contract U2): the introduction, the three details as a radio group with their waits, the note and
 * «Guardar», as «Modo al importar PDF». The waits are the reference phone's, written as the locale writes numbers.
 */
@Composable
fun PageDetailScreen(
    state: PageDetailUiState,
    actions: PageDetailActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LisdataTopBar(title = stringResource(R.string.page_detail_title), onBack = actions.onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Spacer(Modifier.height(spacing.space1))
                Text(
                    text = stringResource(R.string.page_detail_intro),
                    style = LisdataTheme.typography.material.bodyMedium,
                    color = colors.textSecondary,
                )
                val locale = LocalConfiguration.current.locales[0]
                LisdataCardGroup(modifier = Modifier.selectableGroup()) {
                    PageDetail.entries.forEachIndexed { position, detail ->
                        if (position > 0) LisdataDivider()
                        LisdataRadioRow(
                            title = stringResource(detail.label()),
                            subtitle = stringResource(detail.body()) + " " +
                                stringResource(R.string.page_detail_wait, waitSeconds(detail.estimateTenthsOfSecond, locale)),
                            selected = state.selected == detail,
                            onSelect = { actions.onSelect(detail) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.page_detail_note),
                    style = LisdataTheme.typography.material.bodyMedium,
                    color = colors.textSecondary,
                )
                LisdataGradientButton(
                    text = stringResource(if (state.saving) R.string.languages_saving else R.string.page_detail_save),
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

private fun PageDetail.body(): Int = when (this) {
    PageDetail.FAST -> R.string.page_detail_fast_body
    PageDetail.BALANCED -> R.string.page_detail_balanced_body
    PageDetail.DETAILED -> R.string.page_detail_detailed_body
}

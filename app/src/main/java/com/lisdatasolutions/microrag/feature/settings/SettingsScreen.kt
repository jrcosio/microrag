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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSettingsRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.component.SettingsTrailing
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.ModelOverview
import com.lisdatasolutions.microrag.feature.onboarding.setup.chipText
import com.lisdatasolutions.microrag.feature.onboarding.setup.statusKind

/** What every control of Ajustes asks for; the navigation and the ViewModel decide what happens. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onOpenModel: (ModelId) -> Unit = {},
    val onCloseSheet: () -> Unit = {},
    val onCompleteSetup: () -> Unit = {},
    val onLanguages: () -> Unit = {},
    val onIndex: () -> Unit = {},
    val onClearHistory: () -> Unit = {},
    val onConfirmClear: () -> Unit = {},
    val onDismissClear: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** P09 (PNG 09 with the approved proposals S1–S6, contracts/settings-ui.md §1). */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LisdataTopBar(title = stringResource(R.string.settings_top_title), onBack = actions.onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Headline()
                Section(stringResource(R.string.settings_section_ai)) {
                    ModelRow(ModelId.GEMMA4_E2B, state.models, actions.onOpenModel)
                    LisdataDivider()
                    ModelRow(ModelId.EG2_270M, state.models, actions.onOpenModel)
                }
                Section(stringResource(R.string.settings_section_preferences)) {
                    LisdataSettingsRow(
                        icon = LisdataIcons.Globe,
                        title = stringResource(R.string.settings_languages),
                        subtitle = state.appLanguage.displayName(),
                        onClick = actions.onLanguages,
                    )
                    LisdataDivider()
                    LisdataSettingsRow(
                        icon = LisdataIcons.Database,
                        title = stringResource(R.string.settings_index),
                        subtitle = stringResource(R.string.settings_index_subtitle),
                        onClick = actions.onIndex,
                    )
                }
                Section(stringResource(R.string.settings_section_privacy)) {
                    // S5: information only, read as one sentence and never a switch.
                    val localDescription = stringResource(R.string.settings_local_a11y)
                    LisdataSettingsRow(
                        icon = LisdataIcons.ShieldCheck,
                        title = stringResource(R.string.settings_local),
                        subtitle = stringResource(R.string.settings_local_subtitle),
                        trailing = SettingsTrailing.Chip(stringResource(R.string.settings_local_active), StatusKind.Local),
                        iconTint = colors.success,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = localDescription },
                    )
                    LisdataDivider()
                    LisdataSettingsRow(
                        icon = LisdataIcons.Trash,
                        title = stringResource(R.string.settings_clear_history),
                        subtitle = stringResource(R.string.settings_clear_history_subtitle),
                        destructive = true,
                        onClick = actions.onClearHistory,
                    )
                }
                Spacer(Modifier.height(spacing.space4))
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(spacing.space3))
    }
    state.confirmClear?.let { ClearHistoryDialog(it, actions) }
    Notices(state.message, snackbarHostState, actions.onMessageShown)
    val sheet = state.openSheet?.let { id -> state.models.firstOrNull { it.id == id } }
    if (sheet != null) {
        ModelSheet(
            model = sheet,
            onDismiss = actions.onCloseSheet,
            onCompleteSetup = {
                actions.onCloseSheet()
                actions.onCompleteSetup()
            },
        )
    }
}

/** S6: destructive confirmation; «Cancelar» takes the focus. */
@Composable
private fun ClearHistoryDialog(confirm: ConfirmClear, actions: SettingsActions) {
    val body = stringResource(R.string.settings_clear_body)
    LisdataConfirmationDialog(
        title = stringResource(R.string.settings_clear_title),
        body = if (confirm.stopsAnswer) body + "\n\n" + stringResource(R.string.settings_clear_body_answering) else body,
        confirmText = stringResource(R.string.settings_clear_confirm),
        onConfirm = actions.onConfirmClear,
        onDismiss = actions.onDismissClear,
    )
}

/**
 * S6: each notice once in the snackbar, which TalkBack reads politely. It is consumed as soon as it
 * arrives, so a rotation does not repeat it; the snackbar follows this copy, as consuming it must not
 * cancel it.
 */
@Composable
private fun Notices(message: SettingsMessage?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    var showing by remember { mutableStateOf<SettingsMessage?>(null) }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        showing = message
        onShown()
    }
    val text = showing?.let {
        stringResource(
            when (it) {
                SettingsMessage.HISTORY_CLEARED -> R.string.settings_history_cleared
                SettingsMessage.NO_CONVERSATIONS -> R.string.settings_no_conversations
                SettingsMessage.CLEAR_FAILED -> R.string.settings_clear_failed
            },
        )
    }
    LaunchedEffect(showing) {
        if (text == null) return@LaunchedEffect
        snackbar.showSnackbar(text)
        showing = null
    }
}

@Composable
private fun Headline() {
    val colors = LisdataTheme.colors
    val typography = LisdataTheme.typography.material
    Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
        Text(
            text = stringResource(R.string.settings_title),
            style = typography.headlineLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(R.string.settings_subtitle), style = typography.bodyLarge, color = colors.textSecondary)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
        LisdataSectionHeader(text = title)
        LisdataCardGroup { content() }
    }
}

private const val NO_BREAK_SPACE = '\u00A0'

/**
 * A model row (S1): its real state and a chevron to the sheet. Before the first rescan there is no
 * state, so the row shows neither chip nor action rather than guessing (constitution V). The model's
 * name keeps its spaces unbreakable, so beside a chip it moves whole instead of leaving «2» alone.
 */
@Composable
private fun ModelRow(id: ModelId, models: List<ModelOverview>, onOpen: (ModelId) -> Unit) {
    val model = models.firstOrNull { it.id == id }
    LisdataSettingsRow(
        icon = if (id == ModelId.GEMMA4_E2B) LisdataIcons.Sparkle else LisdataIcons.Layers,
        title = stringResource(if (id == ModelId.GEMMA4_E2B) R.string.settings_model_answers else R.string.settings_model_embeddings),
        subtitle = stringResource(if (id == ModelId.GEMMA4_E2B) R.string.setup_model_gemma_name else R.string.setup_model_embedding_name)
            .replace(' ', NO_BREAK_SPACE),
        trailing = model?.let { SettingsTrailing.ChipAndChevron(it.chip().text(), it.chip().kind()) } ?: SettingsTrailing.None,
        onClickLabel = stringResource(R.string.settings_show_details),
        onClick = model?.let { { onOpen(id) } },
    )
}

@Composable
internal fun ModelChip.text(): String = when (this) {
    ModelChip.Local -> stringResource(R.string.settings_model_local)
    ModelChip.CpuSlow -> stringResource(R.string.settings_model_cpu)
    is ModelChip.Setup -> status.chipText()
}

internal fun ModelChip.kind(): StatusKind = when (this) {
    ModelChip.Local -> StatusKind.Local
    ModelChip.CpuSlow -> StatusKind.Warning
    is ModelChip.Setup -> status.statusKind()
}

/** Each language named in itself, whatever the app language (S11). */
@Composable
internal fun AppLanguage.displayName(): String = stringResource(
    when (this) {
        AppLanguage.SPANISH -> R.string.language_name_es
        AppLanguage.ENGLISH -> R.string.language_name_en
    },
)

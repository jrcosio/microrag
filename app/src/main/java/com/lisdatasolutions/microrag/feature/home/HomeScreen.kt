package com.lisdatasolutions.microrag.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataHeroCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataQuickActionTile
import com.lisdatasolutions.microrag.core.designsystem.component.TrailingIconPlacement
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataLogo
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.koin.androidx.compose.koinViewModel

/** What the stateless screen can ask for (contracts/home-ui.md, navigation). */
data class HomeActions(
    val onNewChat: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onLibrary: () -> Unit = {},
    val onIndex: () -> Unit = {},
    val onLanguages: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onCompleteSetup: () -> Unit = {},
    val onRetrySummary: () -> Unit = {},
)

@Composable
fun HomeRoute(
    onNewChat: () -> Unit,
    onImport: () -> Unit,
    onLibrary: () -> Unit,
    onIndex: () -> Unit,
    onLanguages: () -> Unit,
    onSettings: () -> Unit,
    onCompleteSetup: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        actions = HomeActions(
            onNewChat = onNewChat,
            onImport = onImport,
            onLibrary = onLibrary,
            onIndex = onIndex,
            onLanguages = onLanguages,
            onSettings = onSettings,
            onCompleteSetup = onCompleteSetup,
        ),
    )
}

/** P03 (PNG 03 with the approved proposals P1–P5, contracts/home-ui.md). */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = spacing.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Header(actions.onSettings)
        // FR-009: no chip until the first check, with its room kept so nothing jumps when it comes.
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = spacing.minTouchTarget))
        Text(
            text = stringResource(R.string.home_greeting),
            style = LisdataTheme.typography.material.headlineLarge,
            color = colors.textPrimary,
        )
        LisdataHeroCard(
            title = stringResource(R.string.home_hero_title),
            eyebrow = stringResource(R.string.home_hero_eyebrow),
            decorationIcon = LisdataIcons.Sparkle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LisdataGradientButton(
                text = stringResource(R.string.home_new_chat),
                onClick = actions.onNewChat,
                trailingIcon = LisdataIcons.ArrowRight,
                trailingIconPlacement = TrailingIconPlacement.AfterText,
                modifier = Modifier.padding(top = spacing.space2),
            )
        }
        QuickActions(actions)
        Spacer(Modifier.height(spacing.space4))
    }
}

/** The official logo (G20) and the Settings icon, without a title bar (research R6). */
@Composable
private fun Header(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = LisdataTheme.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LisdataLogo(height = 40.dp)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            LisdataIcon(LisdataIcons.Settings, contentDescription = stringResource(R.string.home_settings), tint = LisdataTheme.colors.textPrimary)
        }
    }
}

private data class QuickAction(val icon: Int, val title: Int, val subtitle: Int, val onClick: () -> Unit)

/** PNG 03: the 2×2 grid, both cards of a row as tall as the taller one. */
@Composable
private fun QuickActions(actions: HomeActions) {
    val entries = listOf(
        QuickAction(LisdataIcons.Upload, R.string.home_import, R.string.home_import_subtitle, actions.onImport),
        QuickAction(LisdataIcons.Folder, R.string.home_documents, R.string.home_documents_subtitle, actions.onLibrary),
        QuickAction(LisdataIcons.Database, R.string.home_index, R.string.home_index_subtitle, actions.onIndex),
        QuickAction(LisdataIcons.Globe, R.string.home_languages, R.string.home_languages_subtitle, actions.onLanguages),
    )
    val gap = LisdataTheme.spacing.gridGap
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        entries.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { entry ->
                    LisdataQuickActionTile(
                        icon = entry.icon,
                        title = stringResource(entry.title),
                        subtitle = stringResource(entry.subtitle),
                        onClick = entry.onClick,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

package com.lisdatasolutions.microrag.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/** The Ajustes tab (F12 US1): its ViewModel, and the destinations it asks the navigation for. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onCompleteSetup: () -> Unit,
    onLanguages: () -> Unit,
    onTheme: () -> Unit,
    onIndex: () -> Unit,
    onImportMode: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
    multimodalViewModel: MultimodalSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val multimodal by multimodalViewModel.state.collectAsStateWithLifecycle()
    // Back to the screen, or to the app: a model file may have changed meanwhile (P13).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onBack = onBack,
            onOpenModel = viewModel::onOpenModel,
            onCloseSheet = viewModel::onCloseSheet,
            onCompleteSetup = onCompleteSetup,
            onLanguages = onLanguages,
            onTheme = onTheme,
            onIndex = onIndex,
            onClearHistory = viewModel::onClearHistory,
            onConfirmClear = viewModel::onConfirmClear,
            onDismissClear = viewModel::onDismissClear,
            onMessageShown = viewModel::onMessageShown,
        ),
        multimodal = multimodal,
        multimodalActions = MultimodalActions(
            onToggle = multimodalViewModel::onToggle,
            onConfirmEnable = multimodalViewModel::onConfirmEnable,
            onConfirmDisable = multimodalViewModel::onConfirmDisable,
            onDismissDialog = multimodalViewModel::onDismissDialog,
            onAction = multimodalViewModel::onAction,
            onNoticeShown = multimodalViewModel::onNoticeShown,
            onImportMode = onImportMode,
        ),
    )
}

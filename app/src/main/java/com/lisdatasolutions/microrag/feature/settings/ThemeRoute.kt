package com.lisdatasolutions.microrag.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Tema (F16 US1): Back discards the marked option without asking (U4); a successful or empty save goes
 * back to Ajustes. The system recreates the activity in the new theme on every version, and the close
 * event still waits in the ViewModel if it comes first (research R5).
 */
@Composable
fun ThemeRoute(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ThemeEvent.Close -> onBack()
            }
        }
    }
    ThemeScreen(
        state = state,
        actions = ThemeActions(
            onBack = onBack,
            onSelect = viewModel::onSelect,
            onSave = viewModel::onSave,
            onSaveFailureShown = viewModel::onSaveFailureShown,
        ),
    )
}

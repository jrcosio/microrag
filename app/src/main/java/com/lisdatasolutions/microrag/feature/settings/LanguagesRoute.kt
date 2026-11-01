package com.lisdatasolutions.microrag.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Idiomas (F12 US3): Back discards the changes without asking (S7); a successful or empty save goes back
 * to where it was opened from. On Android 12 the activity is recreated in the new language after that;
 * from Android 13 the system recreates it, and the close event still waits in the ViewModel.
 */
@Composable
fun LanguagesRoute(
    onBack: () -> Unit,
    viewModel: LanguagesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LanguagesEvent.Close -> {
                    onBack()
                    if (event.recreate) activity?.recreate()
                }
            }
        }
    }
    LanguagesScreen(
        state = state,
        actions = LanguagesActions(
            onBack = onBack,
            onSelectApp = viewModel::onSelectApp,
            onSave = viewModel::onSave,
            onSaveFailureShown = viewModel::onSaveFailureShown,
        ),
    )
}

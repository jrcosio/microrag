package com.lisdatasolutions.microrag.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R

/**
 * S8 of F12, A4 of F16: «No se pudieron guardar las preferencias.» once in the snackbar, which TalkBack reads
 * politely. It is consumed as soon as it arrives, so a rotation does not repeat it, and the snackbar follows
 * this copy. Shared by Idiomas and Tema.
 */
@Composable
internal fun PreferencesSaveFailure(failed: Boolean, snackbar: SnackbarHostState, onShown: () -> Unit) {
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        showing = true
        onShown()
    }
    val text = stringResource(R.string.languages_save_failed)
    LaunchedEffect(showing) {
        if (!showing) return@LaunchedEffect
        snackbar.showSnackbar(text)
        showing = false
    }
}

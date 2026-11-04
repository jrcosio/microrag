package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Shows [text] once in [host] and consumes it at once with [onShown], so a rotation does not repeat it. The
 * snackbar follows a copy, as consuming the message must not cancel it (F08 B8, seen on the phone).
 */
@Composable
fun LisdataSnackbarOnce(text: String?, host: SnackbarHostState, onShown: () -> Unit) {
    var showing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (text == null) return@LaunchedEffect
        showing = text
        onShown()
    }
    LaunchedEffect(showing) {
        val shown = showing ?: return@LaunchedEffect
        host.showSnackbar(shown)
        showing = null
    }
}

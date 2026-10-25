package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/**
 * Confirmation for sensitive actions. The body must explain the consequences; initial focus goes
 * to the safe (cancel) action.
 */
@Composable
fun LisdataConfirmationDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
    dismissText: String = stringResource(R.string.action_cancel),
) {
    val colors = LisdataTheme.colors
    val cancelFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = LisdataTheme.shapes.card,
        title = { Text(text = title, style = LisdataTheme.typography.material.titleLarge) },
        text = { Text(text = body, style = LisdataTheme.typography.material.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    style = LisdataTheme.typography.material.labelLarge,
                    color = if (destructive) colors.error else colors.brandBlue,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus)) {
                Text(text = dismissText, style = LisdataTheme.typography.material.labelLarge, color = colors.textPrimary)
            }
        },
    )
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}

/** Contextual block shown when a required model is missing, with a route to set it up. */
@Composable
fun LisdataModelMissingBlock(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LisdataEmptyState(
        icon = LisdataIcons.Sparkle,
        title = title,
        body = body,
        modifier = modifier,
        action = { LisdataGradientButton(text = actionText, onClick = onAction) },
    )
}

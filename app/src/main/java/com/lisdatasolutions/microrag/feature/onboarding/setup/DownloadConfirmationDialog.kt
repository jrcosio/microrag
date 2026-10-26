package com.lisdatasolutions.microrag.feature.onboarding.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.NetworkKind

/**
 * Confirmation before the first download (FR-009, FR-010, D-027): models and sizes, total, space,
 * current network with the mobile data notice, low RAM notice and the notification permission line.
 */
@Composable
fun DownloadConfirmationDialog(
    confirmation: ConfirmationUi,
    needsNotificationPermission: Boolean,
    onDownload: () -> Unit,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val details = confirmation.details
    val colors = LisdataTheme.colors
    val typography = LisdataTheme.typography.material
    LisdataConfirmationDialog(
        title = stringResource(R.string.confirm_title),
        body = "",
        confirmText = stringResource(R.string.confirm_download),
        onConfirm = onDownload,
        onDismiss = onDismiss,
        destructive = false,
        confirmEnabled = details.missingBytes == null && details.totalBytes > 0,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
                details.models.forEach { (id, size) ->
                    Text(
                        stringResource(R.string.confirm_model_line, stringResource(id.nameRes()), bytesText(size)),
                        style = typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                }
                Text(stringResource(R.string.confirm_total, bytesText(details.totalBytes)), style = typography.titleMedium, color = colors.textPrimary)
                Text(
                    stringResource(R.string.confirm_free_space, bytesText(details.freeBytes), bytesText(details.freeAfterBytes)),
                    style = typography.bodyMedium,
                )
                Text(
                    stringResource(
                        when (details.network) {
                            NetworkKind.WIFI -> R.string.confirm_network_wifi
                            NetworkKind.MOBILE -> R.string.confirm_network_mobile
                            NetworkKind.NONE -> R.string.confirm_network_none
                        },
                    ),
                    style = typography.bodyMedium,
                )
                if (details.network == NetworkKind.MOBILE) Notice(stringResource(R.string.confirm_mobile_warning))
                if (details.lowRam) Notice(stringResource(R.string.confirm_low_ram))
                details.missingBytes?.let { Notice(stringResource(R.string.confirm_no_space, bytesText(it))) }
                if (needsNotificationPermission) Text(stringResource(R.string.confirm_notification_notice), style = typography.bodyMedium)
                TextButton(onClick = onPickFile, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.confirm_pick_file), style = typography.labelLarge, color = colors.brandBlue)
                }
            }
        },
    )
}

@Composable
private fun Notice(text: String) {
    Text(text, style = LisdataTheme.typography.material.bodyMedium, color = LisdataTheme.colors.warning)
}

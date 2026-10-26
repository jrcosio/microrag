package com.lisdatasolutions.microrag.feature.ingest

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomSheet
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataLinearProgress
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSheetContent
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatusChip
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState

/** G15 (proposal P3): the detail of a queue row with its state and the actions it allows. */
@Composable
fun QueueItemSheet(
    item: QueueItem,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    LisdataBottomSheet(onDismiss = onDismiss) {
        QueueItemDetail(item, onRetry, onRemove)
    }
}

@Composable
fun QueueItemDetail(item: QueueItem, onRetry: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    val typography = LisdataTheme.typography.material
    LisdataSheetContent(modifier = modifier) {
        Text(item.displayName, style = typography.titleLarge, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        val size = sizeText(item.sizeBytes)
        val format = item.format?.label()
        val details = if (size != null && format != null) stringResource(R.string.ingest_sheet_meta, size, format) else size ?: format
        if (details != null) Text(details, style = typography.bodyMedium, color = colors.textSecondary)
        val error = item.state.error
        if (error != null) {
            LisdataStatusChip(text = error.chipText(), kind = StatusKind.Error)
            Text(error.detailText(item.format), style = typography.bodyLarge, color = colors.textPrimary)
        } else {
            item.state.stateText()?.let { Text(it, style = typography.bodyLarge, color = colors.textPrimary) }
        }
        item.state.progress()?.let { LisdataLinearProgress(it) }
        if (item.canRetry) {
            LisdataGradientButton(text = stringResource(R.string.ingest_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
        }
        if (item.canRemove) {
            LisdataOutlinedButton(
                text = stringResource(
                    if (item.state is QueueItemState.Importing) R.string.ingest_cancel_import else R.string.ingest_remove,
                ),
                onClick = onRemove,
                destructive = item.removalNeedsConfirmation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Constitution V: removing a registered document deletes its private copy, so it is confirmed. */
@Composable
fun RemovalConfirmation(item: QueueItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    LisdataConfirmationDialog(
        title = stringResource(R.string.ingest_remove_confirm_title),
        body = stringResource(R.string.ingest_remove_confirm_body, item.displayName),
        confirmText = stringResource(R.string.ingest_remove_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.ingest_remove_dismiss),
    )
}

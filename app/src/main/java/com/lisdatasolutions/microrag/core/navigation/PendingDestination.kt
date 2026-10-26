package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** A provisional entry to a screen that already exists, labelled as such (F06 P6, F07 P10). */
data class PendingAction(val label: String, val onClick: () -> Unit)

/**
 * Temporary content of a destination whose screen arrives in a later feature (FR-021). It shows
 * the logical route, the received arguments and the owning feature; no product data. [actions] are
 * provisional, labelled entries to delivered screens until the owning feature replaces this one.
 */
@Composable
fun PendingDestination(
    destinationName: String,
    ownerFeature: String,
    modifier: Modifier = Modifier,
    args: Map<String, Any?> = emptyMap(),
    onBack: (() -> Unit)? = null,
    actions: List<PendingAction> = emptyList(),
) {
    val arguments = if (args.isEmpty()) {
        stringResource(R.string.pending_destination_no_args)
    } else {
        args.entries.joinToString(", ") { (key, value) -> "$key=$value" }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LisdataTheme.colors.background),
    ) {
        LisdataTopBar(title = destinationName, onBack = onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            LisdataEmptyState(
                icon = LisdataIcons.Info,
                title = stringResource(R.string.pending_destination_title, ownerFeature),
                body = stringResource(R.string.pending_destination_body, destinationName, arguments),
                action = actions.takeIf { it.isNotEmpty() }?.let { entries ->
                    {
                        Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space3)) {
                            entries.forEach { LisdataOutlinedButton(text = it.label, onClick = it.onClick) }
                        }
                    }
                },
            )
        }
    }
}

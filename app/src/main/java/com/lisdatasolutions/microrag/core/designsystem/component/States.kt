package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/**
 * Empty state: icon, title, explanation and an optional call to action (empty library, chat
 * without documents, no results, empty queue).
 */
@Composable
fun LisdataEmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space7),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.space3),
    ) {
        LisdataIconTile(icon = icon, style = IconTileStyle.Light, size = 64.dp)
        Text(
            text = title,
            style = LisdataTheme.typography.material.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = LisdataTheme.typography.material.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = spacing.space2)) { action() }
        }
    }
}

/** Error with a retry action. The error is conveyed by icon and text, not by color alone. */
@Composable
fun LisdataErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.space3),
    ) {
        LisdataIcon(LisdataIcons.Warning, contentDescription = null, tint = colors.error)
        Text(
            text = message,
            style = LisdataTheme.typography.material.bodyMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        LisdataOutlinedButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
            leadingIcon = LisdataIcons.Refresh,
        )
    }
}

/** Loading placeholder row for lists; announced as "Cargando" by screen readers. */
@Composable
fun LisdataSkeletonRow(
    modifier: Modifier = Modifier,
    lines: Int = 2,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val loading = stringResource(R.string.state_loading)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(LisdataTheme.shapes.card)
            .background(colors.surface)
            .padding(spacing.cardPadding)
            .clearAndSetSemantics { contentDescription = loading },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Box(
            modifier = Modifier
                .size(spacing.iconTileSize)
                .clip(LisdataTheme.shapes.iconTile)
                .background(colors.neutralTile),
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            repeat(lines.coerceAtLeast(1)) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 0) 0.7f else 0.45f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.neutralTile),
                )
            }
        }
    }
}

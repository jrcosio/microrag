package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataColors
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

private data class StatusStyle(val container: Color, val content: Color, val indicator: Color, val icon: Int?)

private fun LisdataColors.statusStyle(kind: StatusKind): StatusStyle = when (kind) {
    StatusKind.Local, StatusKind.Ready -> StatusStyle(successContainer, success, successGraphic, null)
    StatusKind.Processing -> StatusStyle(surfaceInfo, blue800, brandBlue, null)
    StatusKind.Queued -> StatusStyle(neutralTile, textSecondaryOnContainer, textSecondaryOnContainer, null)
    StatusKind.Paused -> StatusStyle(warningContainer, warning, warning, LisdataIcons.Pause)
    StatusKind.Warning -> StatusStyle(warningContainer, warning, warning, null)
    StatusKind.Error -> StatusStyle(errorContainer, error, error, LisdataIcons.Warning)
    StatusKind.Info -> StatusStyle(surfaceInfo, blue800, brandBlue, null)
}

/**
 * Status pill: a dot or icon plus text, never color alone (FR-013). [onDark] renders the welcome
 * variant over dark backgrounds. When clickable it keeps a 48 dp touch target (FR-014).
 */
@Composable
fun LisdataStatusChip(
    text: String,
    kind: StatusKind,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LisdataTheme.colors
    val style = if (onDark) {
        StatusStyle(colors.onDarkChip, colors.onDarkAccent, colors.onDarkAccent, null)
    } else {
        colors.statusStyle(kind)
    }
    val interaction = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(modifier = modifier.then(interaction), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .clip(LisdataTheme.shapes.chip)
                .background(style.container)
                .padding(horizontal = LisdataTheme.spacing.space3, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2),
        ) {
            if (style.icon != null) {
                LisdataIcon(style.icon, contentDescription = null, tint = style.indicator, size = 14.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(style.indicator),
                )
            }
            Text(text = text, style = LisdataTheme.typography.material.labelMedium, color = style.content)
        }
    }
}

/** Library filter (Todos / Indexados / Pendientes): Material 3 FilterChip themed with tokens (D-018). */
@Composable
fun LisdataFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = text, style = LisdataTheme.typography.material.labelMedium) },
        modifier = modifier,
        shape = LisdataTheme.shapes.chip,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceInfo,
            labelColor = colors.blue800,
            selectedContainerColor = colors.brandBlue,
            selectedLabelColor = colors.onBrand,
        ),
    )
}

/** Small counter pill ("3 archivos", "2 fuentes", "Página 12"). */
@Composable
fun LisdataCountChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = LisdataTheme.typography.material.labelMedium,
        color = LisdataTheme.colors.blue800,
        modifier = modifier
            .clip(LisdataTheme.shapes.chip)
            .background(LisdataTheme.colors.surfaceInfo)
            .padding(horizontal = LisdataTheme.spacing.space3, vertical = 5.dp),
    )
}

/** Uppercase, tracked label ("INGESTA LOCAL"). */
@Composable
fun LisdataEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LisdataTheme.colors.textSecondary,
) {
    Text(
        text = text.toUpperCase(Locale.current),
        style = LisdataTheme.typography.eyebrow,
        color = color,
        modifier = modifier,
    )
}

/** Section title ("FUENTES CONSULTADAS") with an optional trailing element ("Incluir en RAG"). */
@Composable
fun LisdataSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LisdataEyebrow(text = text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Onboarding step indicator: "PASO N DE M" pill and a gradient progress bar. */
@Composable
fun LisdataStepIndicator(
    step: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    require(total > 0 && step in 1..total) { "Invalid step $step of $total" }
    val colors = LisdataTheme.colors
    val description = stringResource(R.string.step_of, step, total)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..total.toFloat(), steps = total - 1)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space4),
    ) {
        Text(
            text = description.toUpperCase(Locale.current),
            style = LisdataTheme.typography.eyebrow,
            color = colors.blue800,
            modifier = Modifier
                .clip(LisdataTheme.shapes.chip)
                .background(colors.surfaceInfo)
                .padding(horizontal = LisdataTheme.spacing.space4, vertical = LisdataTheme.spacing.space2),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(LisdataTheme.shapes.chip)
                .background(colors.outline),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(step / total.toFloat())
                    .height(6.dp)
                    .clip(LisdataTheme.shapes.chip)
                    .background(LisdataTheme.gradients.stepProgress),
            )
        }
    }
}

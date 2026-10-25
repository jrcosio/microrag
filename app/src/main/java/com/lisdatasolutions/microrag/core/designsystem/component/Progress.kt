package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/**
 * Progress ring for dark heroes (indexing). Shows the real percentage, or an indeterminate ring
 * with "Procesando…" when the total is unknown; never an invented number (constitution V).
 */
@Composable
fun LisdataProgressRing(
    progress: ProgressValue,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val colors = LisdataTheme.colors
    val description = when (progress) {
        is ProgressValue.Determinate -> stringResource(R.string.progress_percent, progress.percent)
        ProgressValue.Indeterminate -> stringResource(R.string.progress_indeterminate)
    }
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                stateDescription = description
                progressBarRangeInfo = when (progress) {
                    is ProgressValue.Determinate -> ProgressBarRangeInfo(progress.fraction, 0f..1f)
                    ProgressValue.Indeterminate -> ProgressBarRangeInfo.Indeterminate
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (progress) {
            is ProgressValue.Determinate -> CircularProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.size(size),
                color = colors.brandCyan,
                trackColor = colors.onDarkTrack,
                strokeWidth = 14.dp,
                strokeCap = StrokeCap.Round,
            )
            ProgressValue.Indeterminate -> CircularProgressIndicator(
                modifier = Modifier.size(size),
                color = colors.brandCyan,
                trackColor = colors.onDarkTrack,
                strokeWidth = 14.dp,
                strokeCap = StrokeCap.Round,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (progress is ProgressValue.Determinate) {
                Text(
                    text = stringResource(R.string.progress_value_percent, progress.percent),
                    style = LisdataTheme.typography.progressValue,
                    color = colors.onDarkText,
                )
                LisdataEyebrow(text = label, color = colors.onDarkAccent)
            } else {
                LisdataEyebrow(text = stringResource(R.string.progress_indeterminate), color = colors.onDarkAccent)
            }
        }
    }
}

/** Card with a stage title, real progress, an optional detail line and an optional action. */
@Composable
fun LisdataProgressCard(
    stage: String,
    progress: ProgressValue,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LisdataTheme.colors
    LisdataCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stage,
                    style = LisdataTheme.typography.material.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            LisdataLinearProgress(progress)
            if (detail != null) {
                Text(text = detail, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
            }
        }
    }
}

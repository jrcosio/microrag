package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Constraints
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
    val labelPadding = RingStroke + LisdataTheme.spacing.space4
    Layout(
        modifier = modifier.clearAndSetSemantics {
            stateDescription = description
            progressBarRangeInfo = when (progress) {
                is ProgressValue.Determinate -> ProgressBarRangeInfo(progress.fraction, 0f..1f)
                ProgressValue.Indeterminate -> ProgressBarRangeInfo.Indeterminate
            }
        },
        content = {
            when (progress) {
                is ProgressValue.Determinate -> CircularProgressIndicator(
                    progress = { progress.fraction },
                    color = colors.brandCyan,
                    trackColor = colors.onDarkTrack,
                    strokeWidth = RingStroke,
                    strokeCap = StrokeCap.Round,
                )
                ProgressValue.Indeterminate -> CircularProgressIndicator(
                    color = colors.brandCyan,
                    trackColor = colors.onDarkTrack,
                    strokeWidth = RingStroke,
                    strokeCap = StrokeCap.Round,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (progress is ProgressValue.Determinate) {
                    Text(
                        text = stringResource(R.string.progress_value_percent, progress.percent),
                        style = LisdataTheme.typography.progressValue,
                        color = colors.onDarkText,
                        softWrap = false,
                    )
                }
                Text(
                    text = (if (progress is ProgressValue.Determinate) label else stringResource(R.string.progress_indeterminate))
                        .toUpperCase(Locale.current),
                    style = LisdataTheme.typography.eyebrow,
                    color = colors.onDarkAccent,
                    softWrap = false,
                )
            }
        },
    ) { measurables, constraints ->
        // Square ring sized by its label (large font scales grow it), at least [size].
        val labelPlaceable = measurables[1].measure(Constraints())
        val needed = maxOf(labelPlaceable.width, labelPlaceable.height) + 2 * labelPadding.roundToPx()
        val side = maxOf(size.roundToPx(), needed).coerceAtMost(constraints.maxWidth)
        val ringPlaceable = measurables[0].measure(Constraints.fixed(side, side))
        layout(side, side) {
            ringPlaceable.place(0, 0)
            labelPlaceable.place((side - labelPlaceable.width) / 2, (side - labelPlaceable.height) / 2)
        }
    }
}

private val RingStroke = 14.dp

/**
 * Card with a stage title, real progress, an optional detail line and an optional action. With
 * [onClick] the whole card opens something (the Home indexing card), read with [onClickLabel].
 */
@Composable
fun LisdataProgressCard(
    stage: String,
    progress: ProgressValue,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val colors = LisdataTheme.colors
    LisdataCard(modifier = modifier, onClick = onClick, onClickLabel = onClickLabel) {
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

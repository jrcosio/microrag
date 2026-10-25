package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** User question bubble, aligned to the end. */
@Composable
fun LisdataChatBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            style = LisdataTheme.typography.material.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = LisdataTheme.colors.textPrimary,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(LisdataTheme.shapes.bubble)
                .background(LisdataTheme.colors.surfaceInfoAlt)
                .padding(LisdataTheme.spacing.cardPadding),
        )
    }
}

/**
 * Assistant answer card. [statusText] describes the visible phase while streaming
 * ("Buscando en documentos…", "Generando respuesta…"); cancelled and failed answers say so
 * explicitly. Copy is available once there is text.
 */
@Composable
fun LisdataAnswerCard(
    body: String,
    state: AnswerState,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    sourcesCountText: String? = null,
    onSources: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    LisdataCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
            LisdataIconTile(icon = LisdataIcons.Sparkle, style = IconTileStyle.Accent, size = spacing.avatarSize)
            LisdataEyebrow(text = stringResource(R.string.assistant_name), color = colors.brandBlue)
        }
        if (state == AnswerState.Streaming && statusText != null) {
            Row(
                modifier = Modifier.padding(top = spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.brandBlue, strokeWidth = 2.dp)
                Text(text = statusText, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
            }
        }
        if (body.isNotEmpty()) {
            Text(
                text = body,
                style = LisdataTheme.typography.material.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = spacing.space3),
            )
        }
        when (state) {
            AnswerState.Cancelled -> Text(
                text = stringResource(R.string.answer_cancelled),
                style = LisdataTheme.typography.material.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.space2),
            )
            AnswerState.Failed -> Row(
                modifier = Modifier.padding(top = spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                LisdataIcon(LisdataIcons.Warning, contentDescription = null, tint = colors.error, size = spacing.iconSizeSmall)
                Text(text = stringResource(R.string.answer_failed), style = LisdataTheme.typography.material.bodyMedium, color = colors.error)
            }
            else -> Unit
        }
        LisdataDivider(Modifier.padding(vertical = spacing.space3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val canCopy = body.isNotEmpty() && state != AnswerState.Streaming
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = canCopy, role = Role.Button, onClick = onCopy),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                LisdataIcon(LisdataIcons.Copy, contentDescription = null, tint = colors.textSecondary, size = spacing.iconSizeSmall)
                Text(
                    text = stringResource(R.string.action_copy_answer),
                    style = LisdataTheme.typography.material.labelMedium,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state == AnswerState.Failed && onRetry != null) {
                LisdataOutlinedButton(text = stringResource(R.string.action_retry), onClick = onRetry, leadingIcon = LisdataIcons.Refresh)
            } else if (sourcesCountText != null) {
                LisdataStatusChip(
                    text = sourcesCountText,
                    kind = StatusKind.Info,
                    onClick = onSources,
                )
            }
        }
    }
}

/**
 * Question composer: text field plus send button that turns into stop while generating (G12).
 * The screen anchors it above the keyboard with `imePadding()`.
 */
@Composable
fun LisdataChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    generating: Boolean = false,
    onStop: () -> Unit = {},
    enabled: Boolean = true,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val shape = LisdataTheme.shapes.card
    val canSend = enabled && value.isNotBlank()
    val actionEnabled = generating || canSend
    val actionDescription = stringResource(if (generating) R.string.action_stop else R.string.action_send)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(spacing.borderWidth, colors.outline, shape)
            .padding(spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled && !generating,
            placeholder = { Text(text = placeholder, style = LisdataTheme.typography.material.bodyLarge) },
            textStyle = LisdataTheme.typography.material.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedPlaceholderColor = colors.textSecondary,
                unfocusedPlaceholderColor = colors.textSecondary,
                cursorColor = colors.brandBlue,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
        )
        Box(
            modifier = Modifier
                .size(spacing.minTouchTarget)
                .clip(LisdataTheme.shapes.button)
                .background(if (actionEnabled) LisdataTheme.gradients.cta else SolidColor(colors.disabledContainer))
                .clickable(enabled = actionEnabled, role = Role.Button, onClick = if (generating) onStop else onSend)
                .semantics { contentDescription = actionDescription },
            contentAlignment = Alignment.Center,
        ) {
            LisdataIcon(
                icon = if (generating) LisdataIcons.Stop else LisdataIcons.Send,
                contentDescription = null,
                tint = if (actionEnabled) colors.onDarkText else colors.onDisabled,
            )
        }
    }
}

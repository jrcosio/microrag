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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** User question bubble, aligned to the end; [description] replaces what TalkBack reads («Tú: …», F11 U3). */
@Composable
fun LisdataChatBubble(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
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
                .padding(LisdataTheme.spacing.cardPadding)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        )
    }
}

/**
 * Assistant answer card. [statusText] describes the visible phase while streaming
 * ("Buscando en documentos…", "Generando respuesta…"); cancelled and failed answers say so
 * explicitly, with [failureText] when the reason is not the generic one. Copy is available once there
 * is text, and shows [copiedLabel] after a copy where the system does not confirm it (F11 C10).
 *
 * The body comes formatted ([AnswerContent]): paragraphs, bullets with the brand dot, bold runs and
 * citation chips. A chip is a link inside the text that calls [onCitation] with its number; its 48 dp
 * path is the source row below the card (F11 research R3). [notes] go under the body (C2, C6, C16).
 */
@Composable
fun LisdataAnswerCard(
    content: AnswerContent,
    state: AnswerState,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    sourcesCountText: String? = null,
    onSources: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onCitation: (Int) -> Unit = {},
    notes: List<String> = emptyList(),
    failureText: String? = null,
    copiedLabel: String? = null,
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
        if (!content.isEmpty) AnswerBody(content, onCitation, Modifier.padding(top = spacing.space3))
        notes.forEach { note ->
            Text(
                text = note,
                style = LisdataTheme.typography.material.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.space2),
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
                Text(
                    text = failureText ?: stringResource(R.string.answer_failed),
                    style = LisdataTheme.typography.material.bodyMedium,
                    color = colors.error,
                )
            }
            else -> Unit
        }
        // F11 contracts/chat-ui.md §3: copy only a finished answer with text; retry and the sources chip
        // can show together (an interrupted answer keeps its valid citations, C13).
        val canCopy = !content.isEmpty && (state == AnswerState.Completed || state == AnswerState.Cancelled)
        val retry = onRetry?.takeIf { state == AnswerState.Failed }
        if (!canCopy && retry == null && sourcesCountText == null) return@LisdataCard
        LisdataDivider(Modifier.padding(vertical = spacing.space3))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
            if (canCopy) {
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClick = onCopy),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    LisdataIcon(LisdataIcons.Copy, contentDescription = null, tint = colors.textSecondary, size = spacing.iconSizeSmall)
                    Text(
                        text = copiedLabel ?: stringResource(R.string.action_copy_answer),
                        style = LisdataTheme.typography.material.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (retry != null) {
                LisdataOutlinedButton(text = stringResource(R.string.action_retry), onClick = retry, leadingIcon = LisdataIcons.Refresh)
            }
            if (sourcesCountText != null) LisdataCountChip(text = sourcesCountText, onClick = onSources)
        }
    }
}

@Composable
private fun AnswerBody(content: AnswerContent, onCitation: (Int) -> Unit, modifier: Modifier) {
    val spacing = LisdataTheme.spacing
    val style = LisdataTheme.typography.material.bodyLarge
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        content.blocks.forEach { block ->
            when (block) {
                is AnswerBlock.Paragraph -> AnswerText(block.spans, onCitation)
                is AnswerBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                    val marker = block.marker
                    if (marker == null) {
                        Box(modifier = Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(LisdataTheme.colors.brandBlue))
                        }
                    } else {
                        Text(text = marker, style = style, color = LisdataTheme.colors.brandBlue)
                    }
                    AnswerText(block.spans, onCitation, Modifier.weight(1f))
                }
            }
        }
    }
}

/** A run of text with bold parts and citation chips: links drawn on a rounded `surfaceInfo` background. */
@Composable
private fun AnswerText(spans: List<AnswerSpan>, onCitation: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    val chipStyle = LisdataTheme.typography.material.labelMedium.toSpanStyle().copy(color = colors.blue800, fontWeight = FontWeight.SemiBold)
    val chips = mutableListOf<IntRange>()
    val text = buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is AnswerSpan.Text -> if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) } else append(span.text)
                is AnswerSpan.Citation -> {
                    val start = length
                    withLink(LinkAnnotation.Clickable("cite-${span.number}", TextLinkStyles(chipStyle)) { onCitation(span.number) }) {
                        // Thin spaces pad the number inside its chip.
                        append("\u2009${span.number}\u2009")
                    }
                    chips += start until length
                }
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = LisdataTheme.typography.material.bodyLarge,
        color = colors.textPrimary,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            chips.forEach { range ->
                val bounds = result.getPathForRange(range.first, range.last + 1).getBounds()
                drawRoundRect(color = colors.surfaceInfo, topLeft = bounds.topLeft, size = bounds.size, cornerRadius = CornerRadius(bounds.height / 2))
            }
        },
    )
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

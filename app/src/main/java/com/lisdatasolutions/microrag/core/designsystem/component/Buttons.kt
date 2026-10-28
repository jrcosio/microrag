package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** Where a button's trailing icon sits: at the far end (full-width CTAs) or right after the text. */
enum class TrailingIconPlacement { End, AfterText }

/**
 * Primary call to action with the LISDATA gradient (52 dp). [compact] narrows the side padding, for two
 * buttons side by side (PNG 07).
 */
@Composable
fun LisdataGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes trailingIcon: Int? = null,
    trailingIconPlacement: TrailingIconPlacement = TrailingIconPlacement.End,
    compact: Boolean = false,
) {
    val colors = LisdataTheme.colors
    LisdataButtonContainer(
        text = text,
        onClick = onClick,
        modifier = modifier,
        background = if (enabled) LisdataTheme.gradients.cta else SolidColor(colors.disabledContainer),
        contentColor = if (enabled) colors.onDarkText else colors.onDisabled,
        enabled = enabled,
        loading = loading,
        trailingIcon = trailingIcon,
        trailingIconPlacement = trailingIconPlacement,
        compact = compact,
    )
}

/** Dark navy button, e.g. "Pausar indexación". */
@Composable
fun LisdataDarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes trailingIcon: Int? = null,
) {
    val colors = LisdataTheme.colors
    LisdataButtonContainer(
        text = text,
        onClick = onClick,
        modifier = modifier,
        background = SolidColor(if (enabled) colors.navy900 else colors.disabledContainer),
        contentColor = if (enabled) colors.onDarkText else colors.onDisabled,
        enabled = enabled,
        loading = false,
        trailingIcon = trailingIcon,
        trailingIconPlacement = TrailingIconPlacement.End,
        compact = false,
    )
}

/**
 * Bordered secondary button (48 dp); destructive variant uses the error token ("Eliminar"). [compact]
 * narrows the side padding, as [LisdataGradientButton]'s.
 */
@Composable
fun LisdataOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    compact: Boolean = false,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val shape = LisdataTheme.shapes.button
    val contentColor = when {
        !enabled -> colors.textSecondary
        destructive -> colors.error
        else -> colors.textPrimary
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = spacing.buttonHeightSecondary)
            .clip(shape)
            .background(colors.surface)
            .border(BorderStroke(spacing.borderWidth, colors.outline), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) spacing.space3 else spacing.space5),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            LisdataIcon(leadingIcon, contentDescription = null, tint = contentColor, size = spacing.iconSizeSmall)
            Spacer(Modifier.width(spacing.space2))
        }
        Text(
            text = text,
            style = LisdataTheme.typography.material.labelLarge,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LisdataButtonContainer(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    background: Brush,
    contentColor: Color,
    enabled: Boolean,
    loading: Boolean,
    @DrawableRes trailingIcon: Int?,
    trailingIconPlacement: TrailingIconPlacement,
    compact: Boolean,
) {
    val spacing = LisdataTheme.spacing
    val shape = LisdataTheme.shapes.button
    val loadingDescription = stringResource(R.string.state_loading)
    val iconAtEnd = trailingIcon != null && trailingIconPlacement == TrailingIconPlacement.End
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = spacing.buttonHeight)
            .clip(shape)
            .background(background, shape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .semantics { if (loading) stateDescription = loadingDescription }
            .padding(horizontal = if (compact) spacing.space4 else spacing.space6, vertical = spacing.space3),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(spacing.iconSizeSmall),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                modifier = Modifier.padding(horizontal = if (iconAtEnd) spacing.space6 else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = text,
                    style = LisdataTheme.typography.material.labelLarge,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
                if (trailingIcon != null && !iconAtEnd) {
                    Spacer(Modifier.width(spacing.space3))
                    LisdataIcon(trailingIcon, contentDescription = null, tint = contentColor, size = spacing.iconSizeSmall)
                }
            }
        }
        if (iconAtEnd && !loading) {
            LisdataIcon(
                icon = trailingIcon!!,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

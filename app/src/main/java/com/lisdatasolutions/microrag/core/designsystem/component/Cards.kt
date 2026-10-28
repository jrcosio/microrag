package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import kotlin.math.roundToInt

/** White card with a 1 dp outline and 16 dp corners; clickable when [onClick] is set, with [onClickLabel] for TalkBack. */
@Composable
fun LisdataCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(LisdataTheme.spacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LisdataTheme.colors
    val shape = LisdataTheme.shapes.card
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(LisdataTheme.spacing.borderWidth, colors.outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Thin separator between rows inside a card. */
@Composable
fun LisdataDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = LisdataTheme.colors.divider, thickness = LisdataTheme.spacing.borderWidth)
}

/**
 * Dark gradient hero (Home, Document detail, Indexing, Languages). [titleAccent] renders a second
 * title line in the accent color; the Indexing hero (PNG 08) has no title at all. The orbit
 * decoration is purely visual and hidden from TalkBack, as is [decorationIcon] at its center (the
 * Home sparkle, PNG 03). [compact] has the tighter padding and gaps of PNG 08 (D-042).
 */
@Composable
fun LisdataHeroCard(
    title: String?,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    titleAccent: String? = null,
    decoration: Boolean = true,
    @DrawableRes decorationIcon: Int? = null,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Box(
        modifier = modifier
            .clip(LisdataTheme.shapes.hero)
            .background(LisdataTheme.gradients.hero),
    ) {
        if (decoration) {
            val orbit = colors.brandCyan.copy(alpha = 0.45f)
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width * ORBIT_CENTER_X, size.height * ORBIT_CENTER_Y)
                drawCircle(orbit, radius = 44.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                drawCircle(orbit, radius = 72.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
            }
            if (decorationIcon != null) OrbitCenter { LisdataIcon(decorationIcon, contentDescription = null, tint = colors.onDarkAccent, size = 32.dp) }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) spacing.space4 else spacing.space5),
            verticalArrangement = Arrangement.spacedBy(if (compact) spacing.space2 else spacing.space3),
        ) {
            if (eyebrow != null) LisdataEyebrow(text = eyebrow, color = colors.onDarkAccent)
            if (title != null) {
                Column {
                    Text(text = title, style = LisdataTheme.typography.material.headlineMedium, color = colors.onDarkText)
                    if (titleAccent != null) {
                        Text(text = titleAccent, style = LisdataTheme.typography.material.headlineMedium, color = colors.onDarkAccent)
                    }
                }
            }
            content()
        }
    }
}

/** Places [content] centered on the orbits of [LisdataHeroCard], over the whole hero. */
@Composable
private fun BoxScope.OrbitCenter(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.matchParentSize()) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                (constraints.maxWidth * ORBIT_CENTER_X - placeable.width / 2f).roundToInt(),
                (constraints.maxHeight * ORBIT_CENTER_Y - placeable.height / 2f).roundToInt(),
            )
        }
    }
}

private const val ORBIT_CENTER_X = 0.86f
private const val ORBIT_CENTER_Y = 0.22f

/** Light blue information card with icon, title and optional body. */
@Composable
fun LisdataInfoCard(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Row(
        modifier = modifier
            .clip(LisdataTheme.shapes.card)
            .background(colors.surfaceInfoAlt)
            .padding(spacing.cardPadding)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        LisdataIcon(icon, contentDescription = null, tint = colors.brandBlue)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
            Text(text = title, style = LisdataTheme.typography.material.titleMedium, color = colors.textPrimary)
            if (body != null) {
                Text(text = body, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondaryOnContainer)
            }
        }
    }
}

/**
 * Labelled figure ("PÁGINAS 48", "FRAGMENTOS 860"); [emphasis] colors the value in brand blue. With
 * [accessibilityText] TalkBack reads that instead ("Páginas, 48"; "—" read as words).
 */
@Composable
fun LisdataStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    caption: String? = null,
    accessibilityText: String? = null,
) {
    val colors = LisdataTheme.colors
    val semantics = if (accessibilityText != null) {
        Modifier.clearAndSetSemantics { contentDescription = accessibilityText }
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    LisdataCard(modifier = modifier.then(semantics)) {
        LisdataEyebrow(text = label)
        Text(
            text = value,
            style = LisdataTheme.typography.material.headlineMedium,
            color = if (emphasis) colors.brandBlue else colors.textPrimary,
            modifier = Modifier.padding(top = LisdataTheme.spacing.space2),
        )
        if (caption != null) {
            Text(text = caption, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
        }
    }
}

/** Full-card quick action (Home 2×2 grid): icon tile, title and subtitle. */
@Composable
fun LisdataQuickActionTile(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    LisdataCard(modifier = modifier, onClick = onClick) {
        LisdataIconTile(icon = icon, style = IconTileStyle.Light, size = LisdataTheme.spacing.iconTileSizeSmall)
        Text(
            text = title,
            style = LisdataTheme.typography.material.titleMedium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LisdataTheme.spacing.space3),
        )
        Text(
            text = subtitle,
            style = LisdataTheme.typography.material.bodyMedium,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Groups rows (settings, radios) inside a single card separated by dividers. */
@Composable
fun LisdataCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LisdataCard(modifier = modifier, contentPadding = PaddingValues(0.dp), content = content)
}

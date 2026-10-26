package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.CitationUi
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StageStatus
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/**
 * Model row for onboarding and settings: name, purpose, real status, optional [detail] line and
 * progress. With [accessibilityText] the row is announced as that single sentence and status changes
 * are read politely (live region), so it must change only on meaningful steps (F03 FR-031).
 */
@Composable
fun LisdataModelStatusRow(
    @DrawableRes icon: Int,
    name: String,
    purpose: String,
    statusText: String,
    status: StatusKind,
    modifier: Modifier = Modifier,
    progress: ProgressValue? = null,
    detail: String? = null,
    accessibilityText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    LisdataCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = if (accessibilityText != null) {
                Modifier.clearAndSetSemantics {
                    contentDescription = accessibilityText
                    liveRegion = LiveRegionMode.Polite
                }
            } else {
                Modifier.semantics(mergeDescendants = true) {}
            },
            horizontalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            LisdataIconTile(icon = icon, style = IconTileStyle.Light)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                Text(text = name, style = LisdataTheme.typography.material.titleMedium, color = colors.textPrimary)
                Text(text = purpose, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
                LisdataStatusChip(text = statusText, kind = status, modifier = Modifier.padding(top = spacing.space2))
                if (progress != null) LisdataLinearProgress(progress, Modifier.padding(top = spacing.space2))
                if (detail != null) {
                    Text(text = detail, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
                }
            }
            if (onClick != null) {
                LisdataIcon(
                    icon = LisdataIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.brandBlue,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/**
 * Library row: type tile, name, meta ("PDF · 860 fragmentos") or status, and the "include in RAG"
 * switch, disabled until the document is indexed. Tapping the row opens the detail.
 */
@Composable
fun LisdataDocumentRow(
    name: String,
    meta: String,
    included: Boolean,
    includeEnabled: Boolean,
    onIncludedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    status: StatusKind? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val switchDescription = stringResource(R.string.cd_include_in_rag, name)
    LisdataCard(modifier = modifier, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            LisdataIconTile(icon = LisdataIcons.FileText, style = IconTileStyle.Light)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(spacing.space1),
            ) {
                Text(
                    text = name,
                    style = LisdataTheme.typography.material.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = meta, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
                if (statusText != null && status != null) LisdataStatusChip(text = statusText, kind = status)
            }
            Switch(
                checked = included,
                onCheckedChange = onIncludedChange,
                enabled = includeEnabled,
                colors = lisdataSwitchColors(),
                modifier = Modifier.semantics { contentDescription = switchDescription },
            )
        }
    }
}

/** Import queue row: name, size and state, optional real progress and contextual action. */
@Composable
fun LisdataQueueRow(
    name: String,
    meta: String,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    status: StatusKind? = null,
    progress: ProgressValue? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    LisdataCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            LisdataIconTile(icon = LisdataIcons.FileText, style = IconTileStyle.Light)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                Text(
                    text = name,
                    style = LisdataTheme.typography.material.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = meta, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
                if (statusText != null && status != null) LisdataStatusChip(text = statusText, kind = status)
                if (progress != null) LisdataLinearProgress(progress)
            }
            if (onClick != null) {
                LisdataIcon(LisdataIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary)
            }
        }
    }
}

/** Source under an answer ("Manual.pdf · Página 12 · Fragmento 86"); deleted sources are inert. */
@Composable
fun LisdataSourceCitation(
    citation: CitationUi,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val details = buildList {
        citation.page?.let { add(stringResource(R.string.citation_page, it)) }
        citation.fragment?.let { add(stringResource(R.string.citation_fragment, it)) }
    }.joinToString(" · ")
    LisdataCard(modifier = modifier, onClick = if (citation.available) onOpen else null) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            LisdataIcon(
                icon = LisdataIcons.FileText,
                contentDescription = null,
                tint = if (citation.available) colors.brandBlue else colors.textSecondary,
                size = 28.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = citation.documentName,
                    style = LisdataTheme.typography.material.titleMedium,
                    color = if (citation.available) colors.textPrimary else colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = if (citation.available) details else stringResource(R.string.source_unavailable)
                if (secondary.isNotEmpty()) {
                    Text(text = secondary, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
                }
            }
            if (citation.available) LisdataIcon(LisdataIcons.ChevronRight, contentDescription = null, tint = colors.brandBlue)
        }
    }
}

/** Trailing element of a [LisdataSettingsRow]. */
sealed interface SettingsTrailing {
    data object Chevron : SettingsTrailing
    data class Chip(val text: String, val kind: StatusKind) : SettingsTrailing
    data object None : SettingsTrailing
}

/** Settings entry; place several inside [LisdataCardGroup] separated by [LisdataDivider]. */
@Composable
fun LisdataSettingsRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: SettingsTrailing = SettingsTrailing.Chevron,
    destructive: Boolean = false,
    iconTint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = spacing.cardPadding, vertical = spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        LisdataIcon(
            icon = icon,
            contentDescription = null,
            tint = iconTint ?: if (destructive) colors.error else colors.brandBlue,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = LisdataTheme.typography.material.titleMedium, color = colors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
            }
        }
        when (trailing) {
            SettingsTrailing.Chevron -> LisdataIcon(LisdataIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary)
            is SettingsTrailing.Chip -> LisdataStatusChip(text = trailing.text, kind = trailing.kind)
            SettingsTrailing.None -> Unit
        }
    }
}

/** Card whose whole surface toggles a switch ("Incluir en respuestas"). */
@Composable
fun LisdataSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = LisdataTheme.colors
    LisdataCard(modifier = modifier) {
        Row(
            modifier = Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space4),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = LisdataTheme.typography.material.titleMedium, color = colors.textPrimary)
                if (subtitle != null) {
                    Text(text = subtitle, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
                }
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = lisdataSwitchColors())
        }
    }
}

/** Selectable option row ("Español", "Automático"); place several inside [LisdataCardGroup]. */
@Composable
fun LisdataRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = spacing.cardPadding, vertical = spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = LisdataTheme.typography.material.titleMedium, color = colors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
            }
        }
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.brandBlue, unselectedColor = colors.textSecondary),
        )
    }
}

/** Indexing stage ("Generación de embeddings · En curso"): icon tile and status text. */
@Composable
fun LisdataStageRow(
    @DrawableRes icon: Int,
    title: String,
    statusText: String,
    status: StageStatus,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    val (tileIcon, tileStyle, statusColor) = when (status) {
        StageStatus.Done -> Triple(LisdataIcons.Check, IconTileStyle.Success, colors.success)
        StageStatus.InProgress -> Triple(icon, IconTileStyle.Light, colors.brandBlue)
        StageStatus.Pending -> Triple(icon, IconTileStyle.Neutral, colors.textSecondary)
        StageStatus.Failed -> Triple(LisdataIcons.Warning, IconTileStyle.Error, colors.error)
    }
    LisdataCard(modifier = modifier) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space4),
        ) {
            LisdataIconTile(icon = tileIcon, style = tileStyle, size = LisdataTheme.spacing.iconTileSizeSmall)
            Column {
                Text(text = title, style = LisdataTheme.typography.material.titleMedium, color = colors.textPrimary)
                Text(text = statusText, style = LisdataTheme.typography.material.bodyMedium, color = statusColor)
            }
        }
    }
}

/** Real linear progress: determinate with a known total, indeterminate otherwise. */
@Composable
fun LisdataLinearProgress(
    progress: ProgressValue,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    when (progress) {
        is ProgressValue.Determinate -> LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = modifier.fillMaxWidth(),
            color = colors.brandBlue,
            trackColor = colors.outline,
        )
        ProgressValue.Indeterminate -> LinearProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            color = colors.brandBlue,
            trackColor = colors.outline,
        )
    }
}

@Composable
private fun lisdataSwitchColors(): SwitchColors {
    val colors = LisdataTheme.colors
    return SwitchDefaults.colors(
        checkedThumbColor = colors.onBrand,
        checkedTrackColor = colors.brandBlue,
        checkedBorderColor = colors.brandBlue,
        uncheckedThumbColor = colors.textSecondary,
        uncheckedTrackColor = colors.neutralTile,
        uncheckedBorderColor = colors.textSecondary,
    )
}

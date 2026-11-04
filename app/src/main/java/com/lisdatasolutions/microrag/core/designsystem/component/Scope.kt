package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

// F17 K7 and K8 (research R9): the scope of the chat, its chip and the rows of its selector.

/**
 * A checkbox row with three states, read as one item (K13): «Manual.pdf, Pendiente de indexar». Disabled,
 * it is greyed out and [subtitle] says why. [stateText] replaces the state TalkBack reads, for «algunos
 * documentos».
 */
@Composable
fun LisdataCheckRow(
    title: String,
    checked: ToggleableState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    stateText: String? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .triStateToggleable(state = checked, enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .then(if (stateText != null) Modifier.semantics { stateDescription = stateText } else Modifier)
            .padding(horizontal = spacing.cardPadding, vertical = spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space3),
    ) {
        TriStateCheckbox(state = checked, onClick = null, enabled = enabled, colors = lisdataCheckboxColors())
        CheckTexts(title, subtitle, enabled, Modifier.weight(1f))
    }
}

/**
 * A folder of the selector (K8): its three-state checkbox with its name and count, and a separate button
 * that shows its documents, «Mostrar documentos», which says whether it is open.
 */
@Composable
fun LisdataExpandableRow(
    title: String,
    checked: ToggleableState,
    onToggle: () -> Unit,
    expanded: Boolean,
    onExpand: () -> Unit,
    expandLabel: String,
    expandedText: String,
    collapsedText: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    stateText: String? = null,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Row(modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .triStateToggleable(state = checked, enabled = enabled, role = Role.Checkbox, onClick = onToggle)
                .then(if (stateText != null) Modifier.semantics { stateDescription = stateText } else Modifier)
                .padding(start = spacing.cardPadding, top = spacing.space2, bottom = spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            TriStateCheckbox(state = checked, onClick = null, enabled = enabled, colors = lisdataCheckboxColors())
            CheckTexts(title, subtitle, enabled, Modifier.weight(1f))
        }
        IconButton(
            onClick = onExpand,
            modifier = Modifier
                .padding(end = spacing.space1)
                .semantics {
                    contentDescription = expandLabel
                    stateDescription = if (expanded) expandedText else collapsedText
                },
        ) {
            LisdataIcon(
                LisdataIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.rotate(if (expanded) -90f else 90f),
            )
        }
    }
}

/**
 * The chat's scope above the composer (K7): «Buscar en: Legal», cut with «…» when it does not fit. It is
 * one button, [description] for TalkBack and [onClickLabel] its action, «Cambiar ámbito».
 */
@Composable
fun LisdataScopeChip(
    text: String,
    description: String,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(LisdataTheme.shapes.chip)
            .clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = spacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(LisdataTheme.shapes.chip)
                .background(colors.surfaceInfo)
                .padding(horizontal = spacing.space3, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            LisdataIcon(LisdataIcons.Folder, contentDescription = null, tint = colors.blue800, size = 16.dp)
            Text(
                text = text,
                style = LisdataTheme.typography.material.labelMedium,
                color = colors.blue800,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            LisdataIcon(LisdataIcons.ChevronRight, contentDescription = null, tint = colors.blue800, size = 16.dp, modifier = Modifier.rotate(90f))
        }
    }
}

@Composable
private fun CheckTexts(title: String, subtitle: String?, enabled: Boolean, modifier: Modifier) {
    val colors = LisdataTheme.colors
    Column(modifier = modifier) {
        Text(
            text = title,
            style = LisdataTheme.typography.material.bodyLarge,
            color = if (enabled) colors.textPrimary else colors.textSecondary,
        )
        if (subtitle != null) {
            Text(text = subtitle, style = LisdataTheme.typography.material.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun lisdataCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = LisdataTheme.colors.brandBlue,
    uncheckedColor = LisdataTheme.colors.textSecondary,
    checkmarkColor = LisdataTheme.colors.onBrand,
    disabledUncheckedColor = LisdataTheme.colors.outline,
    disabledCheckedColor = LisdataTheme.colors.outline,
)

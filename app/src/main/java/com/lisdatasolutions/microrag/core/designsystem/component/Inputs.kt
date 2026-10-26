package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** Search field with leading search icon and a clear action when there is a query. */
@Composable
fun LisdataSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = { onQueryChange("") },
) {
    val colors = LisdataTheme.colors
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LisdataTheme.spacing.searchFieldHeight),
        singleLine = true,
        shape = LisdataTheme.shapes.search,
        textStyle = LisdataTheme.typography.material.bodyLarge,
        placeholder = { Text(text = placeholder, style = LisdataTheme.typography.material.bodyLarge) },
        leadingIcon = { LisdataIcon(LisdataIcons.Search, contentDescription = null, tint = colors.textSecondary) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    LisdataIcon(
                        icon = LisdataIcons.Close,
                        contentDescription = stringResource(R.string.action_clear_search),
                        tint = colors.textSecondary,
                    )
                }
            }
        } else {
            null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.brandBlue,
            unfocusedBorderColor = colors.outline,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedPlaceholderColor = colors.textSecondary,
            unfocusedPlaceholderColor = colors.textSecondary,
            cursorColor = colors.brandBlue,
        ),
    )
}

/**
 * Import area with dashed border that launches the system file picker. It deliberately does not
 * promise drag and drop (G14).
 */
@Composable
fun LisdataFilePickerCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val dashColor = colors.brandCyan.copy(alpha = 0.6f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LisdataTheme.shapes.card)
            .background(colors.surfaceInfo.copy(alpha = 0.5f))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .padding(spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.space3),
    ) {
        LisdataIconTile(icon = LisdataIcons.Upload, style = IconTileStyle.Light, size = 64.dp)
        Text(
            text = title,
            style = LisdataTheme.typography.material.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = LisdataTheme.typography.material.bodyMedium,
            color = colors.textSecondaryOnContainer,
            textAlign = TextAlign.Center,
        )
        LisdataGradientButton(text = buttonText, onClick = onPick, enabled = enabled)
    }
}

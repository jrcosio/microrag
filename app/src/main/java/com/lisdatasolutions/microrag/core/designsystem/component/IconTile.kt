package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** Rounded square behind an icon. Decorative: meaning must come from nearby text. */
@Composable
fun LisdataIconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    style: IconTileStyle = IconTileStyle.Light,
    size: Dp = LisdataTheme.spacing.iconTileSize,
) {
    val colors = LisdataTheme.colors
    val (background, tint) = when (style) {
        IconTileStyle.Light -> SolidColor(colors.surfaceInfoAlt) to colors.brandBlue
        IconTileStyle.OnDark -> SolidColor(colors.onDarkTile) to colors.onDarkText
        IconTileStyle.Success -> SolidColor(colors.successContainer) to colors.success
        IconTileStyle.Neutral -> SolidColor(colors.neutralTile) to colors.textSecondaryOnContainer
        IconTileStyle.Error -> SolidColor(colors.errorContainer) to colors.error
        IconTileStyle.Accent -> LisdataTheme.gradients.brandAccent to colors.onDarkText
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(LisdataTheme.shapes.iconTile)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        LisdataIcon(icon, contentDescription = null, tint = tint, size = LisdataTheme.spacing.iconSize)
    }
}

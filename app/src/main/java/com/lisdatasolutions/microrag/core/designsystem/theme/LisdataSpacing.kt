package com.lisdatasolutions.microrag.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing and sizing tokens (data-model.md §4); 4 dp base unit. */
@Immutable
data class LisdataSpacing(
    val space1: Dp = 4.dp,
    val space2: Dp = 8.dp,
    val space3: Dp = 12.dp,
    val space4: Dp = 16.dp,
    val space5: Dp = 20.dp,
    val space6: Dp = 24.dp,
    val space7: Dp = 32.dp,
    val space8: Dp = 40.dp,
    val screenPaddingHorizontal: Dp = 20.dp,
    val gridGap: Dp = 12.dp,
    val listGap: Dp = 12.dp,
    val cardPadding: Dp = 16.dp,
    val minTouchTarget: Dp = 48.dp,
    val buttonHeight: Dp = 52.dp,
    val buttonHeightSecondary: Dp = 48.dp,
    val searchFieldHeight: Dp = 48.dp,
    val iconSize: Dp = 24.dp,
    val iconSizeSmall: Dp = 20.dp,
    val iconTileSize: Dp = 44.dp,
    val iconTileSizeSmall: Dp = 40.dp,
    val avatarSize: Dp = 32.dp,
    val borderWidth: Dp = 1.dp,
)

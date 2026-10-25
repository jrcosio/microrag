package com.lisdatasolutions.microrag.core.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Corner tokens measured on the mockups (data-model.md §4). */
@Immutable
data class LisdataShapes(
    val card: CornerBasedShape = RoundedCornerShape(16.dp),
    val hero: CornerBasedShape = RoundedCornerShape(16.dp),
    val bubble: CornerBasedShape = RoundedCornerShape(16.dp),
    val button: CornerBasedShape = RoundedCornerShape(12.dp),
    val iconTile: CornerBasedShape = RoundedCornerShape(12.dp),
    val search: CornerBasedShape = RoundedCornerShape(24.dp),
    val chip: CornerBasedShape = RoundedCornerShape(percent = 50),
) {
    val material: Shapes
        get() = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = button,
            medium = card,
            large = hero,
            extraLarge = search,
        )
}

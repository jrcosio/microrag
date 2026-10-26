package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/**
 * Themed Material 3 bottom sheet (D-018) for details and actions of one item, first used by the
 * import queue (F06 G15). The content goes in [LisdataSheetContent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LisdataBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LisdataTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) },
    ) {
        LisdataSheetContent(content = content)
    }
}

/** Padding and spacing of a sheet, separate so previews can show it without a window. */
@Composable
fun LisdataSheetContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val spacing = LisdataTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = spacing.screenPaddingHorizontal, end = spacing.screenPaddingHorizontal, bottom = spacing.space6),
        verticalArrangement = Arrangement.spacedBy(spacing.space3),
        content = content,
    )
}

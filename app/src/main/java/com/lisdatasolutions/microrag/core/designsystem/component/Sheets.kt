package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
        // A detail sheet opens whole: half open, its actions at the bottom would stay off screen (F12 T065).
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        // The handle is actionable (expand, dismiss): 48 dp of touch area around the 32 dp bar.
        dragHandle = { BottomSheetDefaults.DragHandle(modifier = Modifier.minimumInteractiveComponentSize(), color = colors.outline) },
    ) {
        LisdataSheetContent(content = content)
    }
}

/**
 * Padding and spacing of a sheet, separate so previews can show it without a window. It scrolls, so
 * every row and action stays reachable with large text or in landscape.
 */
@Composable
fun LisdataSheetContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val spacing = LisdataTheme.spacing
    val scroll = rememberScrollState()
    // It scrolls only within a height limit: a host that measures the sheet unbounded (Robolectric) has none.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (constraints.hasBoundedHeight) Modifier.verticalScroll(scroll) else Modifier)
                .navigationBarsPadding()
                .padding(start = spacing.screenPaddingHorizontal, end = spacing.screenPaddingHorizontal, bottom = spacing.space6),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
            content = content,
        )
    }
}

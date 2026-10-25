package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** Screen top bar: optional back arrow, title, optional subtitle row and trailing actions. */
@Composable
fun LisdataTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(
                    start = if (onBack != null) spacing.space1 else spacing.screenPaddingHorizontal,
                    end = spacing.space1,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    LisdataIcon(
                        icon = LisdataIcons.ChevronLeft,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colors.textPrimary,
                    )
                }
            }
            Text(
                text = title,
                style = LisdataTheme.typography.material.titleLarge,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.space2)
                    .semantics { heading() },
            )
            actions()
        }
        if (subtitle != null) {
            Box(
                modifier = Modifier.padding(
                    start = spacing.screenPaddingHorizontal,
                    end = spacing.screenPaddingHorizontal,
                    bottom = spacing.space2,
                ),
            ) {
                subtitle()
            }
        }
    }
}

/** One destination of [LisdataBottomBar]. */
data class LisdataBottomBarItem(
    @DrawableRes val icon: Int,
    val label: String,
)

/** Bottom navigation bar with icon, label and selection indicator (FR-017). */
@Composable
fun LisdataBottomBar(
    items: List<LisdataBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    val typography = LisdataTheme.typography
    Column(modifier = modifier) {
        HorizontalDivider(color = colors.outline, thickness = LisdataTheme.spacing.borderWidth)
        NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(index) },
                    icon = { LisdataIcon(item.icon, contentDescription = null) },
                    label = {
                        Text(
                            text = item.label,
                            style = if (selected) typography.navLabelSelected else typography.navLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.brandBlue,
                        selectedTextColor = colors.blue800,
                        indicatorColor = colors.surfaceInfoAlt,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                    ),
                )
            }
        }
    }
}

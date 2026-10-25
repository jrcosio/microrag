package com.lisdatasolutions.microrag.debug.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataFilterChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

private val themeOptions = listOf("Sistema", "Claro", "Oscuro")
/** null keeps the system font scale (including Android 14+ non-linear scaling). */
private val fontScaleOptions = listOf<Float?>(null, 1f, 1.5f, 2f)

/** Debug-only catalog of every LISDATA component with theme and font scale switches (FR-011). */
@Composable
fun ComponentCatalogScreen(onBack: () -> Unit) {
    var themeIndex by rememberSaveable { mutableIntStateOf(0) }
    var fontScaleIndex by rememberSaveable { mutableIntStateOf(0) }
    val fontScale = fontScaleOptions[fontScaleIndex]
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeIndex) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val density = LocalDensity.current
    val catalogDensity = if (fontScale == null) density else Density(density.density, fontScale)
    CompositionLocalProvider(LocalDensity provides catalogDensity) {
        LisdataTheme(darkTheme = dark) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LisdataTheme.colors.background),
            ) {
                LisdataTopBar(title = "Catálogo de componentes", onBack = onBack)
                FlowRow(
                    modifier = Modifier.padding(horizontal = LisdataTheme.spacing.screenPaddingHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    themeOptions.forEachIndexed { index, label ->
                        LisdataFilterChip(label, selected = themeIndex == index, onClick = { themeIndex = index })
                    }
                    fontScaleOptions.forEachIndexed { index, scale ->
                        val label = if (scale == null) "Fuente del sistema" else "${(scale * 100).toInt()} %"
                        LisdataFilterChip(label, selected = fontScaleIndex == index, onClick = { fontScaleIndex = index })
                    }
                }
                Text(
                    text = SampleData.BANNER,
                    color = LisdataTheme.colors.warning,
                    style = LisdataTheme.typography.material.labelMedium,
                    modifier = Modifier.padding(horizontal = LisdataTheme.spacing.screenPaddingHorizontal, vertical = 4.dp),
                )
                LazyColumn(
                    // Entries render top/bottom bars; consume system insets so they are not applied twice.
                    modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars),
                    contentPadding = PaddingValues(
                        start = LisdataTheme.spacing.screenPaddingHorizontal,
                        end = LisdataTheme.spacing.screenPaddingHorizontal,
                        top = LisdataTheme.spacing.space3,
                        bottom = LisdataTheme.spacing.space7 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space5),
                ) {
                    catalogEntries.groupBy { it.section }.forEach { (section, entries) ->
                        item(key = "section/$section") { LisdataSectionHeader(section) }
                        items(entries, key = { it.id }) { entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
                                Text(
                                    text = entry.title,
                                    style = LisdataTheme.typography.material.labelMedium,
                                    color = LisdataTheme.colors.textSecondary,
                                )
                                entry.content()
                            }
                        }
                    }
                }
            }
        }
    }
}

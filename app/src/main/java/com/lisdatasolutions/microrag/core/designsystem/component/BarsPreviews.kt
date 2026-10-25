package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

private val sampleTabs = listOf(
    LisdataBottomBarItem(LisdataIcons.Home, "Inicio"),
    LisdataBottomBarItem(LisdataIcons.Chat, "Chat"),
    LisdataBottomBarItem(LisdataIcons.Folder, "Biblioteca"),
    LisdataBottomBarItem(LisdataIcons.Settings, "Ajustes"),
)

@LisdataPreviews
@Composable
private fun TopBarsPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LisdataTopBar(title = "Importar documentos", onBack = {})
        LisdataTopBar(
            title = "Chat con documentos",
            onBack = {},
            subtitle = { Text("Gemma 4 · Procesamiento local") },
            actions = { IconButton(onClick = {}) { LisdataIcon(LisdataIcons.MoreHorizontal, "Más opciones") } },
        )
        LisdataTopBar(title = "Un título de barra superior deliberadamente largo para probar el ajuste", onBack = {})
    }
}

@LisdataPreviews
@Composable
private fun BottomBarPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LisdataBottomBar(items = sampleTabs, selectedIndex = 0, onSelect = {})
        LisdataBottomBar(items = sampleTabs, selectedIndex = 2, onSelect = {})
    }
}

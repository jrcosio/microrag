package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun EmptyStatePreview() = LisdataPreviewSurface {
    LisdataEmptyState(
        icon = LisdataIcons.Folder,
        title = "Aún no tienes documentos",
        body = "Importa PDF, TXT o Markdown para consultarlos con tu IA local.",
        action = { LisdataGradientButton("Importar documentos", onClick = {}) },
    )
}

@LisdataPreviews
@Composable
private fun ErrorStatePreview() = LisdataPreviewSurface {
    LisdataErrorState(message = "No se pudo cargar el resumen de la biblioteca.", onRetry = {})
}

@LisdataPreviews
@Composable
private fun SkeletonPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataSkeletonRow()
        LisdataSkeletonRow(lines = 1)
    }
}

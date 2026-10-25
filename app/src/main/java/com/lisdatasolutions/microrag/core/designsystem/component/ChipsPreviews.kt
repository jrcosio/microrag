package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

@LisdataPreviews
@Composable
private fun StatusChipsPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LisdataStatusChip("IA local disponible", StatusKind.Ready)
        LisdataStatusChip("Offline", StatusKind.Local)
        LisdataStatusChip("En curso", StatusKind.Processing)
        LisdataStatusChip("En cola", StatusKind.Queued)
        LisdataStatusChip("Pausado", StatusKind.Paused)
        LisdataStatusChip("Por configurar", StatusKind.Warning)
        LisdataStatusChip("Error de indexación", StatusKind.Error)
        LisdataStatusChip("Información", StatusKind.Info)
        Column(Modifier.background(LisdataTheme.colors.navy900).padding(8.dp)) {
            LisdataStatusChip("100% en tu dispositivo", StatusKind.Local, onDark = true)
        }
    }
}

@LisdataPreviews
@Composable
private fun FiltersAndCountersPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LisdataFilterChip("Todos", selected = true, onClick = {})
            LisdataFilterChip("Indexados", selected = false, onClick = {})
            LisdataFilterChip("Pendientes", selected = false, onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LisdataCountChip("3 archivos")
            LisdataCountChip("Página 12")
        }
        LisdataSectionHeader("24 documentos", trailing = { Text("Incluir en RAG") })
        LisdataEyebrow("Ingesta local")
        LisdataStepIndicator(step = 1, total = 3)
        LisdataStepIndicator(step = 3, total = 3)
    }
}

@LisdataPreviews
@Composable
private fun ChipsLongTextPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LisdataStatusChip("Un estado con una etiqueta muy larga que debe ajustarse", StatusKind.Error)
        LisdataSectionHeader("Una cabecera de sección muy larga para probar el ajuste", trailing = { Text("Incluir en RAG") })
    }
}

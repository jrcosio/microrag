package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun ScopePreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataScopeChip("Buscar en: Legal y Motores", "Buscar en: Legal y Motores", "Cambiar ámbito", onClick = {})
        LisdataCardGroup {
            LisdataExpandableRow(
                "Motores", ToggleableState.Indeterminate, {}, expanded = true, onExpand = {}, expandLabel = "Mostrar documentos",
                expandedText = "desplegada", collapsedText = "plegada", subtitle = "5 documentos", stateText = "algunos documentos",
            )
            LisdataCheckRow("Manual_motor_V8.pdf", ToggleableState.On, {})
            LisdataCheckRow("Ficha técnica.pdf", ToggleableState.Off, {}, subtitle = "Pendiente de indexar")
            LisdataCheckRow("Notas.txt", ToggleableState.Off, {}, subtitle = "No incluido en RAG", enabled = false)
        }
    }
}

@LisdataPreviews
@Composable
private fun ScopeLongTextPreview() = LisdataPreviewSurface {
    LisdataScopeChip(
        "Buscar en: Contratos de arrendamiento y Manuales de mantenimiento + 12 documentos",
        "Buscar en: …", "Cambiar ámbito", onClick = {},
    )
}

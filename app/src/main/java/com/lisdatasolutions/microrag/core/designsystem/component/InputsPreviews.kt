package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun InputsPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataSearchField(query = "", onQueryChange = {}, placeholder = "Buscar documentos...")
        LisdataSearchField(query = "protocolo", onQueryChange = {}, placeholder = "Buscar documentos...")
        LisdataFilePickerCard(
            title = "Selecciona archivos",
            subtitle = "Todo se procesa en tu móvil",
            buttonText = "Seleccionar archivos",
            onPick = {},
        )
    }
}

@LisdataPreviews
@Composable
private fun InputsLongTextPreview() = LisdataPreviewSurface {
    LisdataSearchField(
        query = "una búsqueda muy larga que no cabe en el campo de texto disponible",
        onQueryChange = {},
        placeholder = "Buscar documentos...",
    )
}

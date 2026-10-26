package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun ConfirmationDialogPreview() = LisdataPreviewSurface {
    LisdataConfirmationDialog(
        title = "¿Eliminar documento?",
        body = "Se borrarán el archivo privado, su texto, sus fragmentos y sus vectores. Esta acción no se puede deshacer.",
        confirmText = "Eliminar",
        onConfirm = {},
        onDismiss = {},
    )
}

@LisdataPreviews
@Composable
private fun ConfirmationWithContentPreview() = LisdataPreviewSurface {
    LisdataConfirmationDialog(
        title = "Descargar modelos",
        body = "",
        confirmText = "Descargar",
        onConfirm = {},
        onDismiss = {},
        destructive = false,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gemma 4 · 2,59 GB")
                Text("EmbeddingGemma 2 · 165 MB")
                Text("Total: 2,75 GB")
            }
        },
    )
}

@LisdataPreviews
@Composable
private fun ModelMissingPreview() = LisdataPreviewSurface {
    LisdataModelMissingBlock(
        title = "Falta el modelo de respuestas",
        body = "Configura Gemma 4 en Preparación para poder consultar tus documentos.",
        actionText = "Ir a configuración",
        onAction = {},
    )
}

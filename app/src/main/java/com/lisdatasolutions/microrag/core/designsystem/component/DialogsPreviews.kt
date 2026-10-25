package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.runtime.Composable
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
private fun ModelMissingPreview() = LisdataPreviewSurface {
    LisdataModelMissingBlock(
        title = "Falta el modelo de respuestas",
        body = "Configura Gemma 4 en Preparación para poder consultar tus documentos.",
        actionText = "Ir a configuración",
        onAction = {},
    )
}

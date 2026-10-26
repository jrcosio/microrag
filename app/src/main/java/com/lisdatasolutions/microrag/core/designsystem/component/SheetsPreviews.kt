package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

@LisdataPreviews
@Composable
private fun SheetContentPreview() = LisdataPreviewSurface {
    LisdataSheetContent {
        Text("Manual técnico.pdf", style = LisdataTheme.typography.material.titleLarge)
        Text("2,4 MB · PDF", style = LisdataTheme.typography.material.bodyMedium, color = LisdataTheme.colors.textSecondary)
        LisdataStatusChip(text = "Archivo cifrado", kind = StatusKind.Error)
        Text("El PDF está protegido con contraseña. Quita la protección e impórtalo de nuevo.")
        LisdataOutlinedButton(text = "Quitar de la cola", onClick = {}, destructive = true, modifier = Modifier.fillMaxWidth())
    }
}

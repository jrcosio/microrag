package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun ButtonsPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataGradientButton("Comenzar", onClick = {}, Modifier.fillMaxWidth(), trailingIcon = LisdataIcons.ChevronRight)
        LisdataGradientButton(
            "Nuevo chat",
            onClick = {},
            trailingIcon = LisdataIcons.ArrowRight,
            trailingIconPlacement = TrailingIconPlacement.AfterText,
        )
        LisdataDarkButton("Pausar indexación", onClick = {}, Modifier.fillMaxWidth(), trailingIcon = LisdataIcons.Pause)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LisdataOutlinedButton("Eliminar", onClick = {}, destructive = true, leadingIcon = LisdataIcons.Trash)
            LisdataOutlinedButton("Reintentar", onClick = {}, leadingIcon = LisdataIcons.Refresh)
        }
    }
}

@LisdataPreviews
@Composable
private fun ButtonsStatesPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataGradientButton("Continuar", onClick = {}, Modifier.fillMaxWidth(), enabled = false)
        LisdataGradientButton("Guardando", onClick = {}, Modifier.fillMaxWidth(), loading = true)
        LisdataGradientButton(
            "Un texto de botón deliberadamente largo para comprobar el ajuste en varias líneas",
            onClick = {},
            Modifier.fillMaxWidth(),
            trailingIcon = LisdataIcons.ChevronRight,
        )
    }
}

@LisdataPreviews
@Composable
private fun IconTilesPreview() = LisdataPreviewSurface {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTileStyle.entries.forEach { style -> LisdataIconTile(LisdataIcons.Sparkle, style = style) }
    }
}

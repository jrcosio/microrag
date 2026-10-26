package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun ProgressRingPreview() = LisdataPreviewSurface {
    LisdataHeroCard(title = null, eyebrow = "Procesamiento local", decoration = false, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LisdataProgressRing(ProgressValue.Determinate(0.68f), label = "Completado", size = 140.dp)
            LisdataProgressRing(ProgressValue.Indeterminate, label = "Completado", size = 140.dp)
        }
    }
}

@LisdataPreviews
@Composable
private fun ProgressCardPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataProgressCard("Generación de embeddings", ProgressValue.Determinate(0.68f), detail = "8.432 / 12.400 fragmentos")
        LisdataProgressCard("Extrayendo texto", ProgressValue.Indeterminate, detail = "Procesando ahora: Manual de procesos.pdf")
    }
}

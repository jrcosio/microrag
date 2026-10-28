package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun HeroCardPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataHeroCard(
            title = "Una respuesta. Todas tus fuentes.",
            eyebrow = "Pregunta a tu biblioteca",
            decorationIcon = LisdataIcons.Sparkle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LisdataGradientButton("Nuevo chat", onClick = {}, trailingIcon = LisdataIcons.ArrowRight, trailingIconPlacement = TrailingIconPlacement.AfterText)
        }
        LisdataHeroCard(title = "Tus documentos.", titleAccent = "Tu idioma.", decoration = false, modifier = Modifier.fillMaxWidth())
    }
}

@LisdataPreviews
@Composable
private fun InfoAndStatCardsPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataInfoCard(
            icon = LisdataIcons.ShieldCheck,
            title = "Tus documentos nunca salen de este dispositivo.",
            body = "La instalación se verifica localmente.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LisdataStatCard(label = "Páginas", value = "48", modifier = Modifier.weight(1f))
            LisdataStatCard(label = "Fragmentos", value = "860", emphasis = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LisdataQuickActionTile(LisdataIcons.Upload, "Importar", "Añadir archivos", onClick = {}, modifier = Modifier.weight(1f))
            LisdataQuickActionTile(LisdataIcons.Globe, "Idiomas", "Preferencias", onClick = {}, modifier = Modifier.weight(1f))
        }
        LisdataCard(onClick = {}) { Text("Tarjeta genérica con contenido libre") }
    }
}

@LisdataPreviews
@Composable
private fun CardsLongTextPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataInfoCard(
            icon = LisdataIcons.Info,
            title = "Excluir un archivo evita que se use en futuras respuestas, pero no lo elimina de la biblioteca local.",
            body = "Un texto adicional deliberadamente largo para comprobar que la tarjeta crece en altura sin recortar.",
        )
        LisdataQuickActionTile(
            LisdataIcons.Database,
            "Índice local con un título muy largo",
            "Estado del RAG y del procesamiento en segundo plano",
            onClick = {},
        )
    }
}

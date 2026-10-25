package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.CitationUi
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StageStatus
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun ModelAndDocumentRowsPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataModelStatusRow(LisdataIcons.Sparkle, "Gemma 4", "Modelo de respuestas", "Por configurar", StatusKind.Warning, onClick = {})
        LisdataModelStatusRow(
            LisdataIcons.Layers, "EmbeddingGemma 2", "Búsqueda semántica", "Descargando", StatusKind.Processing,
            progress = ProgressValue.Determinate(0.42f),
        )
        LisdataDocumentRow("Manual de procesos.pdf", "PDF · 860 fragmentos", included = true, includeEnabled = true, onIncludedChange = {}, onClick = {})
        LisdataDocumentRow("Informe anual.pdf", "PDF · 1.120 fragmentos", included = false, includeEnabled = true, onIncludedChange = {}, onClick = {})
    }
}

@LisdataPreviews
@Composable
private fun RowsErrorPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataModelStatusRow(LisdataIcons.Sparkle, "Gemma 4", "Modelo de respuestas", "Incompatible", StatusKind.Error)
        LisdataDocumentRow(
            "Escaneado sin texto.pdf", "PDF", included = false, includeEnabled = false, onIncludedChange = {}, onClick = {},
            statusText = "Error de indexación", status = StatusKind.Error,
        )
        LisdataQueueRow("Contrato cifrado.pdf", "1,2 MB", statusText = "Archivo cifrado", status = StatusKind.Error, onClick = {})
        LisdataSourceCitation(CitationUi("Protocolo interno.pdf", page = 8, fragment = 41, available = false), onOpen = {})
        LisdataStageRow(LisdataIcons.Database, "Persistencia vectorial", "Error", StageStatus.Failed)
    }
}

@LisdataPreviews
@Composable
private fun QueueCitationStageRowsPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataQueueRow("Manual técnico.pdf", "2,4 MB · Listo para procesar", onClick = {})
        LisdataQueueRow("Notas de reunión.md", "48 KB · Extrayendo", progress = ProgressValue.Indeterminate)
        LisdataSourceCitation(CitationUi("Manual de procesos.pdf", page = 12, fragment = 86), onOpen = {})
        LisdataSourceCitation(CitationUi("Notas.md", page = null, fragment = 3), onOpen = {})
        LisdataStageRow(LisdataIcons.FileText, "Extracción de texto", "Completado", StageStatus.Done)
        LisdataStageRow(LisdataIcons.Cpu, "Generación de embeddings", "En curso", StageStatus.InProgress)
        LisdataStageRow(LisdataIcons.Database, "Persistencia vectorial", "Pendiente", StageStatus.Pending)
    }
}

@LisdataPreviews
@Composable
private fun SettingsSwitchRadioRowsPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataCardGroup {
            LisdataSettingsRow(LisdataIcons.Sparkle, "Modelo de respuestas", subtitle = "Gemma 4", trailing = SettingsTrailing.Chip("Local", StatusKind.Local))
            LisdataDivider()
            LisdataSettingsRow(LisdataIcons.Globe, "Idiomas", subtitle = "Español", onClick = {})
            LisdataDivider()
            LisdataSettingsRow(LisdataIcons.Trash, "Borrar historial de chat", subtitle = "Eliminar conversaciones locales", destructive = true, onClick = {})
        }
        LisdataSwitchRow("Incluir en respuestas", checked = true, onCheckedChange = {}, subtitle = "Disponible para consultas RAG")
        LisdataSwitchRow("Incluir en respuestas", checked = false, onCheckedChange = {}, subtitle = "Disponible cuando termine la indexación", enabled = false)
        LisdataCardGroup {
            LisdataRadioRow("Automático", selected = true, onSelect = {}, subtitle = "Detectar idioma de la pregunta")
            LisdataDivider()
            LisdataRadioRow("Español", selected = false, onSelect = {})
        }
    }
}

@LisdataPreviews
@Composable
private fun RowsLongTextPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataDocumentRow(
            "Un nombre de documento extremadamente largo que no cabe en una sola línea del listado.pdf",
            "PDF · 12.400 fragmentos", included = true, includeEnabled = true, onIncludedChange = {}, onClick = {},
        )
        LisdataSourceCitation(
            CitationUi("Procedimiento de validación final de expedientes con un nombre muy largo.pdf", page = 120, fragment = 1043),
            onOpen = {},
        )
    }
}

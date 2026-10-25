package com.lisdatasolutions.microrag.debug.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataAnswerCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBar
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBarItem
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataChatBubble
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataChatComposer
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCountChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDarkButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDocumentRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataErrorState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEyebrow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataFilePickerCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataFilterChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataHeroCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataIconTile
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataInfoCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataLinearProgress
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataModelMissingBlock
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataModelStatusRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataProgressCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataProgressRing
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataQueueRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataQuickActionTile
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataRadioRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSearchField
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSettingsRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSkeletonRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSourceCitation
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStageRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatusChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStepIndicator
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSwitchRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.component.SettingsTrailing
import com.lisdatasolutions.microrag.core.designsystem.component.TrailingIconPlacement
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataLogo
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StageStatus
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** One component variant rendered by the catalog and by CatalogAccessibilityTest. */
class CatalogEntry(
    val id: String,
    val section: String,
    val title: String,
    val content: @Composable () -> Unit,
)

private val tabs = listOf(
    LisdataBottomBarItem(LisdataIcons.Home, "Inicio"),
    LisdataBottomBarItem(LisdataIcons.Chat, "Chat"),
    LisdataBottomBarItem(LisdataIcons.Folder, "Biblioteca"),
    LisdataBottomBarItem(LisdataIcons.Settings, "Ajustes"),
)

private fun entry(section: String, id: String, title: String, content: @Composable () -> Unit) =
    CatalogEntry(id = "$section/$id", section = section, title = title, content = content)

@Composable
private fun Stack(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space3)) { content() }
}

/** Every component of INVENTARIO.md §4 with its variants (FR-011, SC-001). */
val catalogEntries: List<CatalogEntry> = listOf(
    // Brand
    entry("Marca", "logo", "LisdataLogo") { LisdataLogo(height = 40.dp) },
    entry("Marca", "icons", "LisdataIcons (Lucide, ISC)") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LisdataIcons.all.forEach { LisdataIcon(it, contentDescription = null, tint = LisdataTheme.colors.brandBlue) }
        }
    },
    entry("Marca", "iconTiles", "LisdataIconTile · estilos") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTileStyle.entries.forEach { LisdataIconTile(LisdataIcons.Sparkle, style = it) }
        }
    },
    // Bars
    entry("Barras", "topBar", "LisdataTopBar") {
        Stack {
            LisdataTopBar(title = "Importar documentos", onBack = {})
            LisdataTopBar(
                title = "Chat con documentos",
                onBack = {},
                subtitle = { LisdataStatusChip("Gemma 4 · Procesamiento local", StatusKind.Local) },
                actions = { IconButton(onClick = {}) { LisdataIcon(LisdataIcons.MoreHorizontal, "Más opciones") } },
            )
            LisdataTopBar(title = "Una barra superior con un título deliberadamente largo para probar el ajuste", onBack = {})
        }
    },
    entry("Barras", "bottomBar", "LisdataBottomBar") {
        var selected by remember { mutableStateOf(0) }
        LisdataBottomBar(items = tabs, selectedIndex = selected, onSelect = { selected = it })
    },
    // Buttons
    entry("Botones", "gradient", "LisdataGradientButton") {
        Stack {
            LisdataGradientButton("Comenzar", onClick = {}, Modifier.fillMaxWidth(), trailingIcon = LisdataIcons.ChevronRight)
            LisdataGradientButton(
                "Nuevo chat", onClick = {}, trailingIcon = LisdataIcons.ArrowRight,
                trailingIconPlacement = TrailingIconPlacement.AfterText,
            )
            LisdataGradientButton("Continuar", onClick = {}, Modifier.fillMaxWidth(), enabled = false)
            LisdataGradientButton("Guardando", onClick = {}, Modifier.fillMaxWidth(), loading = true)
        }
    },
    entry("Botones", "dark", "LisdataDarkButton") {
        LisdataDarkButton("Pausar indexación", onClick = {}, Modifier.fillMaxWidth(), trailingIcon = LisdataIcons.Pause)
    },
    entry("Botones", "outlined", "LisdataOutlinedButton") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LisdataOutlinedButton("Eliminar", onClick = {}, destructive = true, leadingIcon = LisdataIcons.Trash)
            LisdataOutlinedButton("Reintentar", onClick = {}, leadingIcon = LisdataIcons.Refresh)
        }
    },
    // Cards
    entry("Tarjetas", "card", "LisdataCard") {
        LisdataCard(onClick = {}) { Text("Tarjeta genérica", color = LisdataTheme.colors.textPrimary) }
    },
    entry("Tarjetas", "hero", "LisdataHeroCard") {
        Stack {
            LisdataHeroCard(title = "Una respuesta. Todas tus fuentes.", eyebrow = "Pregunta a tu biblioteca", modifier = Modifier.fillMaxWidth()) {
                LisdataGradientButton(
                    "Nuevo chat", onClick = {}, trailingIcon = LisdataIcons.ArrowRight,
                    trailingIconPlacement = TrailingIconPlacement.AfterText,
                )
            }
            LisdataHeroCard(title = "Tus documentos.", titleAccent = "Tu idioma.", decoration = false, modifier = Modifier.fillMaxWidth())
        }
    },
    entry("Tarjetas", "info", "LisdataInfoCard") {
        LisdataInfoCard(LisdataIcons.ShieldCheck, "Tus documentos nunca salen de este dispositivo.", body = "La instalación se verifica localmente.")
    },
    entry("Tarjetas", "stat", "LisdataStatCard") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LisdataStatCard("Páginas", "48", Modifier.weight(1f))
            LisdataStatCard("Fragmentos", "860", Modifier.weight(1f), emphasis = true)
        }
    },
    entry("Tarjetas", "quickAction", "LisdataQuickActionTile") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LisdataQuickActionTile(LisdataIcons.Upload, "Importar", "Añadir archivos", onClick = {}, modifier = Modifier.weight(1f))
            LisdataQuickActionTile(LisdataIcons.Database, "Índice local", "Estado del RAG", onClick = {}, modifier = Modifier.weight(1f))
        }
    },
    // Chips
    entry("Chips", "status", "LisdataStatusChip · todos los estados") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LisdataStatusChip("IA local disponible", StatusKind.Ready)
            LisdataStatusChip("Offline", StatusKind.Local)
            LisdataStatusChip("En curso", StatusKind.Processing)
            LisdataStatusChip("En cola", StatusKind.Queued)
            LisdataStatusChip("Pausado", StatusKind.Paused)
            LisdataStatusChip("Por configurar", StatusKind.Warning)
            LisdataStatusChip("Error de indexación", StatusKind.Error)
            LisdataStatusChip("Información", StatusKind.Info)
        }
    },
    entry("Chips", "statusOnDark", "LisdataStatusChip · sobre fondo oscuro") {
        Column(Modifier.background(LisdataTheme.gradients.welcome).padding(16.dp)) {
            LisdataStatusChip("100% en tu dispositivo", StatusKind.Local, onDark = true)
        }
    },
    entry("Chips", "filter", "LisdataFilterChip") {
        var selected by remember { mutableStateOf(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todos", "Indexados", "Pendientes").forEachIndexed { index, label ->
                LisdataFilterChip(label, selected = selected == index, onClick = { selected = index })
            }
        }
    },
    entry("Chips", "count", "LisdataCountChip") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LisdataCountChip("3 archivos")
            LisdataCountChip("2 fuentes")
            LisdataCountChip("Página 12")
        }
    },
    entry("Chips", "headers", "LisdataEyebrow · LisdataSectionHeader") {
        Stack {
            LisdataEyebrow("Ingesta local")
            LisdataSectionHeader("24 documentos", trailing = { Text("Incluir en RAG", color = LisdataTheme.colors.textSecondary) })
        }
    },
    entry("Chips", "step", "LisdataStepIndicator") {
        Stack {
            LisdataStepIndicator(step = 1, total = 3)
            LisdataStepIndicator(step = 2, total = 3)
        }
    },
    // Rows
    entry("Filas", "model", "LisdataModelStatusRow") {
        Stack {
            LisdataModelStatusRow(LisdataIcons.Sparkle, "Gemma 4", "Modelo de respuestas", "Por configurar", StatusKind.Warning, onClick = {})
            LisdataModelStatusRow(
                LisdataIcons.Layers, "EmbeddingGemma 2", "Búsqueda semántica", "Descargando 42 %", StatusKind.Processing,
                progress = ProgressValue.Determinate(0.42f),
            )
            LisdataModelStatusRow(LisdataIcons.Layers, "EmbeddingGemma 2", "Búsqueda semántica", "Verificando", StatusKind.Processing, progress = ProgressValue.Indeterminate)
            LisdataModelStatusRow(LisdataIcons.Sparkle, "Gemma 4", "Modelo de respuestas", "Incompatible", StatusKind.Error)
        }
    },
    entry("Filas", "document", "LisdataDocumentRow") {
        var included by remember { mutableStateOf(true) }
        Stack {
            LisdataDocumentRow(SampleData.DOCUMENT, "PDF · 860 fragmentos", included, true, { included = it }, onClick = {})
            LisdataDocumentRow("Informe anual.pdf", "PDF · 1.120 fragmentos", false, true, {}, onClick = {})
            LisdataDocumentRow("Notas del proyecto.md", "MD", false, false, {}, onClick = {}, statusText = "En cola", status = StatusKind.Queued)
            LisdataDocumentRow("Escaneado.pdf", "PDF", false, false, {}, onClick = {}, statusText = "Error de indexación", status = StatusKind.Error)
            LisdataDocumentRow(SampleData.LONG_DOCUMENT, "PDF · 12.400 fragmentos", true, true, {}, onClick = {})
        }
    },
    entry("Filas", "queue", "LisdataQueueRow") {
        Stack {
            LisdataQueueRow("Manual técnico.pdf", "2,4 MB · Listo para procesar", onClick = {})
            LisdataQueueRow("Notas de reunión.md", "48 KB · Extrayendo", progress = ProgressValue.Indeterminate)
            LisdataQueueRow("Contrato.pdf", "1,2 MB", statusText = "Archivo cifrado", status = StatusKind.Error, onClick = {})
        }
    },
    entry("Filas", "citation", "LisdataSourceCitation") {
        Stack {
            LisdataSourceCitation(SampleData.citation, onOpen = {})
            LisdataSourceCitation(SampleData.citationWithoutPage, onOpen = {})
            LisdataSourceCitation(SampleData.deletedCitation, onOpen = {})
        }
    },
    entry("Filas", "settings", "LisdataSettingsRow · LisdataCardGroup") {
        LisdataCardGroup {
            LisdataSettingsRow(LisdataIcons.Sparkle, "Modelo de respuestas", subtitle = "Gemma 4", trailing = SettingsTrailing.Chip("Local", StatusKind.Local))
            LisdataDivider()
            LisdataSettingsRow(LisdataIcons.Globe, "Idiomas", subtitle = "Español", onClick = {})
            LisdataDivider()
            LisdataSettingsRow(
                LisdataIcons.ShieldCheck, "Procesamiento 100% local", subtitle = "Sin enviar documentos a la nube",
                trailing = SettingsTrailing.Chip("Activo", StatusKind.Local), iconTint = LisdataTheme.colors.success,
            )
            LisdataDivider()
            LisdataSettingsRow(LisdataIcons.Trash, "Borrar historial de chat", subtitle = "Eliminar conversaciones locales", destructive = true, onClick = {})
        }
    },
    entry("Filas", "switch", "LisdataSwitchRow") {
        var checked by remember { mutableStateOf(true) }
        Stack {
            LisdataSwitchRow("Incluir en respuestas", checked, { checked = it }, subtitle = "Disponible para consultas RAG")
            LisdataSwitchRow("Incluir en respuestas", false, {}, subtitle = "Disponible al terminar la indexación", enabled = false)
        }
    },
    entry("Filas", "radio", "LisdataRadioRow") {
        var selected by remember { mutableStateOf(0) }
        LisdataCardGroup {
            LisdataRadioRow("Automático", selected == 0, { selected = 0 }, subtitle = "Detectar idioma de la pregunta")
            LisdataDivider()
            LisdataRadioRow("Español", selected == 1, { selected = 1 })
        }
    },
    entry("Filas", "stage", "LisdataStageRow") {
        Stack {
            LisdataStageRow(LisdataIcons.FileText, "Extracción de texto", "Completado", StageStatus.Done)
            LisdataStageRow(LisdataIcons.Cpu, "Generación de embeddings", "En curso", StageStatus.InProgress)
            LisdataStageRow(LisdataIcons.Database, "Persistencia vectorial", "Pendiente", StageStatus.Pending)
            LisdataStageRow(LisdataIcons.Database, "Persistencia vectorial", "Error", StageStatus.Failed)
        }
    },
    // Progress
    entry("Progreso", "ring", "LisdataProgressRing") {
        LisdataHeroCard(title = "8.432 / 12.400 fragmentos", eyebrow = "Procesamiento local", decoration = false, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LisdataProgressRing(ProgressValue.Determinate(0.68f), label = "Completado", size = 140.dp)
                LisdataProgressRing(ProgressValue.Indeterminate, label = "Completado", size = 140.dp)
            }
        }
    },
    entry("Progreso", "card", "LisdataProgressCard · LisdataLinearProgress") {
        Stack {
            LisdataProgressCard("Generación de embeddings", ProgressValue.Determinate(0.68f), detail = "8.432 / 12.400 fragmentos")
            LisdataProgressCard("Extrayendo texto", ProgressValue.Indeterminate, detail = "Procesando ahora: ${SampleData.DOCUMENT}")
            LisdataLinearProgress(ProgressValue.Determinate(0.3f))
        }
    },
    // Chat
    entry("Chat", "bubble", "LisdataChatBubble") { LisdataChatBubble(SampleData.QUESTION) },
    entry("Chat", "answer", "LisdataAnswerCard · estados") {
        Stack {
            LisdataAnswerCard(SampleData.ANSWER, AnswerState.Completed, onCopy = {}, sourcesCountText = "2 fuentes", onSources = {})
            LisdataAnswerCard("", AnswerState.Streaming, onCopy = {}, statusText = "Buscando en documentos...")
            LisdataAnswerCard("El protocolo establece", AnswerState.Streaming, onCopy = {}, statusText = "Generando respuesta...")
            LisdataAnswerCard("El protocolo establece tres", AnswerState.Cancelled, onCopy = {})
            LisdataAnswerCard("", AnswerState.Failed, onCopy = {}, onRetry = {})
        }
    },
    entry("Chat", "composer", "LisdataChatComposer") {
        var text by remember { mutableStateOf("") }
        Stack {
            LisdataChatComposer(text, { text = it }, onSend = { text = "" }, placeholder = "Pregunta a tus documentos...")
            LisdataChatComposer("¿Y para empresas?", {}, onSend = {}, placeholder = "Pregunta a tus documentos...", generating = true)
        }
    },
    // Inputs
    entry("Entradas", "search", "LisdataSearchField") {
        var query by remember { mutableStateOf("") }
        LisdataSearchField(query, { query = it }, placeholder = "Buscar documentos...")
    },
    entry("Entradas", "filePicker", "LisdataFilePickerCard") {
        LisdataFilePickerCard("Selecciona archivos", "Todo se procesa en tu móvil", "Seleccionar archivos", onPick = {})
    },
    // States
    entry("Estados", "empty", "LisdataEmptyState") {
        LisdataEmptyState(
            LisdataIcons.Folder, "Aún no tienes documentos", "Importa PDF, TXT o Markdown para consultarlos.",
            action = { LisdataGradientButton("Importar documentos", onClick = {}) },
        )
    },
    entry("Estados", "error", "LisdataErrorState") {
        LisdataErrorState("No se pudo cargar el resumen de la biblioteca.", onRetry = {})
    },
    entry("Estados", "skeleton", "LisdataSkeletonRow") { Stack { LisdataSkeletonRow(); LisdataSkeletonRow(lines = 1) } },
    entry("Estados", "modelMissing", "LisdataModelMissingBlock") {
        LisdataModelMissingBlock("Falta el modelo de respuestas", "Configura Gemma 4 para consultar tus documentos.", "Ir a configuración", onAction = {})
    },
    entry("Estados", "dialog", "LisdataConfirmationDialog") {
        var open by remember { mutableStateOf(false) }
        LisdataOutlinedButton("Abrir diálogo de confirmación", onClick = { open = true }, destructive = true)
        if (open) {
            LisdataConfirmationDialog(
                title = "¿Eliminar documento?",
                body = "Se borrarán el archivo privado, su texto, sus fragmentos y sus vectores.",
                confirmText = "Eliminar",
                onConfirm = { open = false },
                onDismiss = { open = false },
            )
        }
    },
)

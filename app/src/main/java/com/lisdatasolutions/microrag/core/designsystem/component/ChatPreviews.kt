package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

/** The answer of PNG 04, formatted as the chat does: an intro, bullets and citation chips. */
private val SAMPLE_ANSWER = AnswerContent(
    listOf(
        AnswerBlock.Paragraph(listOf(AnswerSpan.Text("El protocolo establece tres comprobaciones antes del cierre:"))),
        AnswerBlock.Bullet(listOf(AnswerSpan.Text("Verificar la documentación "), AnswerSpan.Citation(1))),
        AnswerBlock.Bullet(listOf(AnswerSpan.Text("Confirmar las aprobaciones "), AnswerSpan.Citation(2))),
        AnswerBlock.Bullet(listOf(AnswerSpan.Text("Registrar la validación final "), AnswerSpan.Citation(2))),
    ),
)

@LisdataPreviews
@Composable
private fun ChatPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataChatBubble("¿Qué indica el protocolo sobre la validación final?")
        LisdataAnswerCard(SAMPLE_ANSWER, AnswerState.Completed, onCopy = {}, sourcesCountText = "2 fuentes", onSources = {})
        LisdataChatComposer(value = "", onValueChange = {}, onSend = {}, placeholder = "Pregunta a tus documentos...")
    }
}

@LisdataPreviews
@Composable
private fun ChatStatesPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataAnswerCard(AnswerContent.Empty, AnswerState.Streaming, onCopy = {}, statusText = "Buscando en documentos...")
        LisdataAnswerCard(AnswerContent.plain("El protocolo establece"), AnswerState.Streaming, onCopy = {}, statusText = "Generando respuesta...")
        LisdataAnswerCard(AnswerContent.plain("El protocolo establece tres"), AnswerState.Cancelled, onCopy = {})
        LisdataAnswerCard(AnswerContent.Empty, AnswerState.Failed, onCopy = {}, onRetry = {}, failureText = "La respuesta se interrumpió")
        LisdataChatComposer(
            value = "¿Y para empresas?",
            onValueChange = {},
            onSend = {},
            placeholder = "Pregunta a tus documentos...",
            generating = true,
        )
    }
}

@LisdataPreviews
@Composable
private fun ChatLongTextPreview() = LisdataPreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LisdataChatBubble(
            "Una pregunta muy larga sobre los plazos de devolución, las condiciones para empresas y " +
                "las excepciones que aplican a los productos personalizados según el manual de garantías.",
        )
        LisdataAnswerCard(AnswerContent(List(3) { SAMPLE_ANSWER.blocks }.flatten()), AnswerState.Completed, onCopy = {}, sourcesCountText = "6 fuentes")
    }
}

package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

private const val SAMPLE_ANSWER =
    "El protocolo establece tres comprobaciones antes del cierre: verificar la documentación, " +
        "confirmar las aprobaciones y registrar la validación final [1][2]."

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
        LisdataAnswerCard("", AnswerState.Streaming, onCopy = {}, statusText = "Buscando en documentos...")
        LisdataAnswerCard("El protocolo establece", AnswerState.Streaming, onCopy = {}, statusText = "Generando respuesta...")
        LisdataAnswerCard("El protocolo establece tres", AnswerState.Cancelled, onCopy = {})
        LisdataAnswerCard("", AnswerState.Failed, onCopy = {}, onRetry = {})
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
        LisdataAnswerCard(SAMPLE_ANSWER.repeat(3), AnswerState.Completed, onCopy = {}, sourcesCountText = "6 fuentes")
    }
}

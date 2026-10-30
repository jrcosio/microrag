package com.lisdatasolutions.microrag.debug.catalog

import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan
import com.lisdatasolutions.microrag.core.designsystem.model.CitationUi

/**
 * Sample data for the debug catalog only. These are mockup examples, never real results
 * (README: "Las cantidades, documentos y respuestas del prototipo son datos de ejemplo").
 */
internal object SampleData {
    const val BANNER = "Datos de ejemplo — no son resultados reales"
    const val DOCUMENT = "Manual de procesos.pdf"
    const val LONG_DOCUMENT = "Procedimiento de validación final de expedientes con un nombre deliberadamente largo.pdf"
    const val QUESTION = "¿Qué indica el protocolo sobre la validación final?"
    /** The answer of PNG 04 as the chat formats it: an intro, three bullets and two citation chips. */
    val answer = AnswerContent(
        listOf(
            AnswerBlock.Paragraph(listOf(AnswerSpan.Text("El protocolo establece tres comprobaciones antes del cierre:"))),
            AnswerBlock.Bullet(listOf(AnswerSpan.Text("Verificar la documentación "), AnswerSpan.Citation(1))),
            AnswerBlock.Bullet(listOf(AnswerSpan.Text("Confirmar las aprobaciones "), AnswerSpan.Citation(2))),
            AnswerBlock.Bullet(listOf(AnswerSpan.Text("Registrar la validación final", bold = true), AnswerSpan.Text(" "), AnswerSpan.Citation(2))),
        ),
    )
    val citation = CitationUi(DOCUMENT, page = 12, fragment = 86)
    val citationWithoutPage = CitationUi("Notas de reunión.md", page = null, fragment = 3)
    val deletedCitation = CitationUi("Protocolo interno.pdf", page = 8, fragment = 41, available = false)
    val blockCitation = CitationUi(DOCUMENT, page = 12, fragment = 86, lastFragment = 87)
}

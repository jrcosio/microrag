package com.lisdatasolutions.microrag.debug.catalog

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
    const val ANSWER = "El protocolo establece tres comprobaciones antes del cierre: verificar la documentación, " +
        "confirmar las aprobaciones y registrar la validación final [1][2]."
    val citation = CitationUi(DOCUMENT, page = 12, fragment = 86)
    val citationWithoutPage = CitationUi("Notas de reunión.md", page = null, fragment = 3)
    val deletedCitation = CitationUi("Protocolo interno.pdf", page = 8, fragment = 41, available = false)
}

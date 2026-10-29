# Contrato: diagnóstico de búsqueda (F10, solo debug)

Herramienta interna del lanzador «MicroRAG Debug» (F02): es la UI de diagnóstico que permite la constitución para features técnicas. No es UI de producto y no tiene PNG.

## Acceso

- **Entrada nueva** en `debugEntries` (`src/debug/.../DebugEntries.kt`): `DebugEntry("Diagnóstico de búsqueda") { onBack -> RetrievalDiagnosticScreen(onBack) }`.
- **Koin:** `retrievalDiagnosticModule` (`viewModel { RetrievalDiagnosticViewModel(get()) }`) se añade a `debugModules`. El stub de release sigue vacío.

## Estado

```kotlin
data class RetrievalDiagnosticUiState(
    val question: String = "",
    val previousQuestion: String = "",
    val status: Status = Status.Idle,
) {
    sealed interface Status {
        data object Idle : Status
        data object Searching : Status
        data class Done(val result: RetrievalResult) : Status
    }

    val canSearch: Boolean get() = status != Status.Searching
}
```

## Eventos

- `onQuestionChange(text)` y `onPreviousChange(text)`.
- `onSearch()`:
  1. pasa a `Searching`;
  2. llama a `RetrieveContextUseCase(question, previousQuestion.ifBlank { null })`;
  3. pasa a `Done(result)`.

  Una segunda pulsación durante la búsqueda se ignora.

## Contenido (de arriba abajo)

1. **Barra:** `LisdataTopBar` «Diagnóstico de búsqueda», con volver.
2. **Campos:**
   - «Pregunta»;
   - «Pregunta anterior (opcional)»;
   - botón «Buscar», desactivado durante la búsqueda, con un indicador indeterminado.
3. **Resumen**, con `Done`:
   - «Seguimiento: sí/no»;
   - «Texto de búsqueda: …»;
   - «Recortes del embedding: N», solo si N > 0;
   - **resultado:**
     - con bloques: «N bloques · M tokens»;
     - si no, el estado en palabras: «Sin resultados», «Pregunta vacía», «Índice no disponible: <estado>», «Modelos no listos» o «Fallo del embedding».
4. **Tiempos:**
   - embedding, vector, léxica, selección y total, en ms con un decimal;
   - «motor cargado: sí/no» (`coldEngine`).
5. **Bloques**, en su orden:
   - documento;
   - ruta de encabezados;
   - página;
   - ordinales (`3–4`);
   - ids;
   - tokens;
   - el texto completo.
6. **Candidatos** (los 30 fusionados), una fila cada uno:
   - rango de fusión;
   - id;
   - puntuación RRF (4 decimales);
   - rango y distancia vectorial (3 decimales);
   - rango léxico, o «—» si no aparece en esa lista;
   - marca «elegido».

El contenido se desplaza en vertical (`LazyColumn`). Todos los textos se pueden seleccionar para copiarlos.

## Reglas

- **V1 — Solo debug:** el código vive en `src/debug/java/.../debug/retrieval`. Ningún fichero de `src/main` ni de `src/release` lo referencia (prueba en `LayeringTest`). El APK de release no contiene `debug.retrieval` (`apkanalyzer`, al cierre).
- **V2 — Datos reales:** busca en la biblioteca del teléfono con el `ContextRetriever` de la app. No hay datos de ejemplo fuera de las previews (constitución V).
- **V3 — Texto en pantalla, nunca en registros:** el ViewModel y la pantalla no registran nada. El registro de la recuperación solo lleva cifras (regla Q11).
- **V4 — Fuera del hilo principal:** la búsqueda corre en los dispatchers de la base de datos y de embeddings (constitución IV). El ViewModel solo lanza la corrutina.
- **V5 — Design system:** componentes `Lisdata*` y tokens, sin hex. Textos en Kotlin, como el diagnóstico de F02 (herramienta interna).
- **V6 — Accesibilidad básica:**
  - botón y campos con etiqueta;
  - objetivos ≥ 48 dp;
  - el indicador de búsqueda se anuncia como «Buscando».

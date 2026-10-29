# Research: Recuperación híbrida (F10)

Fecha: 2026-10-29. Todas las decisiones parten del código actual (F01–F09), de la spec aclarada (D-049) y de la referencia de escritorio de F05 (`offline/eval/`). No hay dependencias nuevas.

**Orden de trabajo** (petición del usuario, 2026-10-29): el teléfono no estará disponible hasta más tarde. Todo lo que no lo necesita va primero:
- el código;
- las pruebas JVM;
- las pruebas instrumentadas en el emulador;
- el trabajo en el escritorio.

Las pruebas en el teléfono de referencia (R10, R11 y la decisión de INT8 de R13) forman la última fase.

## R1. Dónde vive cada pieza

**Decisión:** cuatro paquetes con una responsabilidad cada uno (constitución II):

| Paquete | Contenido | Por qué ahí |
|---|---|---|
| `rag/retrieval` | `FollowUpRule`, `ReciprocalRankFusion`, `ContextSelector` (con la fusión de consecutivos) | Algoritmos puros portados de Python, como `rag/text/SpanishText` y `rag/ingest/Chunker`. Se prueban en la JVM contra los casos y el golden del escritorio (R2, R3) |
| `domain/retrieval` | Modelos del resultado, interfaz `ContextRetriever` y `RetrieveContextUseCase` | El contrato que verán el chat (F11) y el diagnóstico. No importa LiteRT ni SQLite |
| `data/retrieval` | `HybridRetriever` (orquestación) y `QueryEmbedder` (motor de consulta y estado del modelo) | Usa `RagIndex`, `EngineCoordinator`, `EngineFactory` y `ModelFileRepository`, como `ChunkEmbeddingSession` en `data/indexing` |
| `debug/retrieval` (solo `src/debug`) | Pantalla, ViewModel y módulo Koin del diagnóstico | Las herramientas internas viven en el lanzador «MicroRAG Debug» (F02) |

**Alternativas descartadas:**
- **Todo en `domain`:** el orquestador necesita el motor y el repositorio de modelos, que son de `data` y `rag/runtime`.
- **`HybridRetriever` en `rag/`:** tendría que importar `data.models` para saber si el modelo está listo.
- **Los algoritmos dentro de `HybridRetriever`:** no se podrían probar contra el golden sin índice ni motor.

## R2. Port exacto de la referencia de escritorio

**Decisión:** port literal de `offline/eval/retrieval.py` y `selector.py`, que a su vez portan el informe (L638–727).

- **`FollowUpRule.isFollowUp(question)`:**
  - devuelve `true` si `SpanishText.tokens(question).size < 8`;
  - o si `question.trim().lowercase()` empieza por uno de `«y »`, `«¿y »`, `«pero »`, `«entonces»`, `«¿y si»` o `«y si »`.
- **`FollowUpRule.searchText(question, previous, enabled)`:**
  - devuelve `"$previous $question"` si la regla está activa, hay pregunta anterior (no vacía) y la actual es un seguimiento;
  - en otro caso, solo `question`.
  - Se compara la pregunta tal como llega, sin recortar, igual que Python.
- **`ReciprocalRankFusion.fuse(lists, k)`:**
  - rangos desde 1;
  - suma de `1.0 / (k + rango)` en el orden de las listas: vector primero y léxica después, como Python, para que los `Double` coincidan bit a bit en los empates;
  - orden: puntuación descendente, después mejor rango individual y después id menor.
  - Devuelve la lista completa con la puntuación y el rango en cada lista (para el diagnóstico).
- **`ContextSelector(maxBlocks, maxPerDocument, budgetTokens, expandTop).select(ranked, neighbor)`:**
  - el `tryAdd` del informe;
  - corte en `maxBlocks + expandTop` elegidos;
  - vecino `ordinal + 1` de los `expandTop` primeros;
  - orden por (documento, ordinal);
  - fusión de consecutivos con `mergeWithoutOverlap`;
  - `take(maxBlocks)`.
  - `neighbor` es una función `suspend`, porque carga del índice.
- **`mergeWithoutOverlap(a, b)`:** la de Python (`_merge_without_overlap`). Usa `trimEnd`, `substringAfterLast("\n\n")`, `trimStart` y `trim` de Kotlin, que son los que imita Python con `kt_trim*`.
  - Con prefijo, lo que sigue al párrafo repetido.
  - Sin prefijo, `b` original y recortado.
  - La regla de `PreviewText` (F08) es la misma idea, pero solo devuelve frases de contexto. No se reutiliza su código, porque el bloque necesita el texto completo.
- **Bloque:** además de lo del informe, guarda la lista de ids de los fragmentos que cubre (FR-014). Así F11 cita fragmentos reales (constitución III).
- **Candidatos fusionados:** `RagParameters.FUSED_LIMIT = 30`.
  - Hoy es una constante de Python (`retrieval.FUSED_LIMIT`, y el `[:30]` de `pipeline.retrieve`).
  - Se añade como `fused_limit` a `offline/config/rag_parameters.json`; `RagParametersTest` la compara campo a campo (FR-006).
  - El harness la lee del JSON, y una prueba de Python lo comprueba.

**Pruebas (SC-001):** se portan los casos de `offline/tests/test_retrieval.py` y `test_selector.py` con el mismo resultado:
- RRF a mano;
- empates;
- seguimiento por longitud y por conector;
- texto de búsqueda de «¿Cada cuánto se revisa la bomba? ¿Y el aceite?»;
- pregunta solo con palabras vacías;
- 3 por documento;
- vecino y fusión;
- párrafo repetido;
- presupuesto y límite;
- orden.

Se añaden casos propios: vecino ya elegido, vecino que no cabe, documento borrado a mitad y `expandTop = 0`.

## R3. Golden de recuperación sobre datos reales

**Decisión:** `offline/gen_retrieval_golden.py` escribe `offline/golden/retrieval_golden.jsonl`, con una fila por cada pregunta viva del conjunto de evaluación (426). Cada fila lleva:
- la pregunta y la anterior;
- la marca de seguimiento y el texto de búsqueda;
- las listas vectorial y léxica (ids del índice `ref` de escritorio);
- la fusión;
- los datos de los candidatos que necesita el selector (documento, ordinal, tokens y el id del siguiente);
- los bloques esperados con dos configuraciones:
  - D-034: 4 bloques, sin vecinos, 3 por documento y 1.719 tokens;
  - la del informe: 6 bloques, vecinos de los 2 primeros, 3 por documento y 2.200 tokens, para ejercitar la expansión.

El formato está en data-model §6.

- **`RetrievalGoldenTest` (JVM)** reproduce con el código Kotlin el 100 % de las filas:
  - seguimiento y texto de búsqueda;
  - orden de la fusión;
  - bloques (documento, primer y último ordinal, tokens e ids).
  - No compara el texto de los bloques, que cubren las pruebas de R2: el golden no lleva el contenido de los fragmentos.
- **Determinista:** `--check` lo regenera en memoria y falla si difiere, como los golden de F04–F07.
  - Usa la caché de embeddings de consulta de F05 (`build/query_embeddings.json`), así que no carga el modelo si las preguntas no cambian.
  - Tamaño estimado: unos 550 KB.

**Por qué:** los casos a mano cubren las reglas, y el golden cubre los empates y los límites que aparecen con datos reales. Si el port diverge del escritorio, la paridad del teléfono (R11) fallaría sin decir dónde; el golden lo localiza en la JVM.

**Alternativa descartada:** solo los casos portados. SC-001 se cumpliría, pero la primera señal de divergencia llegaría con el teléfono, al final del todo.

## R4. Embedding de la consulta y estado del modelo

**Decisión:** `QueryEmbedder` en `data/retrieval`:

- **Motor:**
  - `EngineKey(EG2_270M, CPU)` (D-024) a través de `EngineCoordinator.withEngine`, creado con `EngineFactory.embedder(...).initializeOrClose { it.initialize() }`;
  - se carga bajo demanda y queda cargado hasta `releaseUnheld` (memoria o UI oculta), como el resto de motores (FR-021);
  - nunca usa la GPU, así que nunca compite con el LLM por ella (constitución IV).
- **Listo (FR-018, regla de F03):** `states[EG2_270M] is Verified && EG2_270M in preparedBackends`.
  - En un proceso recién arrancado el repositorio aún no ha leído la carpeta. Si `pathOf` da `null`, se llama una vez a `refresh()` (sin calcular huellas, F03 SC-007), como hace `ChunkEmbeddingSession`, y se vuelve a mirar.
  - Si no está listo, «modelos no listos» sin cargar nada.
- **Entrada demasiado larga** (caso límite de la spec): nuevo `TextEmbedder.embedQueryFitting(question, previous): QueryEmbedding`:
  - **implementación por defecto:** une y llama a `embedQuery`, sin recortes;
  - **`LiteRtTextEmbedder`:** si LiteRT rechaza la entrada por longitud (el mismo mensaje que usa `embedDocumentFitting`, F07 R6), aplica la regla pura `QueryOverflow.next(previous, question)`:
    1. mientras haya pregunta anterior, se recorta con `EmbeddingOverflow.cut` (90 % por un espacio, D-036); si queda vacía, se quita;
    2. sin anterior, se recorta la actual con la misma regla;
    3. como mucho `EmbeddingOverflow.MAX_ATTEMPTS` intentos.
  - Después se lanza `EmbeddingInputTooLongException`, y cualquier otro fallo como `EmbeddingEngineException`. Así ningún tipo de LiteRT sale de `rag/runtime/litert` (`LayeringTest`).
  - Solo se recorta lo que se embebe. La búsqueda léxica recibe el texto de búsqueda completo, del que ya usa como mucho 16 términos (F04).
  - El resultado indica cuántos recortes hubo, para el diagnóstico.
  - **Por qué recortar antes la anterior:** la pregunta actual es lo que se responde. La anterior solo aporta el tema (spec, casos límite).
- **Fallos:** cualquier excepción del motor (carga o cálculo) se convierte en «fallo del embedding». No se degrada en silencio a solo búsqueda léxica (spec, Assumptions).

**Verificado en el código:**
- `LiteRtTextEmbedder.embedQuery` hoy deja escapar `LiteRtLmJniException`. `ModelCompatibilityChecker` y el diagnóstico de F02 la capturan como `Exception`, así que el cambio no les afecta.
- El prefijo es `EmbeddingPrefixes.query`, con `trim()` (constitución III).

**Alternativas descartadas:**
- **Recortar por caracteres antes de llamar al modelo:** el límite es en tokens y depende del texto. La regla D-036 ya se validó en el teléfono (F07).
- **Usar la GPU si la ingesta no la usa:** D-024 midió que no acelera la consulta (p95 124–141 ms frente a 120 ms) y competiría con el LLM en F11.

## R5. Búsqueda durante una tanda de indexación: motores de embeddings compartidos

**Problema:**
- La ingesta retiene su motor durante toda la tanda (`withHeldEngine`, F07 R4), y el uso de un motor es exclusivo (un `Mutex` por entrada).
- Si la tanda va en CPU (emulador, o un teléfono que no pasa la comprobación de la GPU), la consulta pide la misma clave `(EG2_270M, CPU)` y esperaría al final de la tanda (incumple FR-020).
- Si la tanda va en GPU, la consulta usa otra clave (CPU) y no espera al motor. Los dos comparten el hilo `microrag-embed`, así que la consulta espera, como mucho, al fragmento en curso: unos 355 ms en GPU (F02).

**Decisión:** el `EngineCoordinator` permite el **uso compartido** de los motores de embeddings; el LLM sigue siendo exclusivo.
- **Por qué es seguro:** `LiteRtTextEmbedder` ejecuta cada llamada en `AppDispatchers.embeddings`, un único hilo. Dos usuarios del mismo motor nunca llaman a la vez al código nativo. El ejecutor es FIFO, así que la consulta se calcula justo después del fragmento en curso (el caso límite de la spec).
- **Cambios en la entrada del coordinador:**
  - un recuento de usuarios en lugar del `Mutex` en los motores compartibles;
  - un recuento de retenciones en lugar del `Boolean held`: la consulta que termina no debe quitar la retención de la ingesta;
  - `closeLocked` espera a que no quede ningún usuario;
  - `otherGpuInUse` mira el recuento.
- El LLM conserva el `Mutex`: su conversación tiene estado y no se comparte.
- **Reglas que no cambian** (F07 R4):
  - nunca dos motores en GPU;
  - nadie espera a otro bloque con el `loadLock` tomado;
  - `releaseUnheld` no cierra un motor retenido;
  - un motor nunca se cierra mientras un bloque lo usa.
- **Coste esperado en una tanda en CPU:** fragmento en curso (p50 874 ms, F02) + consulta (120 ms) + búsquedas. Puede pasar de 1 s. SC-007 se mide en el teléfono de referencia, que indexa en GPU (F07). El caso de la CPU se mide también y se informa (R10, riesgo 4).
- **Fin de la tanda:** `ChunkEmbeddingSession` libera su clave al terminar. Si era la CPU, cierra también el motor que usaba la consulta (cuando esta acaba), y la siguiente lo vuelve a cargar (≈ 271 ms con caché, F02).
  - Pasa una vez por tanda.
  - La comprobación de GPU de F07 (`gpuGivesTheCpuVectors`) hace lo mismo una vez por versión de la app.

**Alternativas descartadas:**
- **Un segundo motor de CPU solo para consultas:** duplica la memoria del modelo durante la tanda y no gana tiempo, porque comparte el hilo de embeddings.
- **Que la ingesta tome el motor fragmento a fragmento:** la consulta competiría con `tryLock` contra la ingesta, sin orden garantizado (riesgo de inanición), y cambia el runner de F07.
- **Un dispatcher propio para la consulta:** quitaría la espera del fragmento en curso, pero añade un cuarto hilo nativo que la constitución IV no prevé. Queda como plan B si SC-007 falla en el teléfono (riesgo 4).
- **Una segunda conexión de solo lectura al índice:** la constitución IV fija una sola conexión. La búsqueda espera como mucho al lote de 64 en curso.

## R6. Flujo de una recuperación y sus tiempos

**Decisión:** `HybridRetriever.retrieve(request)`:

1. **Pregunta vacía o solo espacios:** «pregunta vacía», sin abrir el índice ni cargar el motor.
2. **Seguimiento y texto de búsqueda** (R2).
3. **`index.open()`:** si no es `Compatible`, «índice no disponible» con el estado, sin cargar el motor (FR-017).
4. **Modelo listo** (R4); si no, «modelos no listos» (FR-018).
5. **En paralelo:**
   - la **búsqueda léxica** del texto de búsqueda completo, en el hilo de la base de datos;
   - el **embedding de la consulta**, en el hilo de embeddings.

   La léxica no depende del vector. El informe pone las dos búsquedas en paralelo (diagrama de L599); en la app comparten la conexión única, así que lo que se solapa es la léxica con el embedding.
6. **Búsqueda vectorial** con el vector (top-50, filtrada, D-040).
7. **Selección** (se mide junta):
   1. fusión RRF;
   2. los 30 primeros;
   3. carga **solo de fragmentos buscables** (R7);
   4. selector con los parámetros de `RagParameters`;
   5. vecinos buscables si `expandTop > 0`.
8. **Resultado:**
   - **bloques** si hay al menos uno;
   - **«sin resultados»** si ninguna lista dio candidatos o ninguno se pudo cargar.

**Tiempos (FR-019):**
- `embeddingMs`: incluye la carga del motor si no estaba cargado, y lo indica con `coldEngine`;
- `vectorMs`;
- `textMs`;
- `selectionMs`: fusión, carga y selección;
- `totalMs`: reloj de pared de todo el flujo, con `SystemClock`/`nanoTime` en milisegundos con decimales.

Como la léxica se solapa con el embedding, `totalMs` es menor que la suma de los pasos.

**Errores (FR-016):**
- `IndexUnavailableException` → «índice no disponible» con su estado;
- otra excepción del índice (por ejemplo, de E/S) → «índice no disponible» con `Unreadable("search: <clase>")`;
- fallos del motor → «fallo del embedding»;
- `CancellationException` se propaga siempre.

**Concurrencia (FR-022):** el `HybridRetriever` no guarda estado entre llamadas, así que dos recuperaciones simultáneas no se mezclan. Se prueba con dos llamadas concurrentes y un embedder que bloquea.

## R7. Fragmentos que dejan de ser buscables entre la búsqueda y la carga (FR-015)

**Problema:** `loadChunks` y `neighbor` no filtran por estado. Un documento excluido, borrado o quitado del índice entre la búsqueda y la carga colaría sus fragmentos.

**Decisión:** parámetro nuevo `searchableOnly: Boolean = false` en `RagIndex.loadChunks` y `RagIndex.neighbor`. Con `true`, la consulta añade `AND d.status = 'done' AND d.included = 1`, el mismo filtro que las búsquedas (F07 N7). El valor por defecto mantiene el comportamiento de los llamadores actuales (citas y vista previa de F08).

- **Borrado o quitado del índice:** sus fragmentos ya no existen, y `loadChunks` los omite como hoy.
- **Excluido:** sigue existiendo; el filtro lo quita.
- **Reindexado publicado entre medias:** los ids buscados del original ya no existen (se borran al publicar, F08 R5). Los de la sombra se publican con su id y estado `done`. Como esos ids no estaban en la búsqueda, el resultado tiene menos candidatos, nunca fragmentos de la sombra sin publicar.

**Alternativa descartada:** filtrar en `HybridRetriever` con `document(id)`, una consulta por documento y sin atomicidad con la carga.

## R8. Resultado tipado, registros y privacidad

**Decisión:** `RetrievalResult` con:
- el texto de búsqueda;
- la marca de seguimiento;
- los recortes del embedding;
- los tiempos;
- un `RetrievalOutcome` sellado:
  - `Blocks(blocks, candidates)`;
  - `NoResults`;
  - `EmptyQuestion`;
  - `IndexUnavailable(state)`;
  - `ModelsNotReady`;
  - `EmbeddingFailed`.

`candidates` son los 30 fusionados con su puntuación RRF, su rango y distancia vectorial, su rango léxico y si se eligieron (data-model §3). Son ids y números, para el diagnóstico.

**Registro (FR-030, constitución I):** una línea por recuperación, sin texto:

```
retrieval outcome=Blocks follow_up=true cuts=0 vector=50 text=12 fused=58 blocks=3 tokens=1234 cold=false embed_ms=118.2 vector_ms=22.9 text_ms=19.4 select_ms=3.1 total_ms=146.0
```

Nunca se registran la pregunta, el texto de búsqueda, el texto de los fragmentos ni los nombres de documento. Las pruebas comprueban que el `CapturingLogger` no contiene la pregunta ni el texto de los fragmentos (SC-008).

## R9. Diagnóstico de búsqueda (solo debug)

**Decisión:** entrada «Diagnóstico de búsqueda» en `debugEntries`, con `retrievalDiagnosticModule` en `debugModules` (solo `src/debug`; la variante release tiene el stub vacío de F02).

- **ViewModel:**
  - `RetrievalDiagnosticViewModel(RetrieveContextUseCase)`;
  - estado `Idle | Searching | Done(result)`;
  - campos de pregunta y pregunta anterior;
  - un botón «Buscar», desactivado mientras busca.
- **Pantalla:** `LisdataTopBar`, campos y botón del design system, y el resultado (contrato `retrieval-diagnostic.md`):
  - seguimiento sí/no y texto de búsqueda;
  - recortes;
  - estado sin búsqueda, con su motivo;
  - tiempos, y si el motor estaba cargado;
  - bloques: documento, ruta, página, ordinales, ids, tokens y texto;
  - candidatos: rango de fusión, puntuación RRF, rango y distancia vectorial, rango léxico («—» si no está en esa lista) y marca de elegido.
- **Datos:** usa la biblioteca real del teléfono, por el mismo `RagIndex` de la app. El texto se muestra en pantalla y nunca se registra (spec, Assumptions).
- **Textos:** en Kotlin, como el diagnóstico de F02. Es una herramienta interna, no UI de producto (constitución, Flujo de desarrollo).
- **Release (FR-024, SC-009):**
  - una prueba JVM nueva en `LayeringTest`: ningún fichero de `src/main` ni de `src/release` referencia `debug.retrieval`;
  - además, al cierre, `apkanalyzer dex packages` sobre el APK de release, como en F02 (`checklists/release-verification.md`).

## R10. Medición de latencia en el teléfono

**Decisión:** dos pruebas instrumentadas `@RequiresModels`, en la última fase.

- **`RetrievalBenchmarkTest`** (FR-025, SC-003, SC-004):
  - índice temporal con 10.000 fragmentos sintéticos, como `RagIndexBenchmarkTest` (F04): 100 documentos de 100 fragmentos, texto de un vocabulario fijo y vectores aleatorios unitarios;
  - consultas: las 50 preguntas de la muestra de paridad (R11), leídas de los assets de prueba;
  - motor real (`LiteRtEngineFactory`, CPU) y `HybridRetriever` real;
  - **primera búsqueda con el motor sin cargar:** se mide aparte;
  - **después:** 2 vueltas de las 50 preguntas, que dan p50 y p95 de embedding, vector, léxica, selección y total;
  - **otra vez con 4.300 fragmentos ocultos** (un documento en `EMBEDDING`, F07 R7).
  - Registro `retrieval-benchmark` con el modelo de teléfono y la versión de Android.
  - **Comprobación solo en el A063** (D-022): total p95 ≤ 200 ms y embedding p95 < 150 ms con el motor cargado y sin ocultos. En otros dispositivos solo se registra.
- **`RetrievalDuringIndexingTest`** (FR-020, SC-007):
  - índice temporal con 1.000 fragmentos sintéticos publicados;
  - en paralelo, `IngestRunner` con `ChunkEmbeddingSession` real indexa `pdf/text_200_pages.pdf` (fixture de F07);
  - mientras dura la fase de embedding, una recuperación cada 500 ms con las preguntas de la muestra;
  - **en el A063, con la GPU que valida F07:** p95 del total ≤ 1 s, sin que ninguna espere al final de la tanda (cada una termina antes que la tanda);
  - **variante con la tanda forzada a CPU** (`isEmulator = true` en la sesión): se mide y se informa, sin umbral (R5).

`Percentiles` (debug, F02) calcula los percentiles. Las dos pruebas usan el modelo instalado del teléfono, nunca lo descargan ni lo borran (reglas del usuario).

**Fuera del teléfono:** las mismas pruebas pasan en el emulador en CPU (sin umbral). Sirven para comprobar que funcionan antes de la fase final.

## R11. Paridad con el escritorio (SC-005)

**Decisión** (método confirmado en clarify; aquí los detalles):

- **Muestra fija:** `offline/gen_retrieval_golden.py` escribe también `offline/golden/retrieval_parity.jsonl` con 50 preguntas:
  - 40 respondibles y 10 de seguimiento, elegidas con semilla fija entre las 370 puntuables (`review` distinto de `discarded` y con relevantes);
  - cada fila lleva el id, el tipo, la pregunta, la anterior, el documento (id del manifiesto y fichero), la evidencia y el acierto de escritorio con D-034 (0 o 1).
- **Corpus en el teléfono:** `offline/corpus` se añade como carpeta de assets de `androidTest`, junto a `offline/golden` (F04 R6): `docs/` con los 21 documentos y `manifest.json` con los títulos.
  - Unos 4,8 MB más en el APK de pruebas.
  - Nada se copia con adb, ni se queda nada en el teléfono.
- **`RetrievalParityTest`** (`@RequiresModels`, se lanza bajo demanda):
  1. copia el corpus a `cacheDir/parity-<uuid>`;
  2. registra cada documento en un índice temporal (`TempRagIndex`) con `displayName = «título del manifiesto».<ext>`: `DocumentTitle` da el título del manifiesto, como el `display_name` del escritorio, para la cabecera y el FTS;
  3. indexa con `IngestRunner` y `ChunkEmbeddingSession` reales (la GPU del teléfono, como F07);
  4. por cada pregunta, recupera con el `HybridRetriever` real;
  5. los relevantes son los fragmentos del documento de la pregunta que contienen la evidencia, con la regla `derive_relevant` del escritorio: espacios normalizados como `norm_ws` y, si ninguno la contiene entera, su frase más larga;
  6. acierto = algún bloque cubre un relevante (`blocks_hit`);
  7. **sin relevantes en el móvil** = «evidencia no extraída», contada aparte;
  8. compara el Recall@bloques del teléfono con el de escritorio sobre las mismas preguntas, sin las no extraídas: |diferencia| ≤ 0,05.
- **Registro `retrieval-parity`:** las dos cifras, el recuento de no extraídas y los ids de las preguntas que cambian de acierto (ids, nunca texto).
- **Limpieza:** borra la copia y el índice temporal al terminar. La biblioteca del usuario no se toca (FR-027).

**Verificado al planificar:**
- `offline/corpus/docs` está versionado (21 documentos, 4,8 MB);
- el índice `ref` de escritorio tiene 1.913 fragmentos;
- la regla de relevancia es `testset.derive_relevant`, con `norm_ws` = `" ".join(text.split())`;
- F06 halló 80 de 85 evidencias PDF en el teléfono. Las 5 que faltan son defectos de pypdf copiados en la evidencia, y se contarán como no extraídas.

**Tiempo estimado:** unos 1.900 fragmentos a ≈ 0,35 s en GPU son unos 11 minutos de indexación, más las 50 recuperaciones.

## R12. Revisión del orden dentro del presupuesto (D-035)

**Medición previa en el escritorio** (2026-10-29):
- script temporal sobre el índice `ref` y la caché de embeddings de F05, sin modelos;
- reproduce la cifra de D-034 (0,889).

| Variante | Recall@bloques | R@20 | MRR |
|---|---:|---:|---:|
| D-034 (referencia) | 0,889 | 0,984 | 0,810 |
| Trigramas como tercera lista de RRF (`content`, `trigram` sin tildes, términos ≥ 3 caracteres sin palabras vacías) | 0,873 | 0,976 | 0,787 |
| Reordenación de los 20 primeros por puntuación combinada, α = 0,3 | 0,889 | 0,984 | 0,800 |
| Ídem, α = 0,5 | **0,903** | 0,984 | 0,825 |
| Ídem, α = 0,7 | 0,897 | 0,984 | 0,817 |
| Trigramas + reordenación α = 0,5 | 0,900 | 0,976 | 0,822 |

En la tabla:
- «Puntuación combinada» es α · similitud coseno normalizada + (1 − α) · BM25 normalizado, con min-max sobre los candidatos de cada lista; un candidato que falta en una lista suma 0 por ella.
- Con la regla de adopción, la mejor variante tendría que llegar a 0,909 (+2 puntos).

Exploración adicional, solo informativa:
- reordenar los 10 o los 30 primeros: 0,900 y 0,903;
- fusión por puntuación de toda la lista: 0,903;
- trigramas solo con términos con cifras: 0,849.

**Conclusión preliminar:** ninguna variante cumple la regla de adopción (≥ +2 puntos). La mejor, la reordenación con α = 0,5, sube 1,4 puntos. Los trigramas empeoran: añaden coincidencias parciales que desplazan a los relevantes.

**Decisión:**
- F10 incorpora las dos variantes al harness con pruebas: trigramas como tercera lista y reordenación de los 20 primeros con α ∈ {0,3; 0,5; 0,7}.
- Las ejecuta como ablación sobre las 370 preguntas y las documenta en `offline/reports/eval-report.md`, en una sección «Revisión del orden (D-035, F10)».
- Registra la decisión en DECISIONS (FR-028, SC-010).
- Si la ejecución formal confirma estas cifras, se mantiene D-034 y la app no cambia. Si alguna variante llegara a +2 puntos, se para y se consulta, porque cambia el plan (riesgo 3).
- **Para F14:** se anota que la reordenación da +1,4 puntos, como palanca barata si se revisa el contexto.

**Detalles del harness:**
- **Trigramas:** una tabla FTS5 `trigram` temporal sobre `content`, en una base adjunta en memoria. No cambia `schema.sql` ni el índice; con la versión de SQLite del entorno virtual sí funciona (comprobado al medir).
- **Opciones nuevas de `EvalConfig`:** `third_list` y `rerank_alpha`/`rerank_top`. Se declaran como `LATE_FIELDS`, para que las ejecuciones de F05 conserven su huella.

## R13. INT8 para F14 (FR-026)

- **Hecho:** el p95 vectorial con 10k fragmentos ya superó 20 ms en F04 (23,1 ms) y en F07 con fragmentos ocultos (≈ 45 ms). R10 lo vuelve a medir.
- **Regla de la constitución IV:** «INT8 + preload solo si el p95 de la búsqueda vectorial supera 20 ms». Es una condición necesaria, no una obligación: con el total ≤ 200 ms, INT8 podría no hacer falta.
- **Decisión:** al cierre de F10 se presentan al usuario:
  - las cifras del teléfono;
  - una recomendación sobre INT8 para F14;
  - la decisión que elija, que se registra en DECISIONS.

  F10 no implementa INT8 (spec, fuera de alcance).

## R14. Privacidad de las pruebas

- **Índices temporales:** en `cacheDir`, borrados al terminar (`TempRagIndex`).
- **Corpus:** público. Viene en el APK de pruebas y se borra su copia.
- **Datos del usuario:** ninguna prueba lee su biblioteca. El diagnóstico sí, por diseño, y solo en pantalla.
- **Registros de las pruebas en el teléfono:** solo cifras e ids de pregunta del conjunto de evaluación (texto público, pero no se registra igualmente).
- **Al leer logs del teléfono:** se filtran a la app y a las etiquetas de la prueba (reglas del usuario).

## Versiones verificadas

- **Sin dependencias nuevas** (`gradle/libs.versions.toml`):
  - Kotlin 2.4.21;
  - coroutines 1.11.0;
  - Koin 4.2.2;
  - Compose BOM 2026.09.00;
  - `litertlm-android` 0.18.1;
  - `androidx.sqlite:sqlite-bundled` 2.7.1;
  - `ai.sqlite:vector` 1.1.2;
  - kotlinx-serialization 1.12.0 (para leer los golden en las pruebas).
- **Python:**
  - sin dependencias nuevas;
  - el SQLite del entorno virtual crea tablas FTS5 `trigram` con `remove_diacritics 1` (comprobado al medir, 2026-10-29).

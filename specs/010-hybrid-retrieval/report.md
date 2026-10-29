# Informe de cierre — F10 Recuperación híbrida

> **Estado:** implementada, pendiente de la revisión de cierre. El total p95 en el teléfono (202–211 ms) pasa del límite de 200 ms y se acepta en D-052, que lo pasa a F14 con INT8 y precarga.

**Rama:** `010-hybrid-retrieval`.
**Fecha:** 2026-10-29.
**Dispositivos:**
- emulador `Pixel_10`, API 37, ARM64, con EmbeddingGemma 2 270M instalado;
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), con los modelos de F03 instalados (siguen intactos al terminar).

**Versiones:**
- app 0.1.0 (versionCode 1);
- LiteRT-LM 0.18.1 y SQLite-Vector 1.1.2;
- sin dependencias nuevas;
- esquema del índice v3, sin cambios.

## 1. Qué se ha implementado

- **Algoritmos portados del escritorio** (`rag/retrieval`, R2):
  - `FollowUpRule`: menos de 8 términos normalizados, o empieza por «y », «¿y », «pero », «entonces», «¿y si» o «y si »; entonces se busca «anterior + espacio + actual»;
  - `ReciprocalRankFusion`: RRF con k = 60, rangos desde 1, desempate por mejor rango individual y después id menor; guarda el rango de cada lista para el diagnóstico;
  - `ContextSelector`: D-034 por defecto (4 bloques, 3 por documento, 1.719 tokens, sin vecinos), con vecinos si se piden, orden por documento y ordinal y fusión sin el párrafo repetido. Cada bloque lleva los ids de sus fragmentos.
- **`HybridRetriever`** (`data/retrieval`, R6):
  1. comprueba, en este orden, la pregunta vacía, el índice (`open()`) y el modelo (regla de F03), sin cargar el motor si no va a buscar;
  2. lanza la búsqueda léxica a la vez que el embedding de la consulta, y después la vectorial;
  3. fusiona, toma los 30 primeros (`fused_limit`, nuevo parámetro compartido) y carga **solo fragmentos buscables** (`searchableOnly`, R7);
  4. devuelve un resultado tipado (bloques, sin resultados, pregunta vacía, índice no disponible, modelos no listos, fallo del embedding) con tiempos y puntuaciones. Solo propaga la cancelación;
  5. registra una línea por llamada, solo con cifras.
- **Embedding de la consulta** (`EngineQueryEmbedder`, R4): CPU (D-024), bajo demanda, nunca retenido, así que se libera con poca memoria o con la UI oculta. Si el modelo rechaza el texto por largo, se recorta primero la pregunta anterior y después la actual (`QueryOverflow`, regla de D-036). La búsqueda léxica recibe siempre el texto completo.
- **Motores de embeddings compartidos** (`EngineCoordinator`, R5): varios bloques usan a la vez un motor de embeddings, contando usuarios y retenciones. Una consulta durante una tanda en CPU espera al fragmento en curso, no al final de la tanda. El LLM sigue siendo exclusivo, nunca hay dos motores en la GPU y el cierre espera a todos los usuarios.
- **Diagnóstico de búsqueda** en «MicroRAG Debug» (solo debug, R9): pregunta y pregunta anterior; seguimiento, texto de búsqueda, recortes, resultado, tiempos, bloques con su texto y los 30 candidatos con RRF, rango y distancia vectorial, rango léxico y marca de elegido.
- **Escritorio:**
  - `gen_retrieval_golden.py`: el golden de las 426 preguntas y la muestra de paridad de 50 (con `--check`);
  - revisión del orden (D-035) en el harness: trigramas como tercera lista y reordenación, con el comando `order-review` y su sección en el informe de evaluación (D-051).
- **Pruebas con modelos** preparadas para el teléfono: latencia, búsqueda durante la indexación y paridad, con el corpus de evaluación en el APK de pruebas.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| Pruebas Python (`unittest`) | ✅ 131/131 (F09: 114) |
| `gen_retrieval_golden.py --check` | ✅ al día, dos veces sin cambios |
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ «No issues found» |
| Tests JVM | ✅ 657/657 (F09: 584) |
| Instrumentados en el emulador API 37 (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 490/490 (F09: 485), repetida con los APK finales |
| Pruebas con modelos en el emulador (CPU, sin umbrales) | ✅ las tres (la variante de GPU de la búsqueda durante la indexación se salta en el emulador) |
| `apkanalyzer dex packages` del APK de release | ✅ 0 clases de `debug.retrieval` |
| Instrumentados en el teléfono (mismos filtros) | ✅ 490/490 (611 s) |

## 3. Validación por historia y criterio

### Historias

| Historia | Evidencia | Resultado |
|---|---|---|
| **US1** Contexto para una pregunta | `HybridRetrieverTest`, `RetrievalGoldenTest`, `ContextSelectorTest`, `ReciprocalRankFusionTest` | ✅ en la JVM: las 426 preguntas dan la misma fusión y los mismos bloques que el escritorio, con D-034 y con vecinos |
| **US2** Preguntas de seguimiento | `FollowUpRuleTest`, `RetrievalGoldenTest`, `HybridRetrieverTest` | ✅ regla y texto de búsqueda iguales al escritorio en las 426 |
| **US3** Nunca contenido que no se debe usar | `HybridRetrieverTest`, `RetrievalDuringIngestTest`, `RagIndexSearchableLoadTest`, `HybridRetrieverVisibilityTest` | ✅ JVM, emulador y teléfono |
| **US4** Diagnóstico de búsqueda | `RetrievalDiagnosticViewModelTest`, `RetrievalDiagnosticScreenTest`, `LayeringTest`; recorrido en el emulador | ✅ Recorrido en el emulador con su biblioteca de prueba: preguntas normales (3 y 4 bloques), seguimiento («Y el aceite?» con anterior: «Seguimiento: sí» y el texto unido), pregunta vacía («Pregunta vacía», tiempos a 0) y candidatos con «—». El log de la app solo lleva cifras |
| **US5** Calidad y latencia en el teléfono | `RetrievalBenchmarkTest`, `RetrievalDuringIndexingTest`, `RetrievalParityTest` | ⚠️ Paridad y búsqueda durante la indexación cumplen. El total p95 (202–211 ms) pasa de 200 ms; se acepta y pasa a F14 (D-052) |
| **US6** Revisión del orden | `order-review`, `eval-report.md`, D-051 | ✅ ninguna variante llega a +2 puntos; se mantiene D-034 |

### Criterios de éxito

| Criterio | Resultado |
|---|---|
| SC-001: casos del informe con el mismo resultado | ✅ casos de `test_retrieval.py` y `test_selector.py`, más el golden de 426 preguntas |
| SC-002: nunca fragmentos de documentos no indexados o excluidos | ✅ JVM (carrera entre búsqueda y carga), y SQLite-Vector en el emulador y el teléfono |
| SC-003: total p95 ≤ 200 ms y embedding p95 < 150 ms con 10k fragmentos | ⚠️ Aceptado en D-052:<br>• embedding solo: p95 125,6 ms ✅;<br>• embedding dentro de la búsqueda: 143,6 / 151,1 / 144,4 ms en tres tandas;<br>• total: 202,1 / 205,5 / 211,0 ms ✗, que pasa a F14 |
| SC-004: cifras con fragmentos ocultos | ✅ medidas (§4): total p95 de 216,1 y 227,5 ms con 4.300 ocultos |
| SC-005: paridad ±5 puntos sobre 50 preguntas | ✅ 0,816 en el teléfono frente a 0,837 en el escritorio (−2,0 puntos) sobre las mismas 49 |
| SC-006: sin búsqueda con el índice incompatible o los modelos sin preparar | ✅ JVM, emulador y teléfono |
| SC-007: p95 ≤ 1 s durante una tanda | ✅ 278,0 ms con la tanda en GPU; 258,8 ms con la tanda en CPU |
| SC-008: registros sin texto | ✅ JVM; en el teléfono, ni la paridad ni el diagnóstico dejan texto de las preguntas en el registro |
| SC-009: el diagnóstico no entra en release | ✅ `LayeringTest` y `apkanalyzer` |
| SC-010: revisión del orden documentada | ✅ D-051 y la sección del informe de evaluación |

## 4. Cifras medidas

### Revisión del orden (D-035, escritorio, 370 preguntas)

| Variante | Recall@bloques | R@20 híbrido | MRR híbrido |
|---|---:|---:|---:|
| D-034 | 0,889 | 0,984 | 0,810 |
| Trigramas como tercera lista | 0,873 | 0,976 | 0,787 |
| Reordenación, α = 0,3 | 0,889 | 0,984 | 0,800 |
| Reordenación, α = 0,5 | 0,903 | 0,984 | 0,825 |
| Reordenación, α = 0,7 | 0,897 | 0,984 | 0,817 |
| Trigramas + reordenación, α = 0,5 | 0,900 | 0,976 | 0,822 |

### Diagnóstico en el emulador (T039, biblioteca de prueba del emulador)

| Búsqueda | Embedding | Vectorial | Léxica | Selección | Total |
|---|---:|---:|---:|---:|---:|
| Primera, motor sin cargar | 323,6 ms | 196,4 ms | 100,6 ms | 0,9 ms | 583,0 ms |
| Seguimiento, motor cargado | 93,1 ms | 13,1 ms | 67,8 ms | 0,6 ms | 111,8 ms |
| «Y el aceite?», motor cargado | 51,1 ms | 13,5 ms | 15,6 ms | 0,5 ms | 100,1 ms |

El emulador no es el dispositivo de referencia: estas cifras solo muestran que el diagnóstico mide. La primera vectorial es lenta porque la base aún no está en caché.

### Pruebas con modelos en el emulador (T043, CPU, sin umbrales)

`RetrievalBenchmarkTest`, 10.000 fragmentos sintéticos y las 50 preguntas de la muestra, 2 vueltas (100 medidas):

| Caso | Embedding p50 / p95 | Vectorial p50 / p95 | Léxica p50 / p95 | Selección p50 / p95 | Total p50 / p95 |
|---|---|---|---|---|---|
| Sin ocultos | 113,4 / 157,6 ms | 16,5 / 23,2 ms | 23,9 / 38,1 ms | 1,5 / 2,3 ms | 135,6 / 182,8 ms |
| Con 4.300 ocultos | 83,8 / 101,7 ms | 29,4 / 35,2 ms | 32,3 / 44,6 ms | 1,0 / 1,3 ms | 115,7 / 135,7 ms |

Primera búsqueda con el motor sin cargar: 594,8 ms en total, 560,8 de ellos de embedding (incluye cargar el motor).

`RetrievalDuringIndexingTest`, tanda en CPU (el motor compartido con la ingesta): 40 búsquedas cada 500 ms, p50 95,1 ms, p95 109,0 ms y máximo 217,5 ms; todas terminaron antes que la tanda, que se cortó al llegar a 40. La variante en GPU se salta en el emulador.

`RetrievalParityTest` (corpus de los assets, índice temporal, ingesta y consulta reales en CPU; 966 s): Recall@bloques 0,837 en el emulador y 0,837 en el escritorio sobre las mismas 49 preguntas. Ninguna cambia de acierto; una evidencia no se extrae (q0167). El registro no contiene ninguna pregunta.

### Teléfono de referencia (A063, Android 15, enchufado)

`RetrievalBenchmarkTest`, 10.000 fragmentos sintéticos, las 50 preguntas de la muestra, 2 vueltas (100 medidas), motor de consulta en CPU.

Tanda 1 (31–35 °C):

| Caso | Embedding p50 / p95 | Vectorial p50 / p95 | Léxica p50 / p95 | Selección p50 / p95 | Total p50 / p95 |
|---|---|---|---|---|---|
| Sin ocultos | 133,7 / **143,6** ms | 24,0 / 51,9 ms | 38,8 / 118,8 ms | 5,8 / 7,9 ms | 182,6 / **202,1** ms |
| Con 4.300 ocultos | 129,5 / 147,6 ms | 49,0 / 68,3 ms | 130,1 / 152,7 ms | 5,0 / 6,7 ms | 208,8 / 216,1 ms |

Tanda 3 (23–25 °C), con el embedding medido también solo, sin nada en paralelo:

| Caso | Embedding p50 / p95 | Vectorial p50 / p95 | Léxica p50 / p95 | Selección p50 / p95 | Total p50 / p95 |
|---|---|---|---|---|---|
| Sin ocultos | 134,2 / 144,4 ms | 26,1 / 52,9 ms | 38,8 / 109,2 ms | 7,2 / 11,3 ms | 192,0 / **211,0** ms |
| Embedding solo | 122,5 / **125,6** ms | — | — | — | — |
| Con 4.300 ocultos | 139,6 / 149,9 ms | 48,4 / 66,9 ms | 69,8 / 148,9 ms | 5,8 / 8,5 ms | 215,2 / 227,5 ms |

- La tanda 2 dio el embedding p95 en 151,1 ms, por encima del límite, y el total p95 en 205,5 ms. Por eso se midió el embedding solo (D-052). El primer intento de esa medida se cortó al desconectarse el teléfono.
- El solapamiento con la léxica le cuesta al embedding unos 12 ms de mediana y 19 ms de p95. Sin el solapamiento, el modelo queda por debajo de los 150 ms de la constitución IV.
- Primera búsqueda con el motor sin cargar: 856,1 ms en total, 798,9 de embedding (tanda 1); 944,6 y 879,0 ms (tanda 3). Incluye cargar el motor.
- Los tiempos de la vectorial incluyen la espera a la conexión única cuando la léxica aún no ha terminado. F04 midió la vectorial sola: p95 23,1 ms.
- En este banco las palabras de las preguntas aparecen en casi todos los fragmentos sintéticos, así que la léxica es mucho más cara que en F04 (p95 26,5 ms con 4 palabras).

`RetrievalDuringIndexingTest`, 1.000 fragmentos publicados y text_200_pages.pdf indexándose (40 búsquedas cada 500 ms, tanda cortada al llegar a 40):

| Tanda | p50 | p95 | Máximo | Antes del fin de la tanda |
|---|---:|---:|---:|---|
| GPU | 256,4 ms | **278,0 ms** | 766,2 ms | todas |
| CPU (motor compartido) | 222,7 ms | 258,8 ms | 369,1 ms | todas |

`RetrievalParityTest` (corpus en un índice temporal, ingesta en el backend que elige el teléfono, 596 s): Recall@bloques **0,816** en el teléfono frente a 0,837 en el escritorio sobre las mismas 49 preguntas (−2,0 puntos). Cambia q0147 (acierto → fallo); q0167 no se extrae. El índice temporal y la copia del corpus se borraron.

Diagnóstico en el teléfono (T051): pregunta vacía, con captura sin la barra de estado; pregunta real, comprobada solo por el registro, porque la biblioteca tiene un documento del usuario. Dio «Sin resultados» (ningún fragmento buscable): 596,5 ms en frío y 176,0 ms con el motor cargado.

### INT8 para F14 (FR-026, R13)

El p95 vectorial pasa de los 20 ms de la constitución IV:
- 51,9–52,9 ms con 10.000 fragmentos;
- 66,9–68,3 ms con 4.300 ocultos.

Parte de ese tiempo es la espera a la conexión única mientras termina la léxica. La regla de la constitución que permite INT8 se cumple. El usuario eligió **INT8 + precarga en F14**:
- se implementa con Recall@20 de INT8 frente a F32 ≥ 0,97;
- el total se vuelve a medir frente a 200 ms.

Queda registrado en D-052.

## 5. Diferencias frente al mockup

Ninguna: F10 no tiene UI de producto. El diagnóstico es una herramienta interna de depuración, sin PNG.

## 6. Desviaciones respecto al plan y fallos encontrados

### Decisiones de implementación

1. **Registro de `RetrieveContextUseCase` en Koin:** va en el mismo commit que `HybridRetriever` (T025), no en T006. `AppModulesTest` comprueba el grafo y fallaría sin el `ContextRetriever`.
2. **Textos del diagnóstico:** en recursos de debug (`strings_retrieval.xml`), como el diagnóstico de F02. El contrato decía «en Kotlin» por error; corregido. Las cifras se escriben «Bloques: N · tokens: M», sin plurales, por el aviso `PluralsCandidate` de lint.
3. **Pregunta anterior en blanco:** el recuperador la trata como ausente. La regla portada sigue a Python, donde una cadena de espacios sí une.
4. **`FollowUpRule`:** recorta con `PyText.strip`, el `strip()` de Python, para que la comparación con los conectores sea idéntica.
5. **Golden:** 1,1 MB, el doble de lo estimado (≈ 550 KB), porque guarda la descripción de los fragmentos siguientes.
6. **Prueba existente cambiada:** `EngineCoordinatorTest.sameKeyInUse_waitsOutsideTheLoadLock` comprobaba que el mismo motor de embeddings se esperaba en exclusiva. Con la regla nueva solo el LLM es exclusivo: la prueba pasa a usar el LLM (`sameLlmInUse_…`).
7. **`RetrievalBenchmarkTest` tras la parada de T048 (D-052):**
   - registra el total sin comprobarlo;
   - mide también el embedding solo, con las mismas preguntas;
   - en el A063 solo comprueba el embedding p95 dentro de la búsqueda (< 150 ms), que pasó en la tanda 3.

### Fallos encontrados y corregidos

| Fallo | Dónde | Corrección |
|---|---|---|
| Las tarjetas cortas del diagnóstico se encogían a su texto | Recorrido en el emulador (T039) | `3445a3e`: todas ocupan el ancho |
| Con una pregunta vacía, el diagnóstico decía «Motor cargado: sí» sin motor cargado: los resultados sin búsqueda ponían `coldEngine` a falso | Teléfono (T051) | `022f4fd`: leen el estado real del motor, sin cargarlo |

## 7. Pendiente

- **Revisión de cierre del usuario.** El total p95 de la tanda 3 (211,0 ms) es más alto que el de la tanda 1 (202,1 ms), la que vio para decidir.
- **Subida de la rama:** los commits de F10 son locales; se suben con la aprobación del cierre.
- **En el teléfono:** queda limpio de restos de las pruebas, y los modelos siguen intactos.

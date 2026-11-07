# Informe de cierre — F14 Rendimiento en dispositivo y decisión INT8

> **Estado:** pendiente de la revisión de cierre con el usuario (T062).

**Rama:** `014-ondevice-performance`, creada desde `b938f78`.
**Fecha:** del 2026-11-06 al 2026-11-07 (fase del teléfono el 2026-11-07, con permiso del usuario).

**Dispositivos:**
- emuladores `Pixel_10` (API 37) y `MicroRAG_API33` (API 33);
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), con el modo multimodal activo.

**Versiones:**
- LiteRT-LM **0.19.1** y `litert-lm-api` 0.19.1 (antes 0.18.1); SQLite-Vector **1.2.0** (antes 1.1.2), D-091;
- constitución **1.2.1** (PATCH: LiteRT-LM 0.19.x);
- sin cambios de esquema: `rag.db` v5 y `chat.db` v3. La copia INT8 son tablas `vector0_*` de SQLite-Vector, y `rag_meta` gana `pages_quant_dirty`.

## 1. Qué se ha implementado

**Búsqueda INT8** (research R3 y R10, contrato `int8-index.md`, D-092):
- **Búsqueda:** `DefaultRagIndex` busca en la copia INT8 de SQLite-Vector cuando está al día (`quant_dirty = 0`) y precargada en la conexión. Preselecciona 50 candidatos y los ordena con la distancia exacta de sus vectores F32.
  - Sin ámbito, lee primero la cabeza de la copia (100 filas) y solo la amplía a todos los ocultos si allí no quedan 50 buscables.
  - Con ámbito, usa el escaneo en streaming con el filtro de F17.
  - Si la copia no está al día o no está precargada, la búsqueda es la exacta de siempre.
- **Páginas:** la misma ruta para los vectores de página desde 5.000 páginas, con su propia marca (`pages_quant_dirty`) y su precarga.
- **`QuantizedIndexMaintainer`:** reconstruye y precarga en segundo plano al arrancar, al acabar una tanda de ingesta y al borrar, quitar o reindexar. Agrupa las peticiones seguidas, no hace nada con una tanda activa y, ante un fallo, deja la búsqueda exacta.
- **Umbrales:** desde 5.000 fragmentos buscables (`INT8_MIN_CHUNKS`) y 5.000 páginas (`PAGES_INT8_MIN`). Por debajo no se construye nada.

**Primer token y contexto:**
- **Estimador de tokens por clases de caracteres** (R9, adoptado): texto, dígitos y el resto, en la app y en el harness. Solo cambia el límite de 2.000 tokens.
- **Palancas con interruptor**, todas evaluadas y apagadas: prefijo reutilizado con `clone()` (`PREFIX_CACHE`), decodificación especulativa MTP (`SPECULATIVE_DECODING`), activaciones FP32 (`ACTIVATION_PRECISION`) y la plantilla `v4-cifras`.
- **Desglose del primer token** en el registro de la sesión (`AnswerTimings`): embedding, recuperación, prompt y motor.

**Modo multimodal** (R10, D-096):
- **Precarga por ámbito (adoptada):** al cambiar de ámbito, el chat carga Gemma con o sin visión según tenga páginas el ámbito nuevo, antes de la pregunta.
- **Seguimiento con páginas:** las variantes «solo la nueva» y «la mejor de las dos» están implementadas como palanca (`PAGE_FOLLOW_UP`). Se queda `JOINED`, la de hoy.
- **Equilibrado** anuncia «unos 9 s» (`PageDetail.BALANCED`).

**Herramientas de medida (solo en `androidTest` y en el escritorio):**
- **Bancos** de 10k, 50k y 100k fragmentos con frases reales del corpus y embebidos con la 270M, y bancos de 1.000, 5.000 y 10.000 páginas. Se construyen en el escritorio (`bench-bank`, `bench-pages`) y se cargan con `adb` en `dev-bench`.
- **Kit de medida:** `BenchReport`, `DeviceConditions`, `BenchBank` y `DevFiles`.
- **Pruebas de banco:** `SearchScaleBenchmarkTest`, `PageSearchBenchmarkTest`, `EmbedderComparisonBenchmarkTest`, `GenerationBenchmarkTest`, `SustainedChatBenchmarkTest`, `NumericFidelityBenchmarkTest`, `PrefixCacheGoldenTest`, `StartupBenchmarkTest`, y los modos `reload` y `sustained` de `VisualChatBenchmarkTest`.
- **Limpieza:** `BenchCleanupTest`.
- **Escritorio:**
  - `run_eval.py device-report`, que escribe las tablas del teléfono en `benchmarks.md`;
  - revisiones: `f14-review`, `token-review`, `numeric-review`, `visual-followup-review` y `crosslang-screen`;
  - el conjunto de cifras `offline/testset/numeric`;
  - las reglas congeladas `offline/config/f14_rules.json`.

## 2. Build, lint y tests

| Prueba | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | 1.370 de 1.370 |
| Lint (`lintDebug`) | Sin avisos (los de versión desaparecen con 0.19.1 y 1.2.0) |
| Escritorio (Python) | 318 de 318 |
| Regresión del texto, con el modo apagado (`run_eval.py ablations`) | Mismas cifras que en F20 (SC-009) |
| `Pixel_10` (API 37), instrumentadas sin `@RequiresNetwork` | 1.059 de 1.059 |
| `MicroRAG_API33` (API 33) | 1.055 de 1.059: los 4 fallos conocidos de Atrás desde F13 |
| `RagIndexInt8Test` | 13 de 13 en los dos emuladores, con el caso nuevo de los excluidos que llenan la cabeza |
| A063, instrumentadas (T056) | (pendiente) |
| Release (`assembleRelease`) | Sin `BenchReport`, `BenchBank`, `DevFiles`, `dev-bench` ni `dev-models` en el dex. La ruta INT8 del producto sí está |

## 3. Validación por criterio

| Criterio | Resultado |
|---|---|
| SC-001: `benchmarks.md` con cifras reales de todos los bloques | ✅ `benchmarks.md` §7 (resumen) y §8 (tablas de `device-report`): arranque, ingesta, embedding, búsqueda a 10k, 50k y 100k, INT8, generación con 4, 6 y 8 bloques, extremo a extremo, recursos, sesión sostenida, palancas, multimodal y E4B. El consumo de batería no se mide, porque el teléfono carga por `adb` |
| SC-002: INT8 ≤ 20 ms a 10k con Recall@20 ≥ 0,97 | ✅ 9,8 ms de p95 con Recall@20 de 1,000 (D-092) |
| SC-003: híbrida ≤ 200 ms a 10k con la 270M | ✅ 154 ms de p95 (F32: 249 ms) |
| SC-004: primer token ≤ 5 s en sesión sostenida | ✅ 4,63 s de p95 en 25 min sin reposo, con 111 respuestas (D-094). Con 30 s de reposo, 5,04 s con 34 respuestas |
| SC-005: tras matar el proceso, búsqueda exacta y precarga después; nunca una copia desfasada | ✅ `RagIndexInt8Test` (reabrir, cada operación desfasa, fila nueva igual a la consulta, reconstrucción o precarga fallidas) y `QuantizedIndexMaintainerTest` |
| SC-006: cada palanca con su regla previa, sus cifras y su resultado | ✅ D-092–D-097, con las reglas de `f14_rules.json`, congeladas antes de evaluar |
| SC-007: seguimiento con páginas ≥ 90 % en los cambios | ❌ Ninguna variante llega: 45,8 % como mucho. Se queda `JOINED` (D-096), como permite la spec |
| SC-008: cifras deformadas y estimación de tokens medidas y decididas | ✅ D-095 (15 de 62 con fp16, 0 con FP32, 16 con `v4-cifras`) y D-093 (p95 real/estimado de 1,000 con cifras) |
| SC-009: con el modo apagado y sin palancas, `ablations` igual y la batería en verde | ✅ `ablations` igual que en F20; baterías en §2 |
| SC-010: nada de bancos en release, y el teléfono limpio y sin cambios del usuario | (pendiente de T058). La biblioteca del usuario tiene 97 fragmentos buscables, por debajo del umbral, así que no se crea ninguna copia INT8 en ella |

## 4. Cifras medidas

Todas en `benchmarks.md`. Lo principal:

| Bloque | Cifra |
|---|---|
| Búsqueda vectorial a 10k, p95 | F32 65,1 ms → INT8 9,8 ms (Recall@20 1,000) |
| Híbrida a 10k con la 270M, p95 | 249 → 154 ms |
| Búsqueda vectorial a 50k y 100k, p95 | 216 → 16,7 ms y 441 → 22,4 ms (Recall@20 1,000); híbrida 446 → 224 ms y 778 → 352 ms |
| Primer token, 4 bloques, banco corto | p50 4,82 s · p95 5,49 s |
| Primer token sostenido, 25 min sin reposo | p50 3,60 s · p95 4,63 s |
| Con páginas, p95 (Rápido / Equilibrado / Detallado) | 6,69 / 9,58 s (9,11 s sostenido) / 14,39 s |
| Primera respuesta con página tras cambiar de ámbito | 15,9 s → 9,4 s con la precarga |
| E4B frente a E2B, primer token p95 | 13,64 s frente a 5,49 s; pico de 4,51 GB frente a 2,93 GB |
| Consulta con la 440M frente a la 270M | 123 ms de p50 las dos |
| Arranque de Gemma, en frío y con caché | 15,4 s y 5,7 s (con visión, 16,1 s y 6,1 s) |
| Ingesta | 4,3 fragmentos/s en la GPU; 14,0 páginas/min |

## 5. Decisiones

- **D-088:** clarify.
- **D-089:** plan.
- **D-090:** analyze.
- **D-091:** runtimes y constitución 1.2.1.
- **D-092:** INT8.
- **D-093:** bloques, estimador y E4B.
- **D-094:** primer token y palancas del prompt.
- **D-095:** fp16.
- **D-096:** modo multimodal.
- **D-097:** palancas de calidad.

## 6. Desviaciones respecto al plan

- **La búsqueda INT8 lee primero la cabeza de la copia** (T049). El plan unía k + m filas, como la búsqueda exacta de F10. Con un 32 % de ocultos, eso dejaba INT8 en 40,6 ms de p95. Se descubrió en la segunda pasada y se corrigió con su prueba.
- **`INT8_CANDIDATES` vale 50, no 100,** y sirve también para las páginas, que dan Recall@5 de 1,000 con 50.
- **`INT8_MIN_CHUNKS` vale 5.000, no 10.000.** La biblioteca del usuario sigue por debajo.
- **La batería del teléfono no repite los bancos de F14.** Cada uno se midió en su tarea (T047–T054); repetirlos habría sumado más de una hora sin comprobar nada nuevo.
- **La sesión sostenida final no se repite,** porque ninguna palanca adoptada toca el primer token del chat de texto. El corpus de evaluación tiene menos de 5.000 fragmentos, así que INT8 no entra.
- **La búsqueda entre idiomas** se evaluó por su techo (solo por significado), porque la variante con normalizador inglés no podía cerrar la brecha (filtro previo, R12).

## 7. Fallos encontrados y corregidos

- **La unión de k + m filas en la búsqueda INT8** (§6).
- **`device-report` no incluía los informes planos** del chat con páginas y de la ingesta visual. Además, las horas no decían que eran UTC.
- **`ChatViewModelTest` tenía escrita a mano la espera de Equilibrado.** Ahora la toma de `PageDetail`.

## 8. Lo que queda

- **MTP:** es la mayor ganancia medida, con la respuesta entera de 21,0 s a 14,8 s de p95. Cambia 10 de 20 respuestas greedy, así que no cumple la regla fijada. Queda como opción si el usuario acepta respuestas distintas, con su propia evaluación de calidad.
- **FP32:** no deforma ninguna cifra, a cambio de unos 2,2 s más de primer token. Queda como opción del usuario.
- **Sesión con reposo:** el p95 es de 5,04 s con 34 respuestas, 0,04 s por encima del límite. La que decide es la sesión sin reposo.
- **Seguimiento con páginas:** ninguna variante llega al 90 %. La mejor de las dos mejora los cambios de documento sin perder seguimientos, y queda anotada.
- **Búsqueda entre idiomas:** sigue a 14–19 puntos de la referencia. Necesitaría otra cosa (traducir la consulta o un índice por idioma).
- **Consumo de batería:** no se pudo medir, porque el teléfono carga por `adb`.

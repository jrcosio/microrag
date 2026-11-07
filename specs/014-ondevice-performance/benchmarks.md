# Rendimiento en dispositivo y decisión INT8 (F14): cifras

Cifras reales del Nothing Phone (1) A063 (QTI SM7325, Adreno 642L, 12 GB, Android 15) y del escritorio (macOS en Apple Silicon), con LiteRT-LM 0.19.1 y SQLite-Vector 1.2.0, adoptados en T005 (research R1). Sustituyen los presupuestos de `docs/RAG_Movil/informe-rag-movil-v2.md` («Latencia (presupuesto, no medición)» y «Benchmarks en el dispositivo»).

- **Escritorio:** las secciones 1–6 se escriben a mano a partir de los informes de `offline/build/f14/`.
- **Teléfono:** la sección 7 resume a mano las cifras y las decisiones; las tablas de la sección 8 las escribe `run_eval.py device-report` a partir de los JSON de `files/reports`. Las respuestas y los textos solo van en esos JSON, que no van a git.

## 1. Runtimes (R1, T003–T005)

| Comprobación | Resultado |
|---|---|
| Golden de la 270M en el escritorio, 0.18.1 frente a 0.19.1 | 50 vectores, coseno mínimo 1,00000 |
| Respuestas de `v4` en la GPU del Mac, 0.18.1 frente a 0.19.1 | 426 de 426 idénticas |
| JVM y lint | 1.355/1.355; lint sin los avisos de versión |
| Batería sin modelos en los emuladores | Pixel_10 (API 37) 792/792; MicroRAG_API33 con los 4 fallos conocidos de Atrás (F13) |
| Sesión corta en el A063 | 19/19: `EmbeddingGoldenTest` (440M), humo de Gemma en texto y con visión (primer token con página 7,5 s), goldens de prompt, normalizador e ingesta, extensión 1.2.0 y `RagIndexInt8Test` |

## 2. Estimación de tokens (R9, T035–T036)

Tokenizador de Gemma 4 E2B frente a la estimación, real/estimado:

| Texto | Estimador de F11 (4,554 caracteres por token) | Estimador adoptado (4,5644 · 0,5242 · 4,4305) |
|---|---|---|
| 426 prompts de la app | media 1,045 · p95 1,173 · máximo 1,544 | media 0,958 · p95 1,063 · máximo 1,103 |
| 1.913 fragmentos del corpus | media 1,003 · p95 1,282 · máximo 2,182 | media 0,929 · p95 1,074 · máximo 1,667 |
| Textos con ≥ 10 % de dígitos (13) | media 1,795 · **p95 2,182** | media 0,926 · **p95 1,000** |

- Con el límite de 2.000 tokens del prompt cambian **2 de 426** prompts (q0088 y q0131, un bloque menos cada uno). Sus dos respuestas nuevas son correctas: la calidad de `v4` no cambia (81,1 % correctas, 91,1 % negativas y 94,3 % citas).
- Se cumple la regla de `f14_rules.json`, y se adopta.

## 3. Cifras deformadas con fp16 (R8, T032–T033)

62 preguntas sobre bloques llenos de cifras (`offline/testset/numeric`, revisadas). Una respuesta cuenta si tiene alguna cifra que no está en sus fuentes ni en la pregunta (`numeric.deformed`, comprobado a mano en las marcadas):

| Plantilla | GPU del Mac, fp16 | GPU del Mac, FP32 | A063, fp16 | A063, FP32 |
|---|---:|---:|---:|---:|
| `v4` | 12 de 62 | 0 de 62 | 15 de 62 | 0 de 62 |
| `v4-cifras` | 18 de 62 | 0 de 62 | 16 de 62 | — |

En el Mac y en el A063, FP32 elimina todas las deformaciones y la instrucción reforzada no las reduce. En el teléfono (`offline/testset/numeric/review.csv`, T051):

- `v4-cifras` deja 16, y la regla pide como mucho la mitad que `v4` (7): no se adopta.
- FP32 sube 2,21 s el p95 del primer token en el banco corto (T050), y la regla admite 0,5 s: no se adopta.

El riesgo que queda: 1 de cada 4 respuestas sobre texto muy denso en cifras deforma alguna. Sobre el corpus de evaluación son 2 de 60 (F18).

## 4. Regla de seguimiento con páginas (R10, T041)

40 pares sobre las 302 páginas del corpus visual de F18 (768 px, 140 tokens, 256D, la 440M), con la página que entraría en la respuesta (la primera):

| Variante | Cambios de documento (24) | Seguimientos reales (16) |
|---|---:|---:|
| Unida, la de hoy | 8,3 % | 56,2 % |
| Solo la pregunta nueva | 45,8 % | 18,8 % |
| La mejor de las dos | 29,2 % | 56,2 % |

Ninguna llega al 90 % de la regla (SC-007): la regla de hoy se queda. La búsqueda de texto de los seguimientos no cambia en ninguna variante.

## 5. Búsqueda entre idiomas (R12, T044)

Recall@bloques (preguntas con un fragmento con la evidencia en el contexto):

| Combinación | Híbrida, la de hoy | Solo por significado | Referencia en el mismo idioma |
|---|---:|---:|---:|
| ES·EN (preguntas en inglés sobre documentos en español) | 0,579 | 0,789 | ES·ES 0,889 |
| EN·ES (preguntas en español sobre documentos en inglés) | 0,780 | 0,840 | EN·EN 0,900 |

La búsqueda por significado sola es el máximo peso que puede darle cualquier reordenación. Respuestas juzgadas a mano (`offline/reports/crosslang-review-vector.csv`, D-033):

| Combinación | Correctas, híbrida | Correctas, solo por significado | Referencia | Negativas | Citas |
|---|---:|---:|---:|---:|---:|
| ES·EN | 50,9 % | 63,2 % | 82,5 % | 93,3 % | 91,5 % |
| EN·ES | 64,0 % | 66,0 % | EN·EN 80,0 % | 100,0 % | 90,3 % |

Siguen a 19,3 y 14 puntos de su referencia, y la regla de D-062 pide 10 como mucho: no cumple, y la búsqueda entre idiomas no cambia.

## 6. Tres bloques de contexto (R13, T031)

`v4` con 3 bloques en lugar de 4, en la GPU del Mac con 0.19.1: cambian 224 respuestas, juzgadas a mano (`offline/reports/f14-review.csv`).

| Bloques | Correctas | Negativas | Citas |
|---|---:|---:|---:|
| 4 (`v4`) | 81,1 % | 91,1 % | 94,3 % |
| 3 | 79,7 % | 94,6 % | 92,7 % |

Baja 1,35 puntos, y la regla de `f14_rules.json` admite 1: se quedan 4 bloques.

## 7. Teléfono: resumen

### 7.1 Sesión y versiones

- **Teléfono:** Nothing Phone (1) A063, Android 15 (API 35). La fase del teléfono fue el 2026-11-07 (UTC), con permiso del usuario.
- **Runtimes:** LiteRT-LM 0.19.1 y SQLite-Vector 1.2.0.
- **Condiciones:**
  - el teléfono estuvo cargando por USB durante todas las medidas, porque `adb` lo exige. Por eso el consumo de batería no se mide (77–100 %);
  - pantalla despierta;
  - estado térmico `NONE` al principio y al final de todos los informes, y en cada muestra de las sesiones sostenidas.

### 7.2 Modelos

- **Gemma 4 E2B** (`gemma-4-E2B-it.litertlm`): en la GPU, con las activaciones del fichero (fp16), con y sin visión.
- **EmbeddingGemma 440M:** instalada, porque el A063 tiene el modo multimodal activo.
- **La 270M y Gemma 4 E4B:** cargadas desde `dev-models` solo para medir, verificadas por SHA-256 y borradas al terminar (D-088).

### 7.3 Arranque (`StartupBenchmarkTest`, T048)

| Modelo | Carga en frío | Con caché |
|---|---:|---:|
| Gemma sin visión | 15,4 s | 5,7 s |
| Gemma con visión | 16,1 s | 6,1 s |
| 440M | 1,01 s | 0,49 s |
| 270M | 0,99 s | 0,47 s |

### 7.4 Ingesta (T048)

- **Texto** (`IndexingBenchmarkTest`): 200 páginas y 430 fragmentos con la 270M. En la CPU, 3,0 fragmentos/s (144,7 s); en la GPU, 4,3 fragmentos/s (108,6 s).
- **Páginas** (`VisualIngestBenchmarkTest`): 14,0 páginas/min, con 111 KB por imagen de página.

### 7.5 Embedding de la consulta (`EmbedderComparisonBenchmarkTest`, T049)

200 consultas cada una, en 4 rondas alternas:

| Modelo | p50 | p95 | Pico de RSS |
|---|---:|---:|---:|
| 440M | 123 ms | 125 ms | 603–635 MB |
| 270M | 123 ms | 124 ms | 601–621 MB |

Con el modo activo, la consulta no se hace más lenta por usar la 440M.

### 7.6 Búsqueda (`SearchScaleBenchmarkTest`, T049)

426 preguntas × 2 rondas, con la 440M:

| Banco | Vectorial F32 p95 | Híbrida F32 p95 |
|---|---:|---:|
| 1k | 14,0 ms | — |
| 2,5k | 10,4 ms | — |
| 5k | 18,2 ms | — |
| 10k | 66,3 ms | 247 ms |
| 50k | 184 ms | 365 ms |
| 100k | 375 ms | 691 ms |

- **Con la 270M** a 10k (SC-003): la híbrida da un p95 de 247–249 ms en F32.
- **Desglose del p95 en F32:**
  - embedding de la consulta, 163 ms;
  - vectorial, 75 ms, y léxica, 124 ms, en paralelo dentro de la híbrida.

### 7.7 INT8 (R3, T049)

**Tercera pasada,** la adoptada: 50 candidatos reordenados, leyendo primero la cabeza. Con 10k fragmentos y la 270M, 426 preguntas × 2 rondas:

| Variante | Vectorial p50 / p95 | Recall@20 | Híbrida p50 / p95 | Con ámbito de carpeta p95 |
|---|---:|---:|---:|---:|
| F32 | 46,1 / 65,1 ms | — | 173 / 249 ms | 272 ms |
| INT8 + reordenar 50 | **8,2 / 9,8 ms** | **1,000** | 144 / **154 ms** | 185 ms |
| INT8 sola (top 20) | 12,3 / 12,8 ms | 0,966 | — | — |

- **SC-002 se cumple:** 9,8 ms ≤ 20 ms, con Recall@20 ≥ 0,97.
- **SC-003 se cumple:** 154 ms ≤ 200 ms con la 270M.
- **Subconjuntos del banco de 10k** (búsquedas vectoriales seguidas, p95):

  | Fragmentos | F32 | INT8 + reordenar 50 |
  |---:|---:|---:|
  | 1.000 | 8,6 ms | 5,4 ms |
  | 2.500 | 11,2 ms | 7,8 ms |
  | 5.000 | 21,9 ms | 11,6 ms |
  | 7.500 | 31,6 ms | 15,7 ms |

  La exacta pasa de 20 ms hacia los 5.000 fragmentos, y desde ahí se usa INT8 (`INT8_MIN_CHUNKS`).
- **Reconstruir y precargar:** 11 ms con 10k; precargar al abrir, 21 ms; memoria de la copia, 2,6 MB.

**50k y 100k** (la 440M, 426 preguntas × 1 ronda):

| Banco | F32 vectorial p95 | INT8 + reordenar 50 p95 · Recall@20 | Híbrida F32 p95 | Híbrida INT8 p95 |
|---|---:|---:|---:|---:|
| 50k | 216 ms | **16,7 ms** · 1,000 | 446 ms | 224 ms |
| 100k | 441 ms | **22,4 ms** · 1,000 | 778 ms | 352 ms |

- Con 50k y 100k, la híbrida pasa de 200 ms por la búsqueda léxica (209 y 335 ms de p95 dentro de la híbrida), no por la vectorial.
- Las copias INT8 ocupan 13,2 y 26,4 MB en memoria, y precargarlas tarda 37 y 55 ms.

**Pasadas anteriores** (en §8):
- La primera, con 100 candidatos y la 440M: la vectorial daba un p95 de 41,5 ms con 10k, 77,3 ms con 50k y 155 ms con 100k, y reconstruir y precargar costaba 73, 576 y 818 ms.
- La segunda, con 50 candidatos, se quedaba en 40,6 ms. Leer la cabeza lo baja a 9,8 ms (research R3).

### 7.8 Generación con 4, 6 y 8 bloques (`GenerationBenchmarkTest`, T050)

20 preguntas por variante, en la GPU:

| Bloques | Primer token p50 / p95 | Respuesta entera p50 / p95 | Prefill p50 | Decodificación p50 |
|---:|---:|---:|---:|---:|
| 4 | 4,82 / 5,49 s | 13,7 / 22,9 s | 348 tok/s | 7,6 tok/s |
| 6 | 6,00 / 7,56 s | 14,5 / 21,7 s | 356 tok/s | 10,0 tok/s |
| 8 | 5,40 / 8,36 s | 14,7 / 19,7 s | 365 tok/s | 9,2 tok/s |

- Carga de Gemma, 6,2 s; pico de RSS, 2,93 GB.
- **Decisión:** se quedan 4 bloques. Más bloques suben el p95 del primer token, y 3 bloques bajan la calidad 1,35 puntos (§6).

### 7.9 Extremo a extremo y recursos (`SustainedChatBenchmarkTest`, T052)

Sesión de 25 min sin reposo, con 111 respuestas enteras:

| Parte | p50 | p95 |
|---|---:|---:|
| Embedding de la consulta | 158 ms | 178 ms |
| Recuperación híbrida (con el embedding) | 215 ms | 242 ms |
| Construir el prompt | 12 ms | 36 ms |
| Primer token del motor | 3,34 s | 4,36 s |
| **Primer token desde `ask()`** | **3,60 s** | **4,63 s** |
| Respuesta entera | 11,2 s | 29,6 s |

- **Prompt estimado:** p50 de 1.376 tokens y p95 de 1.844.
- **Memoria:**
  - RSS de 2,70 GB al empezar y 3,32 GB al terminar;
  - pico de 3,35 GB en el chat de texto, 3,84 GB con páginas en Equilibrado y 4,68 GB con Detallado;
  - heap de Java entre 4 y 26 MB.
- **Temperatura de la batería:** 25–38 °C.

### 7.10 Sesión sostenida (R5, T052)

| Sesión | Respuestas | Primer token p50 / p95 / p99 | Respuesta entera p50 / p95 | Estado térmico |
|---|---:|---:|---:|---|
| Sin reposo | 111 | 3,60 / **4,63** / 4,66 s | 11,2 / 29,6 s | `NONE` |
| Con 30 s de reposo | 34 | 3,70 / 5,04 / 5,40 s | 11,5 / 34,6 s | `NONE` |

- **Por tramos de 5 min,** el p95 va de 4,54 a 4,64 s sin reposo y de 4,20 a 5,40 s con reposo.
- **SC-004:** se cumple con la sesión sin reposo (4,63 s ≤ 5 s). Ninguna palanca adoptada toca el primer token del chat de texto, así que no hace falta repetirla.
- **Con reposo** pasa 0,04 s del límite, con solo 34 respuestas.

### 7.11 Palancas (banco corto: 20 preguntas alternando la base y la palanca, 4 bloques)

| Palanca | Primer token p95, base → palanca | Otra cifra | Regla | Resultado |
|---|---:|---|---|---|
| Prefijo reutilizado | 7,23 → 8,32 s (+1,10) | prefill 355 → 254 tok/s | −0,3 s | No |
| Plantilla más corta (`v4-corta`) | — | cota: 0,19 s como mucho | −0,3 s | Descartada sin medir |
| MTP | 5,50 → 5,39 s | decodificación +49,8 %; 10 de 20 respuestas greedy cambian | +20 %, greedy idénticas | No |
| FP32 | 5,09 → 7,30 s (+2,21) | 0 de 62 respuestas con cifras deformadas | +0,5 s | No |
| `v4-cifras` | 4,43 → 4,45 s | 16 de 62 deformadas, frente a 15 | la mitad que `v4` | No |
| Estimador de tokens | — | p95 real/estimado 1,000 con cifras (§2) | ≤ 1,0 y la calidad | **Sí** |
| 3 bloques | — | −1,35 puntos (§6) | ≤ 1 punto | No |
| Reordenación α = 0,5 | — | +1,4 puntos de Recall@bloques | +2 puntos | No |
| Seguimiento con páginas | — | 45,8 % de cambios como mucho (§4) | 90 % | No: se queda `JOINED` |
| Precarga por ámbito | 15,88 → 9,42 s (primera respuesta con página) | el mismo pico | −0,3 s y ≤ 200 MB | **Sí** |
| Búsqueda entre idiomas | — | 19,3 y 14 puntos por debajo (§5) | ≤ 10 puntos | No (solo se evalúa) |

### 7.12 Modo multimodal (T049, T053)

- **Primer token por detalle** (20 preguntas):

  | Respuesta | p50 | p95 | Anuncia |
  |---|---:|---:|---|
  | Sin página | 3,83 s | 4,82 s | — |
  | Rápido | 5,42 s | 6,69 s | unos 6,5 s |
  | Equilibrado | 8,31 s | 9,58 s | **unos 9 s** (antes 8,5) |
  | Detallado | 14,20 s | 14,39 s | unos 14,5 s |

- **Equilibrado sostenido 25 min:** 72 respuestas, p50 de 8,44 s y p95 de 9,11 s sin contar la primera (15,4 s con la carga), sin estrés térmico. El anuncio pasa a «unos 9 s».
- **Recarga de Gemma con visión:**
  - 13,7 s en la pasada por detalle;
  - la primera respuesta con página tras una sin página da un p95 de 15,9 s sin precarga y de 9,4 s con la precarga por ámbito, que se adopta.
- **Páginas F32 frente a INT8,** con la ruta de la app y 50 candidatos (100 consultas × 2 rondas, Recall@5 de 1,000 en todas):

  | Páginas | F32 p95 | INT8 + reordenar 50 p95 |
  |---:|---:|---:|
  | 1.000 | 8,4 ms | 11,6 ms |
  | 5.000 | 16,6 ms | 14,6 ms |
  | 10.000 | 27,5 ms | **18,0 ms** |

  Las páginas usan INT8 desde 5.000 páginas (`PAGES_INT8_MIN`). La primera pasada, con SQL directo y 100 candidatos, dio 4,2, 8,7 y 14,5 ms.
- **Pico de RSS:** 4,68 GB con Detallado.

### 7.13 E4B frente a E2B (`GenerationBenchmarkTest` `e4b`, T054)

| Medida | E2B | E4B |
|---|---:|---:|
| Primer token p50 / p95 | 4,82 / 5,49 s | 10,77 / 13,64 s |
| Respuesta entera p50 / p95 | 13,7 / 22,9 s | 26,4 / 37,9 s |
| Prefill p50 | 348 tok/s | 130 tok/s |
| Decodificación p50 | 7,6 tok/s | 5,2 tok/s |
| Carga | 6,2 s | 22,8 s |
| Pico de RSS | 2,93 GB | 4,51 GB |

**Recomendación:** seguir con E2B (D-035). E4B carga y responde, pero su primer token es más del doble del límite de 5 s.

## 8. Teléfono: tablas de los informes

<!-- device-report:start -->

### prefix-golden · 2026-11-06 22:34 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 67 → 76 % · cargando · pico de RSS 3275 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| prefix-vs-whole (questions=20, backend=GPU) | — | — | — | — | 0 | identical=4 · different=16 |

### startup · 2026-11-06 22:55 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 77 → 78 % · cargando · pico de RSS 2291 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| gemma-text | cold | 15.436,9 | 15.723,3 | 15.723,3 | 2 |  |
| gemma-text | cached | 5.733,8 | 5.827,4 | 5.827,4 | 2 |  |
| gemma-vision | cold | 16.130,2 | 16.343,2 | 16.343,2 | 2 |  |
| gemma-vision | cached | 6.075,3 | 6.107,5 | 6.107,5 | 2 |  |
| eg2-440m | cold | 1.013,7 | 1.101,1 | 1.101,1 | 2 |  |
| eg2-440m | cached | 492,4 | 498,4 | 498,4 | 2 |  |
| eg2-270m | cold | 985,4 | 1.002,3 | 1.002,3 | 2 |  |
| eg2-270m | cached | 468,3 | 472,1 | 472,1 | 2 |  |

### f19-visual-ingest · 2026-11-06 23:10 UTC

| Medida | Valor |
|---|---:|
| pages | 200 |
| first_pass_pages | 100 |
| first_pass_s | 392.1 |
| second_pass_pages | 100 |
| second_pass_s | 429.0 |
| pages_per_minute | 14.0 |
| bytes_per_page | 111165 |

### indexing-cpu · 2026-11-06 23:12 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector - · EG2_270M (CPU, por defecto)
Térmico NONE → NONE · batería 84 → 85 % · cargando · pico de RSS 1933 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| text-200-pages | — | — | — | — | 0 | pages=200 · chunks=430 · totalMs=144704 · embeddingMs=142000 · chunksPerSecond=3.028 · llmPreloaded=true |

### indexing-gpu · 2026-11-06 23:15 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector - · EG2_270M (GPU, por defecto)
Térmico NONE → NONE · batería 85 → 86 % · cargando · pico de RSS 1944 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| text-200-pages | — | — | — | — | 0 | pages=200 · chunks=430 · totalMs=108583 · embeddingMs=98888 · chunksPerSecond=4.348 · llmPreloaded=true |

### prefix-golden · 2026-11-06 23:17 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 86 → 94 % · cargando · pico de RSS 3026 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| prefix-vs-whole (questions=20, backend=GPU) | — | — | — | — | 0 | identical=4 · different=16 |

### prefix-golden · 2026-11-06 23:29 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 94 → 99 % · cargando · pico de RSS 2857 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| prefix-vs-whole (questions=5, backend=CPU) | — | — | — | — | 0 | identical=2 · different=3 |

### generation-base · 2026-11-06 23:34 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 2925 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| base-4 (blocks=4, backend=GPU) | ttft | 4.817,0 | 5.489,0 | 10.050,0 | 20 | initMs=6176.1 · rssPeakMb=2925 |
| base-4 (blocks=4, backend=GPU) | total | 13.746,0 | 22.854,0 | 24.129,0 | 20 |  |
| base-4 (blocks=4, backend=GPU) | prefill-tok-s | 348,4 | 423,8 | 450,7 | 20 |  |
| base-4 (blocks=4, backend=GPU) | decode-tok-s | 7,6 | 9,3 | 9,3 | 20 |  |
| base-6 (blocks=6, backend=GPU) | ttft | 6.000,0 | 7.561,0 | 8.501,0 | 20 | initMs=6176.1 · rssPeakMb=2925 |
| base-6 (blocks=6, backend=GPU) | total | 14.477,0 | 21.748,0 | 21.900,0 | 20 |  |
| base-6 (blocks=6, backend=GPU) | prefill-tok-s | 355,9 | 390,0 | 396,7 | 20 |  |
| base-6 (blocks=6, backend=GPU) | decode-tok-s | 10,0 | 11,2 | 11,3 | 20 |  |
| base-8 (blocks=8, backend=GPU) | ttft | 5.404,0 | 8.356,0 | 9.303,0 | 20 | initMs=6176.1 · rssPeakMb=2925 |
| base-8 (blocks=8, backend=GPU) | total | 14.698,0 | 19.715,0 | 23.089,0 | 20 |  |
| base-8 (blocks=8, backend=GPU) | prefill-tok-s | 365,4 | 414,9 | 420,5 | 20 |  |
| base-8 (blocks=8, backend=GPU) | decode-tok-s | 9,2 | 10,6 | 10,7 | 20 |  |

### generation-prefix · 2026-11-06 23:49 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 2794 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| base-alternating (blocks=4) | ttft | 5.631,0 | 7.226,0 | 8.440,0 | 20 |  |
| base-alternating (blocks=4) | total | 14.763,0 | 22.191,0 | 22.641,0 | 20 |  |
| base-alternating (blocks=4) | prefill-tok-s | 354,8 | 412,2 | 429,8 | 20 |  |
| base-alternating (blocks=4) | decode-tok-s | 8,4 | 10,7 | 10,8 | 20 |  |
| prefix-alternating (blocks=4) | ttft | 6.324,0 | 8.322,0 | 8.565,0 | 20 |  |
| prefix-alternating (blocks=4) | total | 15.114,0 | 21.934,0 | 22.002,0 | 20 |  |
| prefix-alternating (blocks=4) | prefill-tok-s | 254,0 | 289,9 | 388,5 | 20 |  |
| prefix-alternating (blocks=4) | decode-tok-s | 8,2 | 10,7 | 10,7 | 20 |  |

### generation-mtp · 2026-11-06 23:59 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 2513 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| base-alternating (blocks=4) | ttft | 4.414,0 | 5.504,0 | 5.643,0 | 20 |  |
| base-alternating (blocks=4) | total | 14.376,0 | 20.964,0 | 22.763,0 | 20 |  |
| base-alternating (blocks=4) | prefill-tok-s | 379,6 | 441,0 | 452,8 | 20 |  |
| base-alternating (blocks=4) | decode-tok-s | 7,2 | 8,2 | 8,9 | 20 |  |
| mtp-alternating (blocks=4) | ttft | 4.106,0 | 5.385,0 | 5.494,0 | 20 |  |
| mtp-alternating (blocks=4) | total | 11.433,0 | 14.836,0 | 17.040,0 | 20 |  |
| mtp-alternating (blocks=4) | prefill-tok-s | 410,8 | 456,0 | 463,9 | 20 |  |
| mtp-alternating (blocks=4) | decode-tok-s | 10,8 | 12,7 | 13,0 | 20 |  |

### generation-fp32 · 2026-11-07 00:12 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 2719 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| base-alternating (blocks=4) | ttft | 4.079,0 | 5.092,0 | 5.097,0 | 20 |  |
| base-alternating (blocks=4) | total | 12.291,0 | 17.879,0 | 19.933,0 | 20 |  |
| base-alternating (blocks=4) | prefill-tok-s | 411,3 | 467,8 | 484,8 | 20 |  |
| base-alternating (blocks=4) | decode-tok-s | 8,3 | 9,3 | 9,6 | 20 |  |
| fp32-alternating (blocks=4) | ttft | 5.316,0 | 7.299,0 | 7.469,0 | 20 |  |
| fp32-alternating (blocks=4) | total | 17.158,0 | 24.083,0 | 25.195,0 | 20 |  |
| fp32-alternating (blocks=4) | prefill-tok-s | 293,2 | 322,1 | 332,4 | 20 |  |
| fp32-alternating (blocks=4) | decode-tok-s | 6,5 | 7,5 | 7,6 | 20 |  |

### generation-v4-cifras · 2026-11-07 00:27 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 2782 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| base-alternating (blocks=4) | ttft | 3.419,0 | 4.428,0 | 4.497,0 | 20 |  |
| base-alternating (blocks=4) | total | 12.674,0 | 19.493,0 | 19.585,0 | 20 |  |
| base-alternating (blocks=4) | prefill-tok-s | 419,0 | 462,1 | 489,2 | 20 |  |
| base-alternating (blocks=4) | decode-tok-s | 8,3 | 8,8 | 9,1 | 20 |  |
| v4-cifras-alternating (blocks=4) | ttft | 3.477,0 | 4.447,0 | 4.469,0 | 20 |  |
| v4-cifras-alternating (blocks=4) | total | 13.278,0 | 18.934,0 | 19.269,0 | 20 |  |
| v4-cifras-alternating (blocks=4) | prefill-tok-s | 420,6 | 480,4 | 495,9 | 20 |  |
| v4-cifras-alternating (blocks=4) | decode-tok-s | 8,1 | 8,8 | 9,0 | 20 |  |

### mtp-golden · 2026-11-07 00:38 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 2903 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| mtp-vs-whole (questions=20, backend=GPU) | — | — | — | — | 0 | identical=10 · different=10 |

### repeat-golden · 2026-11-07 00:46 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 2801 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| repeat-vs-whole (questions=20, backend=GPU) | — | — | — | — | 0 | identical=20 · different=0 |

### search-10000 · 2026-11-07 00:57 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_440M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 744 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (bank=10000, embedder=EG2_440M) | vector | 47,3 | 66,3 | 68,5 | 852 | loadMs=71047.8 · reusedDatabase=false · dbBytes=59987168 |
| f32 (bank=10000, embedder=EG2_440M) | fts | 25,1 | 44,5 | 54,7 | 852 |  |
| f32 (bank=10000, embedder=EG2_440M) | embed | 135,2 | 156,0 | 173,5 | 852 |  |
| f32 (bank=10000, embedder=EG2_440M) | embed-in-search | 126,5 | 133,6 | 165,5 | 852 |  |
| f32 (bank=10000, embedder=EG2_440M) | vector-in-hybrid | 36,1 | 108,0 | 111,1 | 852 |  |
| f32 (bank=10000, embedder=EG2_440M) | fts-in-hybrid | 32,4 | 103,7 | 124,5 | 852 |  |
| f32 (bank=10000, embedder=EG2_440M) | hybrid | 173,9 | 247,3 | 260,0 | 852 |  |
| f32 (bank=10000, embedder=EG2_440M) | hybrid-folder | 190,9 | 257,2 | 287,9 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | vector | 23,2 | 41,5 | 42,4 | 852 | rebuildAndPreloadMs=73.1 · preloadMs=34.9 · usable=true · dbBytes=57208832 · recallAt20=1.000 |
| int8-rerank (bank=10000, candidates=100) | fts | 30,5 | 52,0 | 60,7 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | embed | 135,9 | 158,4 | 171,2 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | embed-in-search | 123,1 | 132,0 | 152,6 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | vector-in-hybrid | 16,1 | 73,7 | 76,0 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | fts-in-hybrid | 58,0 | 120,4 | 126,1 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | hybrid | 153,6 | 203,3 | 206,7 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | hybrid-folder | 170,6 | 222,7 | 231,7 | 852 |  |
| int8-nopreload (bank=10000) | vector | 11,8 | 12,1 | 12,2 | 852 |  |
| int8 (bank=10000) | vector | 10,3 | 10,7 | 11,4 | 852 | recallAt20=0.966 · preloadBytes=2640000 |
| f32-subset (bank=1000) | vector | 10,0 | 14,0 | 14,8 | 852 |  |
| f32-subset (bank=2500) | vector | 9,3 | 10,4 | 10,8 | 852 |  |
| f32-subset (bank=5000) | vector | 16,9 | 18,2 | 18,9 | 852 |  |

### search-50000 · 2026-11-07 01:16 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_440M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 792 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (bank=50000, embedder=EG2_440M) | vector | 176,9 | 184,1 | 189,0 | 852 | loadMs=342848.6 · reusedDatabase=false · dbBytes=277091648 |
| f32 (bank=50000, embedder=EG2_440M) | fts | 84,3 | 136,5 | 154,5 | 852 |  |
| f32 (bank=50000, embedder=EG2_440M) | embed | 132,3 | 136,5 | 139,9 | 852 |  |
| f32 (bank=50000, embedder=EG2_440M) | embed-in-search | 151,4 | 174,9 | 180,8 | 852 |  |
| f32 (bank=50000, embedder=EG2_440M) | vector-in-hybrid | 169,9 | 228,3 | 273,4 | 852 |  |
| f32 (bank=50000, embedder=EG2_440M) | fts-in-hybrid | 106,4 | 185,4 | 223,8 | 852 |  |
| f32 (bank=50000, embedder=EG2_440M) | hybrid | 331,2 | 365,3 | 398,8 | 852 |  |
| f32 (bank=50000, embedder=EG2_440M) | hybrid-folder | 338,3 | 373,5 | 390,6 | 852 |  |
| int8-rerank (bank=50000, candidates=100) | vector | 69,8 | 77,3 | 78,7 | 852 | rebuildAndPreloadMs=576.4 · preloadMs=54.3 · usable=true · dbBytes=283856896 · recallAt20=1.000 |
| int8-rerank (bank=50000, candidates=100) | fts | 85,1 | 139,7 | 157,7 | 852 |  |
| int8-rerank (bank=50000, candidates=100) | embed | 131,8 | 137,4 | 143,9 | 852 |  |
| int8-rerank (bank=50000, candidates=100) | embed-in-search | 150,9 | 175,0 | 180,6 | 852 |  |
| int8-rerank (bank=50000, candidates=100) | vector-in-hybrid | 64,8 | 133,2 | 161,7 | 852 |  |
| int8-rerank (bank=50000, candidates=100) | fts-in-hybrid | 106,6 | 192,9 | 227,5 | 852 |  |
| int8-rerank (bank=50000, candidates=100) | hybrid | 225,9 | 266,7 | 300,9 | 852 |  |
| int8-rerank (bank=50000, candidates=100) | hybrid-folder | 223,5 | 260,2 | 273,2 | 852 |  |
| int8-nopreload (bank=50000) | vector | 62,5 | 66,4 | 70,1 | 852 |  |
| int8 (bank=50000) | vector | 57,6 | 60,0 | 61,5 | 852 | recallAt20=0.960 · preloadBytes=13200000 |

### search-100000 · 2026-11-07 01:49 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_440M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 810 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (bank=100000, embedder=EG2_440M) | vector | 366,0 | 375,5 | 406,5 | 852 | loadMs=686214.6 · reusedDatabase=false · dbBytes=546866424 |
| f32 (bank=100000, embedder=EG2_440M) | fts | 166,6 | 273,7 | 300,4 | 852 |  |
| f32 (bank=100000, embedder=EG2_440M) | embed | 130,5 | 137,4 | 147,0 | 852 |  |
| f32 (bank=100000, embedder=EG2_440M) | embed-in-search | 181,8 | 189,9 | 197,6 | 852 |  |
| f32 (bank=100000, embedder=EG2_440M) | vector-in-hybrid | 381,6 | 532,8 | 597,6 | 852 |  |
| f32 (bank=100000, embedder=EG2_440M) | fts-in-hybrid | 206,5 | 324,3 | 364,0 | 852 |  |
| f32 (bank=100000, embedder=EG2_440M) | hybrid | 571,7 | 691,3 | 730,8 | 852 |  |
| f32 (bank=100000, embedder=EG2_440M) | hybrid-folder | 548,4 | 667,3 | 711,9 | 852 |  |
| int8-rerank (bank=100000, candidates=100) | vector | 146,4 | 155,4 | 164,5 | 852 | rebuildAndPreloadMs=817.6 · preloadMs=55.4 · usable=true · dbBytes=566923264 · recallAt20=1.000 |
| int8-rerank (bank=100000, candidates=100) | fts | 167,7 | 276,3 | 303,0 | 852 |  |
| int8-rerank (bank=100000, candidates=100) | embed | 130,4 | 137,3 | 144,4 | 852 |  |
| int8-rerank (bank=100000, candidates=100) | embed-in-search | 181,9 | 187,9 | 190,7 | 852 |  |
| int8-rerank (bank=100000, candidates=100) | vector-in-hybrid | 163,0 | 290,0 | 371,9 | 852 |  |
| int8-rerank (bank=100000, candidates=100) | fts-in-hybrid | 202,7 | 314,7 | 358,5 | 852 |  |
| int8-rerank (bank=100000, candidates=100) | hybrid | 349,5 | 460,1 | 504,8 | 852 |  |
| int8-rerank (bank=100000, candidates=100) | hybrid-folder | 316,2 | 435,7 | 467,5 | 852 |  |
| int8-nopreload (bank=100000) | vector | 142,1 | 146,2 | 160,1 | 852 |  |
| int8 (bank=100000) | vector | 136,0 | 139,2 | 143,1 | 852 | recallAt20=0.959 · preloadBytes=26400000 |

### search-10000 · 2026-11-07 02:46 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_270M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 677 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (bank=10000, embedder=EG2_270M) | vector | 47,3 | 66,5 | 68,1 | 852 | loadMs=1.537 · reusedDatabase=true · dbBytes=57208832 |
| f32 (bank=10000, embedder=EG2_270M) | fts | 26,8 | 55,3 | 65,9 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | embed | 135,6 | 163,2 | 172,8 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | embed-in-search | 126,3 | 133,8 | 145,2 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | vector-in-hybrid | 36,0 | 107,5 | 110,5 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | fts-in-hybrid | 33,9 | 125,6 | 131,3 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | hybrid | 174,4 | 247,4 | 264,6 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | hybrid-folder | 190,8 | 264,8 | 288,5 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | vector | 23,6 | 41,3 | 42,6 | 852 | rebuildAndPreloadMs=14.4 · preloadMs=29.6 · usable=true · dbBytes=57208832 · recallAt20=1.000 |
| int8-rerank (bank=10000, candidates=100) | fts | 32,9 | 55,4 | 63,3 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | embed | 137,9 | 165,6 | 173,2 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | embed-in-search | 123,7 | 131,2 | 134,6 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | vector-in-hybrid | 16,3 | 74,9 | 78,2 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | fts-in-hybrid | 58,1 | 124,4 | 129,6 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | hybrid | 153,7 | 206,0 | 211,0 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | hybrid-folder | 171,2 | 224,9 | 228,1 | 852 |  |
| int8-nopreload (bank=10000) | vector | 11,9 | 12,3 | 12,8 | 852 |  |
| int8 (bank=10000) | vector | 10,4 | 10,8 | 10,9 | 852 | recallAt20=0.966 · preloadBytes=2640000 |
| f32-subset (bank=1000) | vector | 8,7 | 14,1 | 14,7 | 852 |  |
| f32-subset (bank=2500) | vector | 9,1 | 10,3 | 10,9 | 852 |  |
| f32-subset (bank=5000) | vector | 16,9 | 18,3 | 19,0 | 852 |  |

### pages-1000 · 2026-11-07 03:04 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 268 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=1000) | page-search | 7,3 | 8,0 | 11,6 | 200 |  |
| int8-rerank (pages=1000, candidates=100) | page-search | 3,1 | 4,2 | 10,8 | 200 | quantizeAndPreloadMs=17.7 · recallAt5=1.000 |

### pages-5000 · 2026-11-07 03:04 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 268 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=5000) | page-search | 13,7 | 15,1 | 20,1 | 200 |  |
| int8-rerank (pages=5000, candidates=100) | page-search | 8,2 | 8,7 | 9,8 | 200 | quantizeAndPreloadMs=124.3 · recallAt5=1.000 |

### pages-10000 · 2026-11-07 03:04 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 271 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=10000) | page-search | 22,4 | 24,0 | 26,5 | 200 |  |
| int8-rerank (pages=10000, candidates=100) | page-search | 14,1 | 14,5 | 15,7 | 200 | quantizeAndPreloadMs=98.7 · recallAt5=1.000 |

### embedders · 2026-11-07 03:05 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 603 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| 440m | embed | 123,2 | 124,7 | 128,0 | 200 | rssPeakMbRound0=635 · rssPeakMbRound1=610 · rssPeakMbRound2=606 · rssPeakMbRound3=603 |
| 270m | embed | 123,2 | 124,3 | 126,6 | 200 | rssPeakMbRound0=621 · rssPeakMbRound1=615 · rssPeakMbRound2=606 · rssPeakMbRound3=601 |

### numeric-v4-default · 2026-11-07 03:08 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector - · GEMMA4_E2B (GPU, default)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 3017 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| answers (template=v4, precision=default) | ttft | 4.564,0 | 5.808,0 | 8.001,0 | 62 |  |

### numeric-v4-fp32 · 2026-11-07 03:23 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector - · GEMMA4_E2B (GPU, FLOAT32)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 3250 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| answers (template=v4, precision=fp32) | ttft | 6.692,0 | 8.844,0 | 10.524,0 | 62 |  |

### numeric-v4-cifras-default · 2026-11-07 03:40 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector - · GEMMA4_E2B (GPU, default)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 2988 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| answers (template=v4-cifras, precision=default) | ttft | 4.484,0 | 5.677,0 | 6.888,0 | 62 |  |

### sustained-base-rest0 · 2026-11-07 03:54 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector - · GEMMA4_E2B (GPU, por defecto), EG2_440M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 3345 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| sustained (minutes=25.0, restSeconds=0, label=base) | ttft | 3.599,0 | 4.630,0 | 4.659,0 | 111 | answers=111 · elapsedMinutes=25.2 |
| sustained (minutes=25.0, restSeconds=0, label=base) | total | 11.196,0 | 29.619,0 | 37.369,0 | 111 |  |
| sustained (minutes=25.0, restSeconds=0, label=base) | retrieval | 215,0 | 242,0 | 246,0 | 111 |  |
| sustained (minutes=25.0, restSeconds=0, label=base) | embed | 157,8 | 177,8 | 184,4 | 111 |  |
| sustained (minutes=25.0, restSeconds=0, label=base) | prompt | 12,0 | 36,0 | 51,0 | 111 |  |
| sustained (minutes=25.0, restSeconds=0, label=base) | engine-ttft | 3.339,0 | 4.360,0 | 4.397,0 | 111 |  |
| sustained (minutes=25.0, restSeconds=0, label=base) | prompt-tokens-est | 1.376,0 | 1.844,0 | 1.857,0 | 111 |  |

### sustained-base-rest30 · 2026-11-07 04:20 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector - · GEMMA4_E2B (GPU, por defecto), EG2_440M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 3301 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| sustained (minutes=25.0, restSeconds=30, label=base) | ttft | 3.704,0 | 5.038,0 | 5.402,0 | 34 | answers=34 · elapsedMinutes=25.0 |
| sustained (minutes=25.0, restSeconds=30, label=base) | total | 11.451,0 | 34.636,0 | 37.239,0 | 34 |  |
| sustained (minutes=25.0, restSeconds=30, label=base) | retrieval | 222,0 | 240,0 | 245,0 | 34 |  |
| sustained (minutes=25.0, restSeconds=30, label=base) | embed | 158,9 | 179,3 | 187,1 | 34 |  |
| sustained (minutes=25.0, restSeconds=30, label=base) | prompt | 13,0 | 49,0 | 52,0 | 34 |  |
| sustained (minutes=25.0, restSeconds=30, label=base) | engine-ttft | 3.423,0 | 4.778,0 | 5.095,0 | 34 |  |
| sustained (minutes=25.0, restSeconds=30, label=base) | prompt-tokens-est | 1.376,0 | 1.844,0 | 1.857,0 | 34 |  |

### visual-chat · 2026-11-07 05:06 UTC

| Medida | Valor |
|---|---:|
| model | A063 |
| sdk | 35 |
| backend | GPU |
| questions | 20 |
| switch_to_vision_ms | 13657 |
| no_page_ttft_p50_ms | 3831 |
| no_page_ttft_p95_ms | 4824 |
| fast_ttft_p50_ms | 5422 |
| fast_ttft_p95_ms | 6694 |
| balanced_ttft_p50_ms | 8305 |
| balanced_ttft_p95_ms | 9583 |
| detailed_ttft_p50_ms | 14203 |
| detailed_ttft_p95_ms | 14391 |
| rss_peak_mb | 4678 |

### visual-reload · 2026-11-07 05:06 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 3629 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| reload-without-prewarm | ttft | 15.428,0 | 15.876,0 | 15.876,0 | 5 |  |
| reload-with-prewarm | ttft | 8.811,0 | 9.421,0 | 9.421,0 | 5 | typingMs=5000 |

### visual-sustained · 2026-11-07 05:22 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 3836 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| sustained-balanced (minutes=25) | ttft | 8.440,0 | 9.149,0 | 15.367,0 | 72 | answers=72 |

### generation-e4b · 2026-11-07 05:58 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector -
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 4512 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| e4b-4 (blocks=4, backend=GPU) | ttft | 10.767,0 | 13.643,0 | 14.167,0 | 20 | initMs=22813.7 · rssPeakMb=4512 |
| e4b-4 (blocks=4, backend=GPU) | total | 26.397,0 | 37.906,0 | 38.842,0 | 20 |  |
| e4b-4 (blocks=4, backend=GPU) | prefill-tok-s | 129,7 | 149,0 | 149,1 | 20 |  |
| e4b-4 (blocks=4, backend=GPU) | decode-tok-s | 5,2 | 5,3 | 5,3 | 20 |  |

### search-10000 · 2026-11-07 06:10 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_270M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 749 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (bank=10000, embedder=EG2_270M) | vector | 46,8 | 65,7 | 67,2 | 852 | loadMs=3.511 · reusedDatabase=true · dbBytes=57208832 |
| f32 (bank=10000, embedder=EG2_270M) | fts | 28,2 | 57,5 | 72,2 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | embed | 136,6 | 163,8 | 176,7 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | embed-in-search | 125,9 | 133,5 | 146,8 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | vector-in-hybrid | 35,2 | 106,0 | 109,1 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | fts-in-hybrid | 34,6 | 127,3 | 136,8 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | hybrid | 173,5 | 247,2 | 261,9 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | hybrid-folder | 191,0 | 243,5 | 287,2 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | vector | 22,0 | 40,6 | 43,0 | 852 | rebuildAndPreloadMs=13.1 · preloadMs=31.8 · usable=true · dbBytes=57208832 · recallAt20=1.000 |
| int8-rerank (bank=10000, candidates=50) | fts | 33,2 | 56,7 | 62,0 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | embed | 139,6 | 166,1 | 184,0 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | embed-in-search | 123,2 | 133,0 | 180,9 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | vector-in-hybrid | 16,6 | 73,1 | 75,9 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | fts-in-hybrid | 59,0 | 123,5 | 129,7 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | hybrid | 158,4 | 203,9 | 213,2 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | hybrid-folder | 170,6 | 221,4 | 226,1 | 852 |  |
| int8-nopreload (bank=10000) | vector | 11,6 | 12,0 | 12,2 | 852 |  |
| int8 (bank=10000) | vector | 10,3 | 10,6 | 10,8 | 852 | recallAt20=0.966 · preloadBytes=2640000 |
| int8-rerank (bank=10000, candidates=100) | vector | 33,1 | 41,4 | 43,1 | 852 | recallAt20=1.000 |
| int8-rerank (bank=10000, candidates=100) | fts | 31,5 | 51,8 | 61,1 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | embed | 139,4 | 155,3 | 163,6 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | embed-in-search | 122,1 | 127,5 | 132,2 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | vector-in-hybrid | 57,2 | 72,7 | 74,1 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | fts-in-hybrid | 65,6 | 118,5 | 125,5 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | hybrid | 186,3 | 200,3 | 203,8 | 852 |  |
| int8-rerank (bank=10000, candidates=100) | hybrid-folder | 204,3 | 220,8 | 223,0 | 852 |  |
| f32-subset (bank=1000) | vector | 7,9 | 10,1 | 13,9 | 852 |  |
| int8-subset (bank=1000, candidates=50) | vector | 6,6 | 7,7 | 8,2 | 852 |  |
| f32-subset (bank=2500) | vector | 9,0 | 10,4 | 13,3 | 852 |  |
| int8-subset (bank=2500, candidates=50) | vector | 8,0 | 11,4 | 12,6 | 852 |  |
| f32-subset (bank=5000) | vector | 16,9 | 17,9 | 18,7 | 852 |  |
| int8-subset (bank=5000, candidates=50) | vector | 7,6 | 8,7 | 9,7 | 852 |  |
| f32-subset (bank=7500) | vector | 25,9 | 27,1 | 27,9 | 852 |  |
| int8-subset (bank=7500, candidates=50) | vector | 10,9 | 12,0 | 12,6 | 852 |  |

### pages-1000 · 2026-11-07 06:38 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 265 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=1000) | page-search | 7,4 | 8,3 | 11,7 | 200 |  |
| int8-rerank (pages=1000, candidates=100) | page-search | 11,9 | 15,2 | 19,8 | 200 | quantizeAndPreloadMs=14.9 · usable=true · recallAt5=1.000 |

### pages-5000 · 2026-11-07 06:38 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 265 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=5000) | page-search | 13,9 | 15,2 | 22,8 | 200 |  |
| int8-rerank (pages=5000, candidates=100) | page-search | 10,9 | 12,7 | 13,4 | 200 | quantizeAndPreloadMs=61.5 · usable=true · recallAt5=1.000 |

### pages-10000 · 2026-11-07 06:38 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 275 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=10000) | page-search | 23,0 | 23,9 | 25,7 | 200 |  |
| int8-rerank (pages=10000, candidates=100) | page-search | 16,0 | 17,1 | 17,6 | 200 | quantizeAndPreloadMs=37.0 · usable=true · recallAt5=1.000 |

### search-10000 · 2026-11-07 06:44 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_270M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 657 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (bank=10000, embedder=EG2_270M) | vector | 46,1 | 65,1 | 67,0 | 852 | loadMs=1.544 · reusedDatabase=true · dbBytes=57208832 |
| f32 (bank=10000, embedder=EG2_270M) | fts | 26,9 | 56,0 | 69,4 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | embed | 130,7 | 155,9 | 169,0 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | embed-in-search | 125,1 | 135,2 | 185,2 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | vector-in-hybrid | 35,3 | 105,1 | 108,4 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | fts-in-hybrid | 36,2 | 121,1 | 129,4 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | hybrid | 172,5 | 249,0 | 273,9 | 852 |  |
| f32 (bank=10000, embedder=EG2_270M) | hybrid-folder | 185,8 | 271,9 | 288,1 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | vector | 8,2 | 9,8 | 30,1 | 852 | rebuildAndPreloadMs=10.7 · preloadMs=21.4 · usable=true · dbBytes=57208832 · recallAt20=1.000 |
| int8-rerank (bank=10000, candidates=50) | fts | 36,4 | 57,8 | 65,9 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | embed | 128,5 | 137,1 | 143,4 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | embed-in-search | 128,7 | 137,1 | 140,3 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | vector-in-hybrid | 3,1 | 18,1 | 28,3 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | fts-in-hybrid | 38,3 | 94,2 | 113,5 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | hybrid | 144,2 | 154,0 | 163,8 | 852 |  |
| int8-rerank (bank=10000, candidates=50) | hybrid-folder | 158,1 | 184,9 | 189,0 | 852 |  |
| int8-nopreload (bank=10000) | vector | 13,9 | 14,4 | 14,7 | 852 |  |
| int8 (bank=10000) | vector | 12,3 | 12,8 | 13,2 | 852 | recallAt20=0.966 · preloadBytes=2640000 |
| f32-subset (bank=1000) | vector | 5,7 | 8,6 | 10,4 | 852 |  |
| int8-subset (bank=1000, candidates=50) | vector | 2,7 | 5,4 | 6,9 | 852 |  |
| f32-subset (bank=2500) | vector | 9,7 | 11,2 | 12,2 | 852 |  |
| int8-subset (bank=2500, candidates=50) | vector | 5,6 | 7,8 | 13,0 | 852 |  |
| f32-subset (bank=5000) | vector | 20,0 | 21,9 | 22,7 | 852 |  |
| int8-subset (bank=5000, candidates=50) | vector | 6,9 | 11,6 | 18,4 | 852 |  |
| f32-subset (bank=7500) | vector | 29,4 | 31,6 | 32,3 | 852 |  |
| int8-subset (bank=7500, candidates=50) | vector | 7,6 | 15,7 | 24,9 | 852 |  |

### search-50000 · 2026-11-07 07:01 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_440M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 620 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (bank=50000, embedder=EG2_440M) | vector | 208,9 | 216,0 | 221,1 | 426 | loadMs=3.845 · reusedDatabase=true · dbBytes=283856896 |
| f32 (bank=50000, embedder=EG2_440M) | fts | 98,4 | 158,9 | 172,4 | 426 |  |
| f32 (bank=50000, embedder=EG2_440M) | embed | 134,8 | 142,9 | 157,5 | 426 |  |
| f32 (bank=50000, embedder=EG2_440M) | embed-in-search | 157,7 | 183,2 | 195,1 | 426 |  |
| f32 (bank=50000, embedder=EG2_440M) | vector-in-hybrid | 203,0 | 293,4 | 312,1 | 426 |  |
| f32 (bank=50000, embedder=EG2_440M) | fts-in-hybrid | 110,3 | 223,5 | 242,0 | 426 |  |
| f32 (bank=50000, embedder=EG2_440M) | hybrid | 380,4 | 446,4 | 472,0 | 426 |  |
| f32 (bank=50000, embedder=EG2_440M) | hybrid-folder | 374,4 | 440,0 | 489,7 | 426 |  |
| int8-rerank (bank=50000, candidates=50) | vector | 13,1 | 16,7 | 17,7 | 426 | rebuildAndPreloadMs=64.1 · preloadMs=37.4 · usable=true · dbBytes=283856896 · recallAt20=1.000 |
| int8-rerank (bank=50000, candidates=50) | fts | 105,0 | 163,3 | 181,0 | 426 |  |
| int8-rerank (bank=50000, candidates=50) | embed | 133,2 | 137,8 | 141,6 | 426 |  |
| int8-rerank (bank=50000, candidates=50) | embed-in-search | 157,8 | 181,4 | 188,7 | 426 |  |
| int8-rerank (bank=50000, candidates=50) | vector-in-hybrid | 6,9 | 85,8 | 112,2 | 426 |  |
| int8-rerank (bank=50000, candidates=50) | fts-in-hybrid | 106,8 | 209,1 | 236,7 | 426 |  |
| int8-rerank (bank=50000, candidates=50) | hybrid | 177,4 | 224,4 | 252,1 | 426 |  |
| int8-rerank (bank=50000, candidates=50) | hybrid-folder | 239,2 | 322,2 | 361,7 | 426 |  |
| int8-nopreload (bank=50000) | vector | 75,0 | 78,8 | 81,0 | 426 |  |
| int8 (bank=50000) | vector | 68,8 | 71,3 | 73,1 | 426 | recallAt20=0.960 · preloadBytes=13200000 |

### search-100000 · 2026-11-07 07:16 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_440M (CPU, por defecto)
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 659 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (bank=100000, embedder=EG2_440M) | vector | 432,7 | 441,0 | 444,9 | 426 | loadMs=2.146 · reusedDatabase=true · dbBytes=566923264 |
| f32 (bank=100000, embedder=EG2_440M) | fts | 190,8 | 310,6 | 337,2 | 426 |  |
| f32 (bank=100000, embedder=EG2_440M) | embed | 133,4 | 137,6 | 139,1 | 426 |  |
| f32 (bank=100000, embedder=EG2_440M) | embed-in-search | 188,8 | 193,5 | 195,5 | 426 |  |
| f32 (bank=100000, embedder=EG2_440M) | vector-in-hybrid | 452,4 | 597,5 | 694,4 | 426 |  |
| f32 (bank=100000, embedder=EG2_440M) | fts-in-hybrid | 212,7 | 342,6 | 393,4 | 426 |  |
| f32 (bank=100000, embedder=EG2_440M) | hybrid | 647,2 | 777,5 | 833,7 | 426 |  |
| f32 (bank=100000, embedder=EG2_440M) | hybrid-folder | 608,5 | 741,0 | 778,5 | 426 |  |
| int8-rerank (bank=100000, candidates=50) | vector | 13,7 | 22,4 | 23,4 | 426 | rebuildAndPreloadMs=101.6 · preloadMs=54.8 · usable=true · dbBytes=566923264 · recallAt20=1.000 |
| int8-rerank (bank=100000, candidates=50) | fts | 193,1 | 313,3 | 343,9 | 426 |  |
| int8-rerank (bank=100000, candidates=50) | embed | 133,1 | 138,2 | 141,4 | 426 |  |
| int8-rerank (bank=100000, candidates=50) | embed-in-search | 188,9 | 193,3 | 195,1 | 426 |  |
| int8-rerank (bank=100000, candidates=50) | vector-in-hybrid | 32,5 | 172,6 | 260,8 | 426 |  |
| int8-rerank (bank=100000, candidates=50) | fts-in-hybrid | 210,8 | 334,6 | 382,1 | 426 |  |
| int8-rerank (bank=100000, candidates=50) | hybrid | 229,8 | 352,1 | 399,2 | 426 |  |
| int8-rerank (bank=100000, candidates=50) | hybrid-folder | 343,3 | 476,1 | 511,0 | 426 |  |
| int8-nopreload (bank=100000) | vector | 170,9 | 175,2 | 178,8 | 426 |  |
| int8 (bank=100000) | vector | 160,9 | 196,7 | 242,9 | 426 | recallAt20=0.959 · preloadBytes=26400000 |

### pages-1000 · 2026-11-07 07:39 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 255 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=1000) | page-search | 7,1 | 8,4 | 10,8 | 200 |  |
| int8-rerank (pages=1000, candidates=50) | page-search | 8,3 | 11,6 | 15,0 | 200 | quantizeAndPreloadMs=15.0 · usable=true · recallAt5=1.000 |

### pages-5000 · 2026-11-07 07:40 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 271 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=5000) | page-search | 14,7 | 16,6 | 17,1 | 200 |  |
| int8-rerank (pages=5000, candidates=50) | page-search | 11,7 | 14,6 | 15,3 | 200 | quantizeAndPreloadMs=58.0 · usable=true · recallAt5=1.000 |

### pages-10000 · 2026-11-07 07:40 UTC

Nothing A063, Android API 35 · LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0
Térmico NONE → NONE · batería 100 → 100 % · cargando · pico de RSS 279 MB · estado **completed**

| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |
|---|---|---:|---:|---:|---:|---|
| f32 (pages=10000) | page-search | 25,5 | 27,5 | 28,1 | 200 |  |
| int8-rerank (pages=10000, candidates=50) | page-search | 16,8 | 18,0 | 18,4 | 200 | quantizeAndPreloadMs=42.0 · usable=true · recallAt5=1.000 |

<!-- device-report:end -->

# Research: Rendimiento en dispositivo y decisión INT8 (F14)

Investigación del plan (2026-11-06). Las comprobaciones se hicieron en el escritorio:
- los AAR de Google Maven y de Maven Central, pasados por `javap`;
- las fuentes de LiteRT-LM (tags v0.18.1 y v0.19.1) y de SQLite-Vector (1.1.2 y 1.2.0);
- builds de macOS de SQLite-Vector, con SQL real;
- el código de la app y del harness.

Nada se ha medido aún en el teléfono. Las cifras del Mac son orientativas.

## R1. Runtimes nuevos (D-088, FR-019)

- **Decisión:** al empezar la implementación se suben LiteRT-LM a **0.19.1** (Android) y `litert-lm-api` a **0.19.1** (escritorio), y SQLite-Vector a **1.2.0**. El orden es:
  1. compilación, JVM, lint y emuladores en la implementación;
  2. en el A063, en una **sesión corta justo después** y antes de construir las palancas (D-090): `embedding_golden` y las goldens de respuesta, unos 15 minutos y sin datos del usuario. Así, si fallan, se vuelve a 0.18.1 y 1.1.2 antes de que exista código que dependa de 0.19 (`clone()` y la especulativa);
  3. la batería completa del teléfono va al final, con las medidas.

  En el escritorio también suben `litert-lm-api` a 0.19.1 y `sqliteai-vector` a 1.2.0. Las 426 respuestas de `v4` se regeneran con 0.19.1 y las que cambian se juzgan a mano: esa es la base de las palancas (D-090).
- **LiteRT-LM 0.19.x frente a 0.18.1:**
  - el `classes.jar` de 0.19.0 y el de 0.19.1 son idénticos; solo cambia el `.so` nativo;
  - **nuevo:** `Conversation.clone()` y `Session.clone()`, que copian historial, caché KV y configuración (R6);
  - **nuevo:** `SpeculativeDecodingConfig(mode = OFF | ON | ADAPTIVE)`, en `EngineConfig`, `ConversationConfig` y `SessionConfig`. `enableSpeculativeDecoding` queda `@Deprecated`, pero sigue funcionando: `LiteRtTextGenerator.conversationConfig()` pasa al campo nuevo (R7);
  - `BenchmarkInfo` añade `maxNumTokens` y `markDurationsInSecond` (por ejemplo, `vision_executor`);
  - `benchmark()` cambia de firma, pero la app no lo usa;
  - sin cambios en `Engine`, `Backend`, `ActivationDataType`, `SamplerConfig`, `Contents`, `visionBackend`, `ModelInfo` ni en el embedder;
  - las notas de 0.19.0 dicen que el muestreo en GPU iguala al de CPU con la misma semilla. Es una razón más para repetir las goldens de respuesta.
- **SQLite-Vector 1.2.0 frente a 1.1.2:**
  - «No API changed and no index needs rebuilding»: 10 arreglos, 3 de ellos de memoria;
  - el AAR tiene las mismas ABIs, el mismo `vector.so`, el mismo punto de entrada y alineación de 16 KB, así que D-031 sigue valiendo;
  - `vector_as_*(blob, dim)` exige ahora la dimensión, pero la app usa la forma de un argumento;
  - si `vector_quantize` falla, deja el contexto como estaba.
- **Nativos:** el AAR 0.19.1 trae `jni/arm64-v8a/liblitertlm_jni.so` (22,7 MB) y `jni/x86_64/liblitertlm_jni.so` (26,9 MB), como 0.18.1 (22,2 y 26,4 MB). SQLite-Vector 1.2.0 sigue con `vector.so` para arm64-v8a, armeabi-v7a y x86_64.
- **Constitución:** «Restricciones técnicas» fija «LiteRT-LM 0.18.x» y «`litert-lm-api` 0.18.x». Adoptar 0.19.1 exige una enmienda **PATCH (1.2.0 → 1.2.1)**, aprobada en la parada del analyze (D-090). Es PATCH porque solo cambia el número de versión de una restricción técnica, sin tocar ningún principio, y la II ya exige las goldens en cada cambio de versión. Se aplica con `/speckit-constitution` cuando las goldens pasen en el teléfono. La constitución II ya pide el `embedding_golden` en cada cambio de versión de LiteRT-LM.
- **Alternativas descartadas:** probarlos al final como palanca, o no tocarlos (D-088).

## R2. Bancos de 10k, 50k y 100k fragmentos (FR-001–FR-003)

- **Decisión:** los bancos se construyen en el escritorio y se cargan en el teléfono con el código de la app. Pasos:
  1. **Textos:** fragmentos sintéticos hechos de frases reales del corpus de F05, recombinadas por documento con semilla fija hasta la longitud del troceado de D-034 (objetivo 1.594 caracteres, máximo 2.113). Cada fragmento lleva su cabecera contextual, como los reales.
  2. **Vectores:** embebidos de verdad con la 270M del escritorio (CPU), cuyos vectores coinciden con los del teléfono por el `embedding_golden` (coseno ≥ 0,99). Así la distribución de los vectores y el vocabulario de la búsqueda por palabras son los reales.
  3. **Documentos y carpetas:** unos 25 fragmentos por documento (como el corpus de F05: 1.913 fragmentos en 77 documentos), repartidos en 20 carpetas, con el 30 % de los documentos excluidos (F10 usó 4.300 ocultos sobre 14.300).
  4. **Formato de intercambio:** `offline/build/bench/bank-<N>/` con `chunks.jsonl.gz` (texto, cabecera, documento, carpeta y estado) y `vectors.f32` (N × 256 float32), más un `manifest.json` con los SHA-256. Fuera de git.
  5. **En el teléfono:** se copian con `adb` a `getExternalFilesDir("dev-bench")`, como `dev-models` y `dev-visual`. La prueba instrumentada verifica el manifiesto y construye una base aparte en `cacheDir/bench/bank-<N>.db` con `DefaultRagIndex` (el mismo esquema, normalizador y triggers que la app). Si la base ya existe y su manifiesto coincide, la reutiliza. Una prueba de limpieza borra `cacheDir/bench` y `dev-bench` al terminar.
- **Coste estimado:** 100k embeddings en el Mac a unos 30–60 ms por fragmento son 50–100 minutos, una sola vez y con caché por fragmento. El banco de 100k ocupa unos 260 MB para copiar, y la base, del orden de 0,5 GB en el teléfono.
- **Preguntas:** las 370 preguntas puntuables de F05 (FR-005: dan un p99 con sentido), que incluyen las 50 de la muestra de F10. Así las cifras de 10k se comparan con D-052.
- **Alternativas descartadas:**
  - vectores aleatorios o perturbados para los fragmentos: el recall de INT8 depende de la distribución real (en el Mac, datos agrupados sintéticos dieron 0,957 a 100k, R3);
  - construir la base en Python: obligaría a replicar el esquema v5, los triggers y el normalizador fuera de la app;
  - generar los bancos en el teléfono: embeber 100k fragmentos en CPU llevaría un día;
  - `/data/local/tmp`: SELinux no deja leerlo a la app.

## R3. INT8 con SQLite-Vector (FR-006–FR-012)

- **API** (igual en 1.1.2 y 1.2.0, verificada con SQL real):
  - `vector_quantize(t, c, 'qtype=INT8,max_memory=…')` reconstruye **entero**. Hace DROP y CREATE de `vector0_<tabla>_<col>` dentro del `.db` y recorre todas las filas con una escala global. **Persiste:** al reabrir basta `vector_init`;
  - hay que poner `qtype=INT8` explícito: con coseno, UINT8 da un recall del 33,8 %. `max_memory` es de 30 MB por defecto; 100k × 264 B = 26,4 MB cabe justo, así que se pone explícito con margen;
  - `vector_quantize_preload(t, c)` carga en la memoria **de la conexión** (no compartida, verificado). La app tiene una sola conexión, en el hilo `microrag-db`;
  - `vector_quantize_scan(t, c, vec[, k])`: con k da el top-k; sin k es streaming y admite WHERE, JOIN y `ORDER BY distance LIMIT`, aplicados después de puntuar todas las filas. Las consultas de F17 y F20 valen cambiando el nombre del módulo;
  - las distancias son aproximadas (0,76002 frente a 0,76087);
  - **obsoleto tras cambios:** una fila nueva no aparece, una borrada sigue saliendo y un UPDATE conserva el vector viejo. Como `chunks.id` no es AUTOINCREMENT, un rowid reutilizado podría apuntar a otro fragmento. Por eso INT8 solo se usa con `quant_dirty = 0`.
- **Cifras del Mac** (256D, datos agrupados sintéticos, k = 20; orientativas):

  | Filas | F32 | INT8 streaming | INT8 precargado | Memoria | Recall@20 | Cuantizar |
  |---:|---:|---:|---:|---:|---:|---:|
  | 20.000 | 4,08 ms | 0,74 ms | 0,28 ms | 5,3 MB | 0,972 | 19 ms |
  | 100.000 | 20,75 ms | 4,11 ms | 1,39 ms | 26,4 MB | 0,957 | 97 ms |

- **Decisión:**
  - **Búsqueda:** con el índice al día y precargado, INT8 preselecciona `INT8_CANDIDATES` (100) y la app los **reordena con la distancia exacta**: lee sus blobs F32 y calcula 1 − producto escalar en Kotlin. No hay función escalar de distancia, y `vector_full_scan` no filtra por rowid. El filtro `SEARCHABLE`, el ámbito y `k + m` siguen como hoy. En la prueba del Mac, el top-10 tras reordenar el top-50 INT8 coincide con el exacto.
  - El banco mide el Recall@20 con y sin reordenación, frente a F32. FR-008 se cumple si la versión que usa la app da ≥ 0,97.
  - **Estado** en `rag_meta`:
    - `quant_dirty` (ya existe) para los fragmentos;
    - una clave nueva `pages_quant_dirty` para las páginas, que solo se usa si R10 lo decide.

    Se ponen a 1 en cada cambio (hoy ya lo hacen `insertChunks`, `deleteDocument`, `publishReindex`, `removeFromIndex` y `deleteChunks`; se añaden las páginas). Pasan a 0 solo tras un `vector_quantize` completo.
  - **Precarga:** marca de memoria `int8Preloaded` en `DefaultRagIndex`, que se pierde con la conexión. Se pone a falso en `markQuantDirty` y al empezar `refreshQuantized`, para que una precarga fallida nunca deje en uso una instantánea anterior. La búsqueda usa INT8 solo si `quant_dirty = 0` y `int8Preloaded`; si no, usa la exacta.
  - **Mantenimiento** (componente nuevo `QuantizedIndexMaintainer`, en `data/rag`):
    - **disparadores:**
      - el fin de una tanda de ingesta (`IngestRunner.finishRun`);
      - las operaciones de la Biblioteca que borran o cambian fragmentos (borrar, quitar del índice, reindexar y descartar, `DefaultLibraryRepository`). «Quitar de la cola» un documento ya pasa por `DeleteDocumentUseCase` y `delete`;
      - el arranque de la app (`MicroRagApplication.onCreate`, junto a los trabajos que ya abren el índice);
    - **acción:** si `quant_dirty = 1`, no hay tanda activa y hay al menos `INT8_MIN_CHUNKS` fragmentos buscables, reconstruye y precarga. Si está al día, solo precarga;
    - **coalescencia:** varios disparadores seguidos dan una sola reconstrucción (espera de 2 s);
    - **ejecución:** en el hilo de la base, como toda escritura (constitución IV). Bloquea las búsquedas lo que dure `vector_quantize`, que se mide (R3, FR-007; en el Mac, 97 ms con 100k);
    - **ante un fallo:** muerte del proceso, falta de espacio o error. `quant_dirty` sigue a 1, la búsqueda sigue con la exacta y se reintenta en el siguiente disparador (constitución V).
  - **Criterio de 20 ms (D-090):** sobre la búsqueda vectorial **sola**, como F04 (23,1 ms con 10k). Dentro de la híbrida (52 ms en D-052) espera en cola detrás de la léxica, y también se informa.
  - **`INT8_MIN_CHUNKS`:** sale del banco. Es el tamaño a partir del cual el p95 F32 solo pasa de 20 ms, redondeado hacia abajo; por debajo, la app se queda en F32 (constitución IV: «solo si»). Se mide con subconjuntos de 1k, 2,5k y 5k del banco de 10k. **Hasta medirlo es 10.000**, donde F04 ya pasa de 20 ms. Además, así la biblioteca del usuario del A063 queda por debajo y las pruebas instrumentadas, que arrancan la `Application`, no la cambian (SC-010).
  - **Espera de las búsquedas:** la reconstrucción y la precarga ocupan el hilo único de la base. El banco mide cuánto espera una búsqueda que llega durante ellas (FR-009, FR-011).
  - **Detalle técnico:** `tableNames()` solo se usa para detectar ficheros vacíos, así que las tablas `vector0_*` no lo afectan. Una prueba lo fija.
- **Alternativas descartadas:**
  - INT8 sin reordenar: recall por debajo de 0,97 a 100k en el Mac;
  - reconstruir en otra conexión: `vector_quantize` escribe, y solo hay una conexión de escritura;
  - reconstrucción incremental: la API no la tiene;
  - 1BIT o UINT8: peor recall con coseno.

## R4. Medidas en el teléfono: método común (FR-004, FR-005, FR-036)

- **Decisión:** un kit de medida en `androidTest/…/testing/bench/`:
  - **`DeviceConditions`:** estado térmico y margen (`PowerManager.getCurrentThermalStatus` y `getThermalHeadroom`), batería, temperatura y carga (`ACTION_BATTERY_CHANGED`), RSS actual y pico (`/proc/self/status`: `VmRSS` y `VmHWM`), heap Java y tamaño de la base;
  - **`BenchReport`:** escribe JSON en `filesDir/reports/f14-<banco>-<fecha>.json`, como `VisualChatBenchmarkTest`. Lleva dispositivo, Android, versiones de app y runtimes, modelos y huellas, backend, precisión, condiciones al principio y al final, y las muestras;
  - **percentiles:** nearest-rank, los mismos que ya usan los bancos de F10 y F11;
  - **protocolo:** calentamiento descartado (5 preguntas); en búsqueda, 2 vueltas de las 370 preguntas; en generación, 20 preguntas por variante en la medida corta y las de la sesión en la larga; pantalla despierta (`svc power stayon usb`) y estado térmico registrado en cada muestra.

  En el escritorio, `offline/eval/device_bench.py` lee los JSON (copiados con `run-as` a `offline/build/device/`, fuera de git) y escribe las tablas de `benchmarks.md`. Así no se copian cifras a mano.
- **Privacidad:** los JSON pueden llevar preguntas del conjunto de prueba y respuestas generadas sobre el corpus público. Nunca datos del usuario, y nada va a logcat salvo los resúmenes numéricos, como hoy.
- **Alternativas descartadas:**
  - Macrobenchmark o Jetpack Benchmark: medir el motor con el código y los hilos reales es lo que piden los bancos;
  - pantalla de diagnóstico nueva: los bancos van en pruebas instrumentadas (son largos y no necesitan interacción), y el diagnóstico de F02 sigue en debug para lo manual.

## R5. Generación: 4, 6 y 8 bloques, extremo a extremo y sesión sostenida (FR-014–FR-016)

- **Prompts:** los de la app (`PromptBuilder` v4) con 4, 6 y 8 bloques reales del corpus de F05, elegidos por el selector de D-034 subiendo `MAX_BLOCKS` y el presupuesto solo en la prueba. Los tokens reales se leen de `BenchmarkInfo.lastPrefillTokenCount` (requiere `ExperimentalFlags.enableBenchmark`, solo en la prueba), junto a la estimación.
- **Extremo a extremo:** la sesión de chat real (`DefaultChatSession.ask`) sobre el índice del corpus, con el desglose: embedding, búsqueda, construcción del prompt (con su límite) y prefill hasta el primer token. Ya existen los tiempos de `ask` y `ttftMs`; se añade el desglose al registro de la prueba.
- **Sesión sostenida** (`SustainedChatBenchmarkTest`): respuestas completas (no se cancelan en el primer token), con las preguntas de F05 en orden fijo.
  - **Sin reposo:** 25 minutos seguidos.
  - **Con reposo:** 30 s entre preguntas, 25 minutos.

  Por pregunta: primer token, total, tokens, estado térmico y batería. Se resume en tramos de 5 minutos, y la batería por cada 10 minutos. Si el estado térmico llega a `SEVERE`, la sesión para y queda registrado.
- **Qué decide las palancas (D-090):** el banco corto de `GenerationBenchmarkTest`, 20 preguntas alternando la base y la palanca, pregunta a pregunta, en las mismas condiciones térmicas. Una sesión sostenida por palanca serían unas 3 horas más de teléfono. La sesión sostenida sin reposo con la base, y la final con todas las adoptadas, confirman SC-004.

## R6. Reutilizar el prefijo fijo del prompt (FR-017)

- **Qué hay:** solo existe desde 0.19.x (R1). En 0.18.1 no hay clone ni forma de rebobinar.
- **Prefijo:** desde «Responde a la PREGUNTA…» hasta la línea en blanco tras las reglas de `v4`. Son unos 900 caracteres, unos 200 tokens, y no cambian entre preguntas del mismo idioma. Con la pendiente de D-057 (3,1 s por cada 1.000 tokens) ahorraría **unos 0,6 s** de primer token, por encima del umbral de 0,3 s.
- **Decisión:** dos caminos, probados en este orden:
  1. **Sin cambiar el prompt** (preferido): `Session` de bajo nivel con la plantilla de chat de Gemma 4 escrita a mano. Se hace el prefill del turno de usuario hasta el final de las reglas una vez por motor, se clona la sesión por pregunta y se añade el resto (conversación previa, fuentes, pregunta y el cierre del turno).

     La secuencia de tokens es la misma que hoy, así que no es un cambio de prompt. La prueba de igualdad: con muestreo greedy, las respuestas de las goldens deben salir idénticas por los dos caminos.

     **A verificar en la implementación (T del setup):** que `Session` en 0.19.1 permite decodificar en streaming y clonar después del prefill, y cuál es la plantilla exacta (los tokens del turno se leen del fichero con `ModelInfo` o de la plantilla Jinja que lleva).
  2. **Con rol de sistema:** `ConversationConfig(systemInstruction = reglas, prefillPrefaceOnInit = true)`, con `clone()` por pregunta. Cambia el prompt, así que pasa por el harness (`template-review`, con `litert-lm-api` 0.19.1 y el mismo rol de sistema) con la regla de FR-017.
- **Memoria:** el clon copia la caché KV de la base, y eso se mide (RSS pico). Se libera con el motor (`onTrimMemory`, ingesta), y la base se vuelve a preparar en el `warmUp`.
- **Alcance:** las respuestas de texto. Las de páginas comparten las reglas `v4` más `PAGE_RULE`; se mide si el mismo mecanismo vale con imágenes, y si no, quedan fuera.
- **Igualdad:** con el camino 1, las respuestas greedy deben salir idénticas. Si alguna cambia (un prefill partido en GPU puede divergir en algún token), `PrefixCacheGoldenTest` informa las diferencias, y las que cambian se juzgan con la regla de calidad de FR-017 antes de adoptar.
- **Plantilla más corta (D-090):** con el prefijo reutilizado, las reglas ya no cuestan prefill. La `v4-corta` solo se evalúa si el prefijo no está disponible o no se adopta; si no, se descarta con ese motivo.
- **Interruptor:** `GenerationConfig.prefixCache`, con su valor por defecto en `RagParameters` según el resultado de la fase del teléfono.
- **Alternativas descartadas:** `CachedSession` o `PrefixCache` de C++, que no están expuestos en Kotlin.

## R7. Decodificación especulativa (MTP)

- **Qué hay:** el fichero genérico de Gemma 4 E2B trae un drafter (`tf_lite_mtp_drafter`). La ficha de Hugging Face avisa de que las copias anteriores al 5 de mayo de 2026 no lo tienen. La de la app es de octubre de 2026, y se comprueba en el teléfono con `ModelInfo…hasSpeculativeDecodingSupport()`.
- **Modo:** `SpeculativeDecodingMode.ADAPTIVE`, que solo especula mientras mide que va más rápido.
- **Qué acelera:** solo la decodificación; el prefill, no. En el S26 Ultra con GPU, la ficha da de 51,5 a 66–92 tokens/s.
- **Encaje con la regla:** no baja el primer token, así que no cumple la regla de FR-017, que es sobre el primer token. Pero acorta la respuesta completa y, con greedy, no debería cambiar el texto.
- **Criterio aprobado (D-090):** se adopta si sube la decodificación al menos un 20 %, no empeora el p95 del primer token más de 0,3 s y las respuestas greedy de las goldens no cambian.
- **Interruptor:** `GenerationConfig.speculative` (OFF o ADAPTIVE).

## R8. fp16 y FP32 (FR-020, FR-021)

- **Qué hay:** la app ya pasa `activationDataType` (`GenerationConfig.activationPrecision`). Con null manda el fichero, y el E2B genérico declara fp16 para el texto y para el adaptador de visión. No está verificado que el backend Artisan lo respete, pero la app no usa ese fichero (D-023).
- **Conjunto de preguntas con cifras** (`offline/testset/numeric/`): unas 60 preguntas sobre fragmentos llenos de cifras del corpus de F05 (tablas del BOE, presupuestos, plazos) y del texto de las páginas de F18. Cada una con sus cifras esperadas.
- **Detector automático:** busca cifras de la respuesta que no estén en las fuentes, normalizando separadores de miles y decimales. Las marcadas se revisan a mano (D-033).
- **Dónde se mide:**
  - **escritorio:** la GPU del Mac con fp16 reproduce el fallo «a menudo» (F18), así que sirve para afinar el conjunto y la instrucción sin el teléfono;
  - **teléfono:** decide la tasa real y el primer token, con las dos precisiones sobre las mismas 60 preguntas.
- **Regla (D-088):**
  - FP32 se adopta si el p95 del primer token sube ≤ 0,5 s en el banco corto, con las palancas ya adoptadas (R6 podría compensar parte del coste), y la sesión sostenida final lo confirma.
  - Si no, se evalúa una instrucción más fuerte. `v4` ya tiene «Copia cifras, plazos, importes y números de artículo tal como aparecen en la fuente que citas.», y los 2 fallos de 60 ocurrieron con ella. La variante candidata (`v4-cifras`) mueve la regla al final, junto a la PREGUNTA, y pide copiar cada cifra «dígito a dígito». Se adopta si da como mucho la mitad de respuestas con cifras deformadas que `v4` en el teléfono y cumple la regla de calidad de FR-017 en el harness (D-090).
  - **Alcance (D-090):** texto y páginas. `buildVisual` toma la plantilla de `PROMPT_TEMPLATE`, así que la v5-visual cambia con ella. Se actualizan `visual_prompt.py` y `VisualPromptGoldenTest`, y las respuestas con páginas se miden en el teléfono, sin regla de calidad visual (D-078).

## R9. La estimación de tokens (FR-022)

- **Qué hay:** `PromptBuilder.estimatedTokens` es ⌈puntos de código / 4,554⌉. LiteRT-LM no cuenta tokens antes de enviar en Kotlin: `Conversation::CountTokens` solo existe en C++, y `getTokenCount()` cuenta lo que ya está en la caché.
- **Decisión:** un estimador por clases de caracteres:

  tokens ≈ ⌈letras / a + dígitos / b + resto / c⌉

  con a, b y c ajustados por mínimos cuadrados frente al tokenizador real (`engine.tokenize` del harness) sobre los fragmentos de F05 y los textos con cifras, y redondeados hacia el lado seguro (el p95 del cociente real/estimado ≤ 1). Vive en `RagParameters` y en `offline/config/rag_parameters.json`, y la prueba de paridad que ya existe los mantiene iguales.
- **Regla (D-090):** el p95 de tokens reales entre estimados ≤ 1,0 en texto con cifras, más la regla de calidad de FR-017.
- **Alcance (D-090):** solo el límite de 2.000 tokens del prompt. La estimación guardada de cada fragmento, el presupuesto de contexto de D-034 (1.719) y sus pares del harness no cambian, y no hay que reindexar.
- **Calidad:** cambia qué bloques entran en los prompts con cifras. `run_eval.py token-review` informa el error (medio, p95 y peor, por tipo de texto) de la estimación vieja y de la nueva, y los prompts y respuestas que cambian. Las respuestas que cambian se juzgan a mano (D-033) con la regla de FR-017.
- **Comprobación en el teléfono:** la prueba de generación compara la estimación con `lastPrefillTokenCount`.
- **Alternativa descartada:** el tokenizador en la app. Exigiría un motor cargado para contar, o una copia del vocabulario de Gemma.

## R10. Modo multimodal (FR-023–FR-027)

- **Páginas:**
  - bancos de 1.000, 5.000 y 10.000 páginas, hechos de los vectores reales de las páginas del corpus visual de F18 (unas 300, embebidos con la 440M) perturbados con ruido gaussiano y renormalizados, con semilla fija;
  - no hay 10.000 páginas reales distintas, y una biblioteca con el modo activo rara vez pasa de unos pocos miles;
  - se mide F32 frente a INT8 con el criterio de 20 ms. La variante INT8 del banco usa SQL directo sobre la base del banco; la ruta INT8 de páginas de la app solo se implementa si las cifras la piden. Si no, las páginas se quedan en F32 y `pages_quant_dirty` no se usa.
- **Consulta con la 440M frente a la 270M:** la 270M se copia a `dev-models` (D-088). Se mide el p50 y p95 de la consulta y el RSS de las dos, alternando el orden.
- **Primer token con páginas en sesión sostenida:** `VisualChatBenchmarkTest` gana un modo sostenido (25 min sin reposo, con Equilibrado) además de sus 20 preguntas por detalle.
- **Recarga de Gemma con visión (14,2 s):**
  - con D-083 la página entra siempre que el ámbito tenga páginas, así que la recarga ocurre al pasar de un ámbito sin páginas a uno con páginas (o al revés);
  - hoy `WarmUpAnswerEngineUseCase` decide la visión con la biblioteca entera (`chatLibraryState()`), no con el ámbito;
  - **palanca:** el caso de uso recibe el ámbito y usa el mismo predicado que hoy (modo activo, `pagesUsable` y `withPages` del ámbito), y `ChatViewModel` lo vuelve a llamar al cambiar el ámbito. Así se carga el motor que pedirá la siguiente respuesta antes de preguntar;
  - **regla (D-090):** se adopta si baja al menos 0,3 s la primera respuesta tras cambiar de ámbito, sin subir el pico de memoria más de 200 MB;
  - nunca hay dos engines en GPU: el coordinador libera uno antes de cargar el otro;
  - se mide la espera de la primera respuesta tras cambiar de ámbito, con y sin precarga, y la memoria.
- **Regla de seguimiento con páginas** (`offline/testset/visual/followups.jsonl`, nuevo): unos 40 pares sobre el corpus visual:
  - **cambio de documento:** una pregunta con página sobre X y luego una corta sobre Y;
  - **seguimiento real:** la segunda continúa la misma tabla, por ejemplo «¿y en 2021?».

  Variantes, en `visual-followup-review`:
  1. **hoy:** las páginas se buscan con la consulta unida;
  2. **solo la nueva:** las páginas se buscan con la pregunta nueva sola;
  3. **la mejor de las dos:** se busca con las dos y gana la página con mayor similitud.

  Las variantes 2 y 3 cuestan un embedding más (unos 120 ms) solo cuando se aplica la regla de seguimiento.

  **Regla** (SC-007 y D-090, fijada antes de evaluar): la página del documento nuevo en ≥ 90 % de los cambios de documento; en los seguimientos reales, no más de 5 puntos por debajo de hoy; sin bajar el Recall@20 de los seguimientos de texto de 0,98. Entre las que cumplen, se elige la que más páginas correctas da en los seguimientos reales.

  El embedding extra de las variantes 2 y 3 solo se hace cuando la respuesta puede llevar página (`request.withPages`): con el modo apagado o sin páginas, nada cambia (FR-027).
- **Con el modo apagado:** nada de esto se ejecuta. `pageSearch` ya devuelve vacío sin `pagesUsable`.

## R11. E4B y la 270M en el teléfono (FR-024, FR-028, FR-029)

- **Decisión (D-090):** se copian con `adb` a `getExternalFilesDir("dev-models")`, y las pruebas los verifican por SHA-256 (los del escritorio, `offline/models/`) y los cargan **directamente** desde allí con `EngineFactory`. No se usa `DevModelImporter.installVerified`, que los movería a `models/` y a `verification.json`, ni se añaden a `ModelCatalog`: E4B sería descargable desde la app (constitución I), y la 270M volvería a aparecer como modelo instalado con el modo activo.
- **E4B:** el banco de generación corre con el contexto de D-034 y el embedder cargado. Mide la carga en frío y con caché, el primer token, el prefill, la decodificación y el pico de memoria.
- **Si no carga** (memoria o GPU): «no cabe» o «no carga», con el error.
- **Limpieza:** una prueba de limpieza borra los ficheros y sus entradas de `verification.json` (lecciones de F18).
- **Recomendación:** con las cifras y la calidad de F05 (+6,5 puntos automáticos, D-035), en DECISIONS. El cambio de modelo no entra en F14.

## R12. Palancas de calidad (FR-030–FR-032)

- **Reordenación α = 0,5:** `order-review` ya existe. Se repite con la regla de F10 (+2 puntos de Recall@bloques sin pasar el presupuesto), **después de decidir en el teléfono** las palancas que cambian el contexto (estimador y bloques), porque son las que pueden cambiar su resultado.
  - **Sin cambios en el contexto,** el resultado es el de D-051 (+1,4 puntos), y no se adopta.
  - **Coste en el teléfono:** solo se mide si se adopta (es aritmética sobre 20 candidatos que ya están en memoria). FR-030 se corrigió en el analyze (D-090).
- **Búsqueda entre idiomas (solo se evalúa, D-088):** `crosslang-review` gana dos variantes:
  1. **por idioma del documento:** normalizador inglés (palabras vacías y raíces Snowball del inglés, con `snowballstemmer`, que es puro Python) para los documentos del índice `en`, y la pregunta normalizada con los dos normalizadores (OR en FTS);
  2. la anterior más **peso en la búsqueda por significado:** RRF con peso 2 en la lista vectorial cuando los idiomas de la pregunta y del documento no coinciden.

  El idioma del documento lo da el índice en el harness, sin detección. Las respuestas que cambian se juzgan a mano (D-033), y la regla es la de D-062 (no más de 10 puntos por debajo de su referencia). El resultado va a DECISIONS y, si pasa, a una ficha nueva del backlog.

## R13. Número de bloques (FR-017)

- **En el teléfono:** 4, 6 y 8 bloques se miden por el primer token (R5).
- **Calidad:** la de 6 y 8 ya está en F05 (D-034: ±1 punto). La de 3 bloques se evalúa en el harness (`ablations` con `MAX_BLOCKS = 3`) con la regla de FR-017, como la única variante que puede bajar el primer token unos 0,8 s.
- **Decisión:** la que cumpla la regla. Si ninguna, se quedan 4 bloques (D-034).

## R14. Informe y entregables (FR-033–FR-035)

- **`benchmarks.md`:** una sección por bloque de la tabla del informe:
  - sesión y versiones;
  - modelos;
  - arranque: carga en frío y con caché de Gemma (texto y visión), la 440M y la 270M (`StartupBenchmarkTest`);
  - ingesta: el ritmo con los runtimes adoptados (`IndexingBenchmarkTest` y `VisualIngestBenchmarkTest`, sin cambios);
  - embedding;
  - búsqueda a 10k, 50k y 100k;
  - INT8;
  - generación con 4, 6 y 8 bloques;
  - extremo a extremo;
  - recursos;
  - sesión sostenida;
  - palancas;
  - multimodal;
  - E4B.

  Las tablas salen de `device_bench.py`.
- **El informe del RAG móvil:** «Latencia (presupuesto, no medición)» y «Benchmarks en el dispositivo» ganan una nota que remite a `specs/014-ondevice-performance/benchmarks.md`.
- **Decisiones:**
  - D-089: el plan;
  - D-090: el analyze;
  - las del resultado, en la fase del teléfono y en el cierre: INT8, bloques, perfil, primer token, fp16, estimador, seguimiento con páginas, recarga y cada palanca.

## R15. Orden de la fase del teléfono

0. **Sesión corta al principio** (D-090), con permiso: goldens con los runtimes nuevos (R1). Si fallan, se vuelve atrás antes de construir las palancas.
1. Ajustes anotados, pantalla despierta y bloqueo descartado.
2. `PrefixCacheGoldenTest`, con las palancas ya construidas.
3. Bancos de búsqueda (10k, 50k y 100k) e INT8.
4. Generación (4, 6 y 8 bloques), extremo a extremo, palancas una a una (R6–R8) y la sesión sostenida base.
5. Modo multimodal, la 270M y E4B.
6. Decisiones con las reglas, palancas adoptadas por defecto, batería instrumentada y sesión sostenida final.
7. Limpieza (bancos, modelos de prueba, informes) y ajustes restaurados.

Son varias horas de teléfono. Se pregunta al usuario antes de empezar y se le avisa de cada bloque largo.

## R16. Lo que no cambia

- Con el modo apagado y sin palancas adoptadas: las cifras de `ablations` (FR-038) y las goldens de la app.
- INT8 cambia la parte vectorial; su efecto en la calidad se acota con el Recall@20 frente a F32 (≥ 0,97), y `ablations` en el escritorio sigue usando F32 (el harness no usa INT8).
- Los textos de la app no cambian salvo una espera anunciada que una palanca cambie (supuestos de la spec).

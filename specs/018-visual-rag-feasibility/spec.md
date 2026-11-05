# Especificación de feature: Viabilidad del RAG visual (F18, fase 0 visual)

**Feature Branch**: `018-visual-rag-feasibility`

**Created**: 2026-11-05

**Status**: Draft

**Input**: Descripción del usuario: «F18 — Viabilidad del RAG visual (fase 0 visual) (docs/roadmap/FEATURES.md §F18, D-066). Feature técnica: demostrar en el teléfono de referencia (A063) que el RAG visual es viable y fijar en el escritorio sus parámetros antes de construirlo (F19–F20). En el teléfono, con diagnóstico solo en debug: Gemma 4 E2B con visión (ModelInfo, primer token con 1, 2 y 3 páginas a 280 y 560 tokens visuales, carga del codificador de visión, RSS pico, si lee tablas, cifras y texto pequeño); EmbeddingGemma 2 de 440M (embedding_golden con coseno ≥ 0,99, vectores de texto idénticos a los de la 270M, consulta en CPU con p95 < 150 ms, arranque y RSS, embedding de una página en CPU y GPU con 70 y 140 tokens por imagen); PdfRenderer (resolución, tiempo y peso por página; decodificación y escalado de JPG y PNG); nunca dos engines en GPU. En el escritorio (offline/): rasterizador fijado (pypdfium2), conjunto de prueba visual sobre un corpus público versionado con tablas, gráficos, escaneados e imágenes (unas 100 preguntas, parte revisadas a mano, relevancia por documento y página), ablaciones solo texto / solo visual / ambos (RRF de tres listas) y respuestas con imágenes frente a bloques de texto. Fija resolución de página, tokens por imagen, dimensión de los vectores de página (256 o 512), páginas en contexto, presupuesto visual de Gemma y regla de fusión y duplicados. Entregables: benchmarks.md con cifras reales, informe de evaluación, propuesta de enmienda de la constitución III (página citable, 440M, dimensión), decisión registrada de seguir o no con F19–F20; el diagnóstico no existe en release. Sin pantallas de producto.» Ver `docs/roadmap/FEATURES.md` (F18).

**Fuentes**:
- `docs/roadmap/FEATURES.md` §F18, y §F19–F20, que dependen del resultado.
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - Embeddings con EmbeddingGemma 2 (Truncación y normalización, Backend);
  - Generación (Modelo y runtime, Plantilla de prompt);
  - Memoria y ciclo de vida;
  - Pipeline offline, con la «Condición para mezclar vectores offline y del dispositivo»;
  - Evaluación (Conjunto de prueba, Métricas y umbrales internos, Ablaciones, Benchmarks en el dispositivo).
- **Constitución v1.1.1:**
  - I: sin red; los modelos solo llegan verificados por SHA-256;
  - III: 270M a 256D, `rag_meta` con la huella del modelo, y citas trazables a un `chunkId`. F18 propone la enmienda; no la aplica;
  - IV: nunca dos engines en GPU; la consulta en < 150 ms; el primer token ≤ 5 s, con la excepción D-057;
  - V: nada se declara listo sin verificarlo;
  - VI: `embedding_golden` con coseno ≥ 0,99, y cifras medidas en lugar de presupuestos;
  - VIII: decisiones en DECISIONS;
  - flujo: las features técnicas usan diagnósticos internos solo en debug.
- **DECISIONS:**
  - D-023 y D-024: Gemma 4 E2B genérico en GPU, y la consulta en CPU;
  - D-025: la 270M, porque las tres variantes dan los mismos vectores de texto (coseno 1,0 en 20 textos, R15 de F02);
  - D-026 y D-057: el presupuesto de primer token y su excepción. En frío, el primer token ≈ 0,27 s + 3,1 s por cada 1.000 tokens;
  - D-033: la revisión del conjunto de prueba la hace el asistente;
  - D-034 y D-035: los parámetros de texto y los umbrales de F05;
  - D-066: RAG visual por documento (Texto, Visual o Ambos), PDF incluidos los escaneados, imágenes JPG y PNG, y la 440M en lugar de la 270M, «sujeto a lo que mida F18».
- **F02:** el diagnóstico de viabilidad en debug (`debug/feasibility`), su protocolo de medida (R6) y su informe exportable. F18 lo amplía.
- **F05:** el harness de `offline/` (corpus con fuentes y licencias, conjunto de prueba, juez y revisión manual). F18 lo amplía con un corpus visual aparte.

**Comprobado al escribir esta spec** (2026-11-05, en el escritorio, con `ModelInfo` de `litert-lm-api` 0.18.1 sobre los ficheros fijados):

| Fichero | Imágenes | Tokens por imagen | Backend de visión |
|---|---|---|---|
| `gemma-4-E2B-it.litertlm`, el de la app (D-023) | Sí | 70, 140 o 280: **máximo 280** | Solo CPU |
| `gemma-4-E2B-it-gpu.litertlm` | No | — | — |
| `embeddinggemma-2-text-vision-440m.litertlm` | Sí | 70 o 140 | Solo CPU |

- **Los 560 tokens del roadmap no existen en el fichero de la app.** Se mide hasta 280, y la página partida en trozos se evalúa como alternativa (FR-023).
- Las dos partes de texto admiten CPU y GPU.
- La API de Python también acepta imágenes en los embeddings y en el LLM, así que la evaluación puede correr en el escritorio con los mismos ficheros.
- Los metadatos de la 270M declaran también visión, pero sin firmas de visión. No se usa para imágenes.

## Clarifications

### Session 2026-11-05

- Q: ¿Cuánto tiene que mejorar el modo visual las respuestas para seguir con F19–F20? → A: En las preguntas visuales (tabla, gráfico, escaneado e imagen suelta), «Ambos» da al menos **15 puntos más** de respuestas correctas que «Texto». En los controles de texto no pierde más de **2 puntos**. La regla se fija antes de evaluar (FR-025, SC-008).
- Q: ¿Qué primer token se acepta en una respuesta que lleva páginas? → A: Un **p95 ≤ 8 s** de extremo a extremo en el teléfono de referencia, con al menos 1 página. Entran las páginas que quepan. Si no cabe ni una, se decide con el usuario si se sigue con una excepción o se para (FR-025, SC-004).
- Q: ¿Quién revisa el conjunto de unas 100 preguntas visuales? → A: El asistente, como en F05 (D-033). Agentes separados redactan las preguntas a partir de las imágenes de página, y la sesión principal las revisa mirando cada página, con criterios explícitos y veredictos registrados (FR-021).
- Q: ¿De dónde salen las imágenes sueltas de prueba? → A: De dos fuentes:
  - imágenes públicas con licencia, como gráficos e infografías;
  - **fotos simuladas:** páginas del corpus convertidas en «foto» (giro, perspectiva, sombra, ruido y compresión JPEG), cuyo contenido y relevancia se conocen (FR-019).

## User Scenarios & Testing *(mandatory)*

Quien usa estas historias es quien desarrolla MicroRAG. Mide en el teléfono de referencia y evalúa en el escritorio para decidir, con cifras, si se construyen F19 y F20 y con qué parámetros.

### User Story 1 - Gemma responde mirando páginas en el teléfono (Priority: P1)

Desde el diagnóstico de depuración, quien desarrolla lanza generaciones con 1, 2 y 3 imágenes de página, con cada presupuesto visual que admite el fichero (70, 140 y 280 tokens por imagen), y una pregunta sobre lo que muestran. Obtiene:
- lo que declara el fichero en el teléfono;
- la carga del codificador de visión;
- el primer token, el prefill y la decodificación;
- la memoria pico;
- la respuesta, para juzgar si Gemma lee tablas, cifras y texto pequeño.

**Why this priority**: es el riesgo principal de D-066. El primer token ya pasa de 5 s sin imágenes (D-057), y cada página suma cientos de tokens y el codificador de visión.

**Independent Test**:
1. Con Gemma verificada y las páginas de prueba copiadas, ejecutar la matriz.
2. Obtener las cifras de cada combinación, o el motivo de las que no se puedan ejecutar.

**Acceptance Scenarios**:

1. **Given** Gemma verificada, **When** se abre el diagnóstico visual, **Then** muestra lo que declara el fichero en el teléfono: si admite imágenes, el presupuesto máximo por imagen, las firmas de visión y los backends de cada modalidad.
2. **Given** una combinación de páginas y presupuesto, **When** se ejecuta, **Then** se informan:
   - la carga con visión, frente a la carga sin ella;
   - los tokens del prompt, de texto y visuales;
   - el primer token, el prefill y la decodificación;
   - la memoria pico;
   - el texto generado.
3. **Given** una combinación que el fichero no admite (un presupuesto mayor que su máximo), **When** se pide, **Then** queda como «no admitida», con el motivo, y las demás siguen.
4. **Given** las páginas de lectura (tablas, cifras, texto pequeño y escaneados), **When** se ejecutan sus preguntas, **Then** queda registrado, para cada una, si la respuesta coincide con la esperada.
5. **Given** el contexto de texto habitual (D-034) más 1 página, **When** se ejecuta, **Then** se informa su primer token, que es el caso real del modo «Ambos».

---

### User Story 2 - La 440M sustituye a la 270M sin reindexar (Priority: P1)

Quien desarrolla comprueba en el teléfono que la 440M:
- pasa el `embedding_golden`;
- da los mismos vectores de texto que la 270M;
- cumple la consulta en < 150 ms en CPU;
- y conoce su arranque y su memoria.

**Why this priority**: D-066 decidió el cambio de modelo con la condición de que el índice actual siga valiendo. Sin esta prueba, F19 no puede migrar instalaciones sin reindexar.

**Independent Test**:
1. Con las dos variantes verificadas, ejecutar el golden y la comparación de vectores.
2. Ejecutar la prueba de consulta.

**Acceptance Scenarios**:

1. **Given** la 440M verificada, **When** se ejecuta el `embedding_golden`, **Then** cada par da un coseno ≥ 0,99, y se informan el mínimo y el medio.
2. **Given** los mismos textos, **When** se embeben con la 270M y con la 440M en el teléfono, **Then** se informan el coseno mínimo y el medio entre las dos. Con un mínimo ≥ 0,9999, sus vectores se dan por idénticos.
3. **Given** la 440M en CPU, **When** se mide la consulta, **Then** se informan la carga en frío, el p50 y el p95 (objetivo < 150 ms) y la memoria, junto a las mismas cifras de la 270M.

---

### User Story 3 - Embeber páginas e imágenes en el teléfono (Priority: P1)

Quien desarrolla mide el embedding de una página con la 440M, a 70 y a 140 tokens por imagen, en cada backend que admita el fichero. Comprueba además que una misma imagen da el mismo vector en el teléfono y en el escritorio.

**Why this priority**:
- el coste por página decide si la indexación visual de F19 es aceptable;
- la igualdad entre el teléfono y el escritorio decide si los parámetros que fija el escritorio valen en el teléfono (informe, «Condición para mezclar vectores»).

**Independent Test**: con la 440M verificada y las imágenes de prueba copiadas, obtener las cifras por página y los cosenos entre el teléfono y el escritorio.

**Acceptance Scenarios**:

1. **Given** la 440M en CPU, **When** se embebe una tanda de páginas, **Then** se informan:
   - la carga con visión;
   - el p50 y el p95 por página, con 70 y con 140 tokens;
   - la memoria pico;
   - que el vector tiene la dimensión pedida y norma 1, tras el truncado y la normalización.
2. **Given** un backend que el fichero no admite para imágenes (la GPU, según sus metadatos), **When** se pide, **Then** queda como «no disponible», con el motivo.
3. **Given** los mismos bytes de imagen, **When** se embeben en el teléfono y en el escritorio, **Then** se informa el coseno de cada imagen. El umbral es ≥ 0,99, como el `embedding_golden`.
4. **Given** una página rasterizada en el teléfono y la misma página rasterizada en el escritorio, **When** se embeben, **Then** se informa su coseno, para conocer cuánto pesa la diferencia de rasterizado.

---

### User Story 4 - Convertir páginas e imágenes en el teléfono (Priority: P2)

Quien desarrolla mide cuánto cuesta en el teléfono convertir una página de PDF en imagen, y escalar una foto o una captura:
- el tiempo;
- el peso en WebP y en JPEG;
- la memoria;
- y una proyección para un PDF de 200 páginas.

**Why this priority**: fija la resolución y el formato de las imágenes de F19, y su coste en tiempo y en espacio.

**Independent Test**: con los PDF e imágenes de prueba copiados, ejecutar la prueba de conversión y obtener sus cifras por resolución.

**Acceptance Scenarios**:

1. **Given** un PDF de texto y uno escaneado, **When** se convierten sus páginas a cada resolución candidata, **Then** se informan, por página:
   - el tiempo (p50 y p95);
   - el peso en WebP y en JPEG, con la calidad fijada;
   - la memoria pico.

   Se mide en vertical y en apaisado.
2. **Given** imágenes JPG y PNG (fotos grandes, capturas con transparencia y fotos giradas por EXIF), **When** se decodifican y se escalan a la resolución candidata, **Then** se informan el tiempo y la memoria, y la imagen queda derecha.
3. **Given** las cifras de conversión y de embedding, **When** se resume, **Then** se informa cuánto tardaría un PDF de 200 páginas en convertirse y embeberse en el teléfono, con su estado térmico.

---

### User Story 5 - Fijar los parámetros con una evaluación en el escritorio (Priority: P1)

Quien desarrolla prepara en `offline/`:
- un corpus visual público y versionado;
- un conjunto de unas 100 preguntas, con su relevancia por documento y página.

Con ellos ejecuta las ablaciones de recuperación y de respuesta, y obtiene un informe que justifica cada parámetro con una cifra.

**Why this priority**: la constitución III solo admite cambios de parámetros con una evaluación documentada. Sin ella, F19 y F20 se construirían a ciegas.

**Independent Test**:
1. Ejecutar la evaluación desde la línea de comandos.
2. Obtener el informe con las métricas por variante y por tipo de pregunta.

**Acceptance Scenarios**:

1. **Given** el corpus visual, **When** se genera su ficha de fuentes, **Then** cada documento tiene su origen, licencia, atribución, fecha, transformaciones y SHA-256, como el corpus de F05.
2. **Given** el conjunto de prueba, **When** se revisa, **Then** tiene al menos 100 preguntas aceptadas. De cada una consta:
   - su tipo: tabla, gráfico, escaneado, imagen suelta, control de texto o sin respuesta;
   - sus páginas relevantes;
   - la respuesta esperada;
   - el veredicto de la revisión.
3. **Given** el corpus indexado, **When** se ejecutan las ablaciones de recuperación, **Then** se informan, por página y por tipo de pregunta, el Recall@k, el MRR y el nDCG@10 de:
   - «Texto»: la búsqueda híbrida de hoy, con cada fragmento llevado a su página;
   - «Visual»: los vectores de página;
   - «Ambos»: RRF de las tres listas.
4. **Given** las mismas preguntas, **When** se ejecutan las ablaciones de respuesta con Gemma 4 E2B, **Then** se informan las respuestas correctas, las negativas y las citas a la página correcta, por tipo de pregunta, para:
   - bloques de texto;
   - imágenes de página;
   - una mezcla de las dos.
5. **Given** los resultados, **When** se cierra la evaluación, **Then** cada parámetro de FR-028 tiene la cifra que lo justifica y las alternativas descartadas.
6. **Given** el conjunto de prueba de texto de F05, **When** se evalúa con «Texto», **Then** da las mismas cifras que hoy: el corpus visual no cambia nada del texto.

---

### User Story 6 - Decidir con cifras y preparar F19 y F20 (Priority: P2)

Al terminar, quien desarrolla tiene:
- `benchmarks.md` con las cifras del teléfono;
- el informe de evaluación;
- las decisiones en DECISIONS;
- la propuesta de enmienda de la constitución;
- y el sí o el no a F19–F20, según una regla fijada antes de evaluar.

**Why this priority**: F18 existe para decidir. Igual que F02 hizo con el resto del roadmap, su salida es una decisión registrada.

**Independent Test**: revisar que DECISIONS, FEATURES y la propuesta de enmienda recogen las cifras y que la decisión aplica la regla fijada antes.

**Acceptance Scenarios**:

1. **Given** las cifras, **When** se registran las decisiones, **Then** cada parámetro y el cambio de modelo (D-025) llevan su cifra.
2. **Given** la regla de sí o no de FR-025, **When** se aplica, **Then** el resultado queda en DECISIONS, y FEATURES marca F19–F20 como siguientes o como descartadas.
3. **Given** un primer token por encima de 8 s de p95 incluso con 1 página, **When** ocurre, **Then** se decide con el usuario si se sigue con una excepción o se para (D-026, D-057).
4. **Given** el sí, **When** se redacta la propuesta de enmienda, **Then** cubre:
   - la página como unidad citable (documento y página reales);
   - la 440M;
   - la dimensión de los vectores de página;
   - los formatos visuales.

   Se aplica en F19 con `/speckit-constitution` (D-066).

---

### User Story 7 - Medir sin poner en riesgo el teléfono ni la app (Priority: P2)

Mientras se mide, nunca hay dos engines en GPU, la interfaz responde y las pruebas se pueden cancelar. Los engines se liberan al salir o al pasar a segundo plano. La build de publicación no contiene nada de F18, y la app de producto se comporta igual que antes.

**Why this priority**:
- principios I y IV de la constitución;
- en el teléfono están los datos del usuario, que F18 no toca.

**Independent Test**:
1. Lanzar una prueba de embedding de páginas y después una de Gemma: el primer engine se libera antes.
2. Comprobar que la build de publicación no contiene el diagnóstico.

**Acceptance Scenarios**:

1. **Given** el embedder cargado, **When** se carga Gemma, **Then** el embedder se libera antes.
2. **Given** una prueba en curso, **When** la app pasa a segundo plano, **Then** se cancela, los engines se liberan y queda registrada como cancelada.
3. **Given** la build de publicación, **When** se inspecciona, **Then** no contiene el diagnóstico visual, sus textos ni sus páginas de prueba, y sigue sin permisos de red.

### Edge Cases

- **El fichero de Gemma no admite imágenes en el teléfono**, al revés que en el escritorio: las pruebas de generación visual se desactivan con el motivo, y se decide con el usuario.
- **Presupuesto mayor que el máximo del fichero** (los 560 del roadmap): queda como «no admitida» (US1).
- **El codificador de visión no carga por memoria:** se captura el error, la app no se cierra y la combinación queda como fallida.
  - Si el sistema mata el proceso, se pierde solo la medida en curso: el informe se reescribe tras cada resultado, como en F02.
- **Páginas difíciles:** en blanco, apaisadas, de tamaño A3 o mayor, protegidas con contraseña o dañadas. Se registra lo que pasa y se sigue.
- **PDF escaneado:** la vía de texto no extrae nada. Sus preguntas solo pueden contestarse con «Visual» o «Ambos», y el informe lo separa.
- **Imágenes problemáticas:** de 12 MP o más, PNG de 16 bits o con transparencia, JPEG en CMYK y fotos giradas por EXIF.
- **Calor:** se registra el estado térmico al inicio y al final. Si es «severo» o peor, se espera, como en F02 (R6).
- **Rasterizado distinto en el teléfono y en el escritorio por encima del umbral:** se documenta. F19 rasteriza en el teléfono, así que los parámetros se eligen para que aguanten esa diferencia.
- **Licencia dudosa de un documento del corpus:** se deja fuera.
- **Preguntas cuya respuesta está a la vez en el texto y en una figura:** se clasifican como «mixtas» y no cuentan como visuales puras.
- **Evaluación lenta en el escritorio** (Gemma con imágenes): se informa la duración. Una tanda parcial se marca como tal y no cuenta para la decisión.
- **Ver los resultados antes de fijar la regla:** no se permite. La regla y el conjunto de prueba se fijan antes de evaluar y no cambian después (FR-025).

## Requirements *(mandatory)*

### Functional Requirements

**Disponibilidad y aprovisionamiento**

- **FR-001**: El diagnóstico visual MUST existir solo en la variante de depuración, como ampliación del de F02. MUST NOT incluirse en la build de publicación.
- **FR-002**: El diagnóstico MUST conocer los ficheros que usa, con nombre, tamaño y SHA-256 fijados al commit de origen (D-011, R2 de F02):
  - Gemma 4 E2B genérico, el de la app;
  - EmbeddingGemma 2 de 440M y de 270M.

  Llegan al teléfono con el procedimiento de F02: se copian desde el ordenador y se importan con verificación. F18 no hace ninguna llamada de red (constitución I).
- **FR-003**: Las páginas e imágenes de prueba del teléfono MUST salir del corpus público de la evaluación (FR-019) y llegar por el mismo procedimiento. Nunca se usa un documento del usuario.

**Gemma con visión**

- **FR-004**: El diagnóstico MUST leer y mostrar lo que declara el fichero de Gemma en el teléfono:
  - si admite imágenes;
  - el presupuesto máximo por imagen;
  - las firmas de visión;
  - los backends de cada modalidad.
- **FR-005**: Para 1, 2 y 3 páginas y cada presupuesto que admita el fichero (70, 140 y 280), el diagnóstico MUST medir:
  - la carga en frío con visión, frente a la carga sin ella;
  - los tokens del prompt, de texto y visuales;
  - el primer token, el prefill y la decodificación;
  - la memoria pico;
  - el texto generado.

  Usa el protocolo de F02: calentamiento descartado y repeticiones medidas. Mide también el contexto de texto habitual (D-034) más 1 página, el caso real de «Ambos».
- **FR-006**: El diagnóstico MUST ejecutar una prueba de lectura: entre 10 y 15 páginas con tablas, cifras, texto pequeño y escaneados, cada una con su pregunta y su respuesta esperada. Registra el acierto de cada una y lo compara con la respuesta del escritorio para la misma página.
- **FR-007**: La visión de Gemma MUST correr en el backend que declara el fichero (CPU), y su parte de texto en GPU (D-023). Si el fichero ofreciera la visión en GPU, también se mide, sin tener nunca dos engines en GPU.

**EmbeddingGemma 2 de 440M**

- **FR-008**: El diagnóstico MUST pasar el `embedding_golden` con la 440M en CPU y en GPU, con un coseno ≥ 0,99 por par (constitución VI).
- **FR-009**: El diagnóstico MUST embeber los textos del golden con la 270M y con la 440M en el teléfono, e informar el coseno mínimo y el medio. Con un mínimo ≥ 0,9999, el índice de texto actual sigue valiendo. Si no, se documenta y se decide con el usuario.
- **FR-010**: Para la 440M, el diagnóstico MUST medir en CPU:
  - la carga en frío;
  - el p50 y el p95 de una consulta (objetivo < 150 ms, constitución IV);
  - el embedding de un fragmento de unos 350 tokens;
  - la memoria;

  con las mismas cifras de la 270M al lado.
- **FR-011**: El diagnóstico MUST medir el embedding de una página con la 440M a 70 y a 140 tokens por imagen, en cada backend que admita el fichero:
  - la carga con visión;
  - el p50 y el p95 por página;
  - la memoria pico;
  - la dimensión y la norma tras truncar y normalizar a las dimensiones candidatas.

  Un backend no admitido queda como «no disponible», con el motivo.
- **FR-012**: Los vectores de imagen MUST compararse entre el teléfono y el escritorio:
  - con los mismos bytes de imagen: coseno por imagen, con umbral ≥ 0,99 (un golden visual);
  - con la misma página rasterizada en cada lado: coseno informado.

**Páginas e imágenes**

- **FR-013**: El diagnóstico MUST convertir páginas de PDF de texto y escaneados a cada resolución candidata, en vertical y en apaisado. Por página informa:
  - el tiempo (p50 y p95);
  - el peso en WebP y en JPEG, con la calidad fijada;
  - la memoria pico.
- **FR-014**: El diagnóstico MUST decodificar y escalar imágenes JPG y PNG a la resolución candidata, e informar el tiempo y la memoria. Incluye fotos de 12 MP o más, transparencias y orientación EXIF, y la imagen queda derecha.
- **FR-015**: El diagnóstico MUST proyectar lo que tardaría un PDF de 200 páginas en convertirse y embeberse en el teléfono, con el estado térmico. Es una proyección para F19: F18 no construye la indexación visual.

**Recursos y seguridad**

- **FR-016**: Nunca MUST haber dos engines en GPU a la vez. Cargar uno MUST liberar antes el anterior, y el embedder y Gemma no conviven en GPU (constitución IV).
- **FR-017**: Toda carga, inferencia y conversión MUST ir fuera del hilo de interfaz, en las colas dedicadas.
  - Las pruebas se cancelan en menos de 1 s, salvo durante la carga de un engine (FR-011 de F02).
  - Los engines se liberan al salir del diagnóstico y al pasar a segundo plano.
- **FR-018**: Cada prueba MUST registrar el estado térmico al inicio y al final, y si el teléfono estaba cargando, con la espera de F02 si el estado es «severo» o peor.
  - Los resultados se guardan tras cada uno, en un informe exportable con el dispositivo, las versiones y las huellas.
  - En los registros del sistema no van prompts, respuestas ni texto de páginas (constitución I), aunque el corpus sea público.

**Evaluación en el escritorio**

- **FR-019**: `offline/` MUST tener un corpus visual público y versionado, en español, con PDF con tablas, PDF con gráficos, PDF escaneados e imágenes sueltas JPG y PNG.
  - Las imágenes sueltas son de dos tipos:
    - imágenes públicas con licencia;
    - **fotos simuladas**, generadas de forma reproducible (con semilla) a partir de páginas del corpus: giro, perspectiva, sombra, ruido y compresión JPEG.
  - Cada documento lleva su origen, licencia, atribución, fecha, transformaciones y SHA-256, en una ficha como la de F05. Las fotos simuladas apuntan a su página de origen.
  - Los ficheros no se distribuyen con la app.
- **FR-020**: El rasterizador del escritorio MUST tener la versión fijada: pypdfium2, de la misma familia de motor (pdfium) que el `PdfRenderer` de Android. La resolución y la codificación son parámetros.
- **FR-021**: El conjunto de prueba MUST tener al menos 100 preguntas aceptadas, con:
  - su tipo: tabla, gráfico, escaneado, imagen suelta, mixta, control de texto o sin respuesta;
  - sus páginas relevantes (documento y página);
  - la respuesta esperada;
  - el veredicto de la revisión.

  Las preguntas las redactan agentes separados a partir de las imágenes de página. Las revisa el asistente, como en F05 (D-033):
  - la sesión principal, que no las ha redactado;
  - mirando cada página;
  - con criterios explícitos: pregunta realista y no ambigua, respuesta deducible de la página, y preguntas sin respuesta comprobadas en el corpus.

  Los veredictos quedan registrados.
- **FR-022**: Las ablaciones de recuperación MUST comparar «Texto» (la búsqueda híbrida de D-034, con cada fragmento llevado a su página), «Visual» (vectores de página) y «Ambos» (RRF con k = 60 de las tres listas).
  - Métricas: Recall@k, MRR y nDCG@10 por página, por tipo de pregunta.
  - Variantes: dimensión de página 256 o 512, 70 o 140 tokens por imagen, y las resoluciones candidatas.
- **FR-023**: Las ablaciones de respuesta MUST usar Gemma 4 E2B con el mismo fichero que la app, y comparar:
  - bloques de texto (el contexto de hoy);
  - imágenes de página, con 1 a 3 páginas y presupuestos de 70, 140 y 280;
  - una mezcla de las dos;
  - la página partida en trozos, como alternativa a un presupuesto mayor que 280.

  Métricas: respuestas correctas, negativas y citas a la página correcta, por tipo de pregunta, con el juez automático y una muestra manual, como en F05. El prompt con páginas numeradas `[n]` es una propuesta que solo se evalúa; la app no cambia.
- **FR-024**: Con «Texto», el conjunto de prueba de F05 MUST dar las mismas cifras que hoy (D-034, D-051). El corpus visual no toca el de texto.
- **FR-025**: Antes de ejecutar la evaluación, MUST quedar fijados por escrito los umbrales, la regla para elegir cada parámetro y la **regla de sí o no** (clarify), que pide las tres condiciones:
  1. **Calidad visual:** en las preguntas visuales (tabla, gráfico, escaneado e imagen suelta), «Ambos» da al menos 15 puntos más de respuestas correctas que «Texto».
  2. **Sin pérdida en texto:** en los controles de texto, «Ambos» no pierde más de 2 puntos frente a «Texto».
  3. **Primer token:** con la configuración elegida, el p95 en el teléfono de referencia es ≤ 8 s de extremo a extremo, con al menos 1 página.
     - De extremo a extremo es como en D-026: el embedding de la consulta, la búsqueda y el primer token de la generación.
     - Entran las páginas que quepan en ese límite.
     - Si no cabe ni una, se decide con el usuario si se sigue con una excepción o se para.

  Además, la 440M tiene que pasar SC-002, o el usuario decide qué hacer.

  El conjunto de prueba no cambia después de ver resultados (como D-057: «regla fijada antes de evaluar»).
- **FR-026**: La evaluación MUST poder repetirse desde la línea de comandos, con semillas fijadas. Sus resultados van a `offline/reports/`, y su código lleva pruebas en `offline/tests`.

**Resultados y decisiones**

- **FR-027**: `specs/018-visual-rag-feasibility/benchmarks.md` MUST recoger las cifras reales del teléfono: dispositivo, Android, versiones de la app y del runtime, huellas, protocolo, y p50 y p95.
- **FR-028**: DECISIONS MUST recoger, cada uno con su cifra:
  - la resolución de página;
  - los tokens por imagen del embedder;
  - la dimensión de los vectores de página;
  - las páginas en el contexto;
  - el presupuesto visual de Gemma;
  - la regla de fusión y de duplicados: si entra una página, no entran también sus fragmentos;
  - el cambio de la 270M a la 440M (sustituye a D-025, o se documenta por qué no);
  - el sí o el no a F19–F20.
- **FR-029**: Si sale el sí, MUST quedar redactada la propuesta de enmienda de la constitución, en III y en Restricciones técnicas:
  - la página como unidad citable, trazable a un documento y una página reales;
  - la 440M;
  - la dimensión de los vectores de página;
  - los formatos visuales.

  Se aplica en F19 (D-066).
- **FR-030**: `docs/roadmap/FEATURES.md` MUST reflejar el resultado: F18 cerrada, y F19–F20 con los parámetros decididos, o descartadas.
- **FR-031**: F18 MUST NOT cambiar el producto: ni el esquema, ni la ingesta, ni la recuperación, ni el prompt, ni la UI de la app. Las pruebas de producto siguen igual.

### Key Entities

- **Página:** documento, número de página, imagen rasterizada (resolución, formato y peso) y origen: PDF de texto, PDF escaneado o imagen suelta.
- **Prueba visual en el teléfono:** la combinación (modelo × backend × páginas × presupuesto o tokens por imagen), sus métricas, su estado (completada, cancelada, no admitida o fallida, con el motivo) y el estado térmico.
- **Pregunta visual:** texto, tipo, páginas relevantes, respuesta esperada y veredicto de la revisión.
- **Variante de evaluación:** el modo (Texto, Visual, Ambos o mezcla en la respuesta), sus parámetros y sus métricas.
- **Decisión de viabilidad:** la regla fijada antes, las cifras, el resultado (sí o no) y los parámetros para F19–F20.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `benchmarks.md` tiene, o el motivo documentado de las que falten:
  - todas las combinaciones de Gemma (1, 2 y 3 páginas × presupuestos admitidos);
  - las de la 440M (consulta, fragmento y página × tokens × backends);
  - la conversión de páginas e imágenes a cada resolución candidata.
- **SC-002**: La 440M pasa el `embedding_golden` en el teléfono (≥ 0,99 en cada par), y su coseno mínimo frente a la 270M es ≥ 0,9999. Si no, queda una decisión documentada y aprobada por el usuario.
- **SC-003**: La consulta con la 440M en CPU da un p95 < 150 ms en el teléfono de referencia.
- **SC-004**: Se conoce el primer token con 1, 2 y 3 páginas, y con el contexto de texto más 1 página, en el teléfono de referencia. El límite es un p95 ≤ 8 s de extremo a extremo con al menos 1 página (FR-025). Si no se cumple ni con 1 página, la decisión del usuario queda registrada.
- **SC-005**: Con los mismos bytes, cada imagen de prueba da un coseno ≥ 0,99 entre el teléfono y el escritorio. Si no, se documenta y los parámetros del escritorio se validan en el teléfono.
- **SC-006**: El conjunto de prueba tiene al menos 100 preguntas aceptadas, al menos el 60 % de ellas visuales (tabla, gráfico, escaneado o imagen suelta), todas con sus páginas relevantes y su veredicto.
- **SC-007**: El informe de evaluación tiene las ablaciones de recuperación y de respuesta por tipo de pregunta, y cada parámetro de FR-028 lleva la cifra que lo justifica.
- **SC-008**: El sí o el no a F19–F20 queda en DECISIONS, aplicando la regla de FR-025 (+15 puntos en las visuales, −2 como mucho en los controles de texto y un p95 ≤ 8 s con al menos 1 página), fijada antes de evaluar.
- **SC-009**: En ningún momento hay dos engines en GPU (prueba automática y registros), y la build de publicación no tiene el diagnóstico ni permisos de red.
- **SC-010**: Con «Texto» se reproducen las cifras de F05 y F10, y la batería de pruebas de la app pasa igual que al cerrar F17.

## Assumptions

- **Modelos:** los ficheros fijados ya están en el ordenador (`~/MicroRAG-models`), con las huellas de F02 (R2). La 440M pesa 387.710.976 B. En el teléfono se copian con el procedimiento de F02.
- **Presupuesto visual de Gemma:** el fichero de la app admite como mucho 280 tokens por imagen (comprobado en el escritorio). Si 280 no bastan para leer texto pequeño, la alternativa que se evalúa es partir la página en trozos, cada uno como una imagen. Otro fichero de Gemma sería un cambio de modelo, que queda fuera de F18.
- **Protocolo de medida:** el de F02:
  - embeddings: 3 repeticiones de calentamiento y 30 medidas;
  - generación: 1 de calentamiento y 3 medidas;
  - espera térmica de R6.
- **Resoluciones candidatas:** el plan fija la malla (por ejemplo, 768, 1.024 y 1.536 px en el lado largo) y la calidad de WebP y JPEG.
- **Corpus visual:**
  - fuentes públicas en español que permitan la reutilización, como INE, Banco de España, AEAT y BOE (incluidos sus escaneados históricos), y Wikimedia Commons para imágenes;
  - entre 15 y 25 documentos y al menos 300 páginas, más las fotos simuladas;
  - la lista exacta la fija el plan, con las licencias comprobadas.
- **Puntos de la regla de sí o no:** son puntos porcentuales de respuestas correctas, juzgadas como en F05 (juez automático y muestra manual), sobre las mismas preguntas en las dos variantes.
- **Revisión del conjunto de prueba:** la hace el asistente (D-033, confirmado en el clarify), con las preguntas redactadas por agentes separados.
- **Juez:** el automático de F05 más una muestra manual, revisada según D-033.
- **Dónde se mide cada cosa:** la calidad, en el escritorio, con los mismos ficheros y `litert-lm-api` 0.18.1. Las latencias y la memoria, solo en el teléfono. La prueba de lectura del teléfono (FR-006) comprueba que su lectura coincide con la del escritorio.
- **Dependencias:**
  - F02: el diagnóstico y su protocolo;
  - F05: el harness;
  - F10: la recuperación híbrida;
  - F11: el prompt y el chat.
- **Fuera de alcance:**
  - la indexación visual en la app (F19) y el chat con páginas (F20);
  - cualquier cambio de esquema, ingesta, recuperación, prompt o UI de producto;
  - el OCR (backlog);
  - Gemma 4 E4B, INT8 y la precarga (F14);
  - el audio.

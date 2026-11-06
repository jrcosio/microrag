# Especificación de feature: Rendimiento en dispositivo y decisión INT8 (F14)

**Feature Branch**: `014-ondevice-performance`

**Created**: 2026-11-06

**Status**: Draft

**Input**: Descripción del usuario: «F14 — Rendimiento en dispositivo y decisión INT8 (docs/roadmap/FEATURES.md §F14; D-023, D-026, D-034, D-035, D-051, D-052, D-057, D-062, D-066, D-077, D-078, D-086, D-087). Feature técnica: diagnóstico y benchmarks internos, solo en debug; sin pantallas de producto salvo lo que decidan las palancas. Objetivo: sustituir los presupuestos del informe por cifras reales en un benchmarks.md (dispositivo, versiones) y tomar las decisiones pendientes con esos datos. Bloques: (1) búsqueda con 10k, 50k y 100k fragmentos (F32 frente a INT8 con preload, p50/p95/p99, FTS, híbrida total, búsqueda acotada por ámbito de F17), con el criterio de la constitución IV […]; (2) LLM: TTFT con 4, 6 y 8 bloques, prefill y decode, extremo a extremo, RSS, heap, tamaño del .db, batería y temperatura, sesión sostenida de 20–30 min […]; (3) palancas del primer token por debajo de 5 s […]; (4) lo que añaden F16–F20 […]; (5) palancas de calidad baratas […]. Criterios: benchmarks.md con cifras reales; INT8 sí o no con su Recall@20; primer token ≤ 5 s en sesión sostenida o decisión documentada; número de bloques y perfil de modelo decididos; todas las decisiones en DECISIONS. […]» Ver `docs/roadmap/FEATURES.md` (F14).

**Fuentes**:
- `docs/roadmap/FEATURES.md` §F14, con sus datos de partida de F04, F10, F11, F12 y F16–F20.
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - Motor vectorial: «Latencia (presupuesto, no medición)» y su tabla de criterio (F32, INT8 + preload, INT8 con reordenación en F32, USearch);
  - Capa de datos: `rebuildInt8()`, `preloadInt8IfClean()` y `quant_dirty`;
  - Evaluación → Benchmarks en el dispositivo (arranque, embedding, búsqueda, LLM, extremo a extremo, recursos y sesión sostenida);
  - Plan del POC, fase 4 («Rendimiento y decisión»).
- **Constitución 1.2.0:**
  - III: FTS5, INT8 y cualquier ANN son índices reconstruibles; las citas son trazables a un fragmento o a una página reales;
  - IV: el primer token ≤ 5 s de extremo a extremo con ≈ 2.000 tokens; la consulta en < 150 ms; INT8 + preload solo si el p95 vectorial pasa de 20 ms; nunca dos engines en GPU; las respuestas con páginas tienen su propio primer token, medido y avisado;
  - V: el índice nunca se publica a medias;
  - VI: las cifras medidas sustituyen a los presupuestos;
  - VIII: las decisiones se registran en DECISIONS;
  - flujo: las features técnicas usan diagnósticos internos solo en debug.
- **DECISIONS:** D-002 (tooling de desarrollo que no se distribuye), D-022 (teléfono de referencia), D-023 (Gemma genérico en GPU, con FP32 a 7,36 s frente a 4,53 s), D-024 (consulta en CPU), D-026 (presupuesto de 5 s y palancas para F14), D-034 y D-035 (parámetros de recuperación y contexto, y E4B para F14), D-051 (reordenación α = 0,5), D-052 (INT8 + precarga para F14), D-057 (excepción del primer token), D-062 (búsqueda entre idiomas), D-066, D-077 y D-078 (F16–F20 antes de F14, hallazgos de fp16 y de la estimación de tokens), D-086 y D-087 (cifras del chat con páginas).

**Punto de partida** (cifras ya medidas en el A063; F14 las vuelve a medir con un método común):

| Bloque | Cifra | Origen |
|---|---|---|
| Búsqueda vectorial F32, 10k fragmentos | p95 23,1 ms sola; 51,9–52,9 ms dentro de la híbrida; 66,9–68,3 ms con 4.300 ocultos | F04, D-052 |
| Recuperación híbrida total, 10k | p95 202–211 ms (objetivo 200 ms); 198–202 ms con un ámbito pequeño | D-052, F17 |
| Embedding de consulta (270M, CPU) | p95 125,6 ms solo; 143,6–151,1 ms dentro de la búsqueda | D-052 |
| Primer token del chat de texto | p95 5,2 s; 5,8 s en frío; 6,6 s en caliente (estado térmico «moderado»); con el modo activo y sin página, 5,0 s | D-057, D-086 |
| Qué pesa en el primer token | ≈ 0,27 s + 3,1 s por cada 1.000 tokens estimados del prompt | D-057 |
| Primer token con página | p95 6,7 s, 8,4 s y 14,4 s (Rápido, Equilibrado y Detallado) | D-086 |
| Recarga de Gemma con visión | 14,2 s hasta el primer token | D-087 |
| Pico de memoria | 3,2–4,8 GB con el chat de texto; 4.620 MB con páginas | D-057, D-087 |
| Cifras deformadas con fp16 en GPU | 2 de 60 respuestas en el teléfono, en contextos llenos de cifras | D-077 |
| Estimación de tokens | se queda corta unas 2,2 veces con texto lleno de cifras | D-077 |
| Búsqueda entre idiomas | 50,9 % (ES·EN) y 64,0 % (EN·ES) de respuestas correctas, frente a 82,5 % y 80,0 % de referencia | D-062 |
| Reordenación α = 0,5 | Recall@bloques de 0,889 a 0,903 en el escritorio | D-051 |

**Estado del entorno** (2026-11-06):
- **Teléfono de referencia:** Nothing Phone (1), A063, con 12 GB de RAM y Android 15 (D-022). Tiene el modo «Multimodal» **activado**, con la 440M y Gemma 4 E2B, y **sin la 270M** (D-082). El usuario tiene allí un documento y una conversación suyos.
- **Versiones:** LiteRT-LM 0.18.1 y SQLite-Vector 1.1.2. En Maven están LiteRT-LM 0.19.1 y SQLite-Vector 1.2.0; lint avisa de ellas desde F11 y F13, pendientes de decidir.
- **Escritorio:** el harness de `offline/` tiene la 270M, Gemma 4 E2B y Gemma 4 E4B verificados, el corpus de F05 (índice `ref`), el corpus visual de F18 y el corpus en inglés de F12.

## Clarifications

### Session 2026-11-06

- Q: ¿Qué modelos se pueden copiar al A063 para medir? → A: La 270M (157 MB) y Gemma 4 E4B (3,66 GB), copiadas con `adb` desde el escritorio a una carpeta de pruebas de la app de depuración. Se verifican por SHA-256 y se borran al terminar; el modo multimodal sigue activado (FR-024, FR-028).
- Q: ¿Qué regla de adopción tienen las palancas del primer token? → A: Se adopta la que baja el p95 del primer token en la sesión sostenida **al menos 0,3 s**. Si cambia el prompt o el contexto, además las respuestas correctas no bajan más de **1 punto** de `v4` (81,1 %), y las negativas y las citas cumplen los umbrales del informe (FR-017).
- Q: ¿Qué salida tienen las cifras deformadas con fp16? → A: **FP32 si cabe:** se adopta si el p95 del primer token en la sesión sostenida sube **≤ 0,5 s**. Si no, se quedan las activaciones por defecto con una instrucción en el prompt para copiar las cifras tal cual, evaluada en el harness con la regla de las palancas; el riesgo que quede se documenta (FR-021).
- Q: ¿Hasta dónde llega F14 con la búsqueda entre idiomas? → A: **Solo se evalúa** en el escritorio y se registra el resultado. Si cumple la regla de 10 puntos, se implementa en otra feature, porque necesita el idioma de cada documento y un índice por palabras distinto. La tarjeta «Búsqueda multilingüe» no cambia en F14 (FR-031).
- Q: ¿Cuándo se prueban los runtimes nuevos (LiteRT-LM 0.19.1 y SQLite-Vector 1.2.0)? → A: **Al empezar.** Si pasan el `embedding_golden`, las goldens de respuesta y la batería de pruebas, todo F14 se mide con ellos y no hay que medir dos veces. Si no, se quedan las versiones actuales y se registra el motivo (FR-019).
- Q: ¿Se aprueba la spec con estas respuestas, y se sigue el ciclo en automático hasta la implementación? → A: Sí (revisión del clarify, 2026-11-06). La fase del teléfono y el cierre se preguntan antes. Queda en D-088.

## User Scenarios & Testing *(mandatory)*

Quien usa estas historias es quien desarrolla MicroRAG. Mide en el teléfono de referencia y evalúa en el escritorio para sustituir los presupuestos del informe por cifras reales y tomar las decisiones pendientes. Quien usa la app nota el resultado en las palancas que se adopten: búsquedas más rápidas con bibliotecas grandes, un primer token más corto y respuestas más fiables con cifras.

### User Story 1 - La búsqueda con 10k, 50k y 100k fragmentos (Priority: P1)

Quien desarrolla carga en el teléfono bancos de prueba de 10.000, 50.000 y 100.000 fragmentos y mide cada parte de la búsqueda:
- la vectorial exacta (F32) y la cuantizada (INT8), precargada y sin precargar;
- la léxica;
- la híbrida completa, con el embedding de la consulta;
- la acotada por un ámbito pequeño (F17);
- con una parte de los documentos excluidos de las búsquedas.

Obtiene los percentiles 50, 95 y 99, el coste de construir y precargar el índice INT8, su memoria y el tamaño de la base de datos.

**Why this priority**: el criterio de la constitución IV (INT8 + preload si el p95 vectorial pasa de 20 ms) ya se dispara con 10k (D-052). Sin cifras a 50k y 100k no se sabe si INT8 basta para el corpus objetivo, ni cuánto pesa cada parte en el total de 200 ms.

**Independent Test**:
1. Cargar cada banco en el teléfono, sin tocar el índice del usuario.
2. Ejecutar el banco de búsqueda con las mismas preguntas en cada tamaño.
3. Obtener la tabla de percentiles por parte y tamaño, y retirar los bancos.

**Acceptance Scenarios**:

1. **Given** un banco de N fragmentos (10k, 50k o 100k) cargado aparte del índice del usuario, **When** se ejecuta el banco de búsqueda, **Then** se informan, por parte (embedding solo, embedding dentro de la búsqueda, vectorial F32, vectorial INT8, léxica, híbrida total y acotada), el p50, el p95 y el p99 en milisegundos.
2. **Given** el mismo banco, **When** se construye el índice INT8 y se precarga, **Then** se informan el tiempo de construcción, el tiempo de precarga, la memoria que ocupa la precarga y el tamaño de la base de datos antes y después.
3. **Given** las mismas preguntas, **When** se comparan los 20 primeros de INT8 con los de F32, **Then** se informa el Recall@20 de INT8 frente a F32 en cada tamaño.
4. **Given** un banco con una parte de los documentos excluidos, **When** se busca, **Then** las cifras se informan también en ese caso, y ningún resultado sale de un documento excluido.
5. **Given** que el teléfono no tiene espacio para un banco, **When** se intenta cargar, **Then** queda como «sin espacio», con el espacio libre y el necesario, y los demás tamaños siguen.

---

### User Story 2 - INT8 y precarga en la app (Priority: P1)

Con la biblioteca del usuario, la búsqueda vectorial usa el índice INT8 precargado cuando está al día, y la búsqueda exacta mientras no lo está. El índice INT8 se reconstruye en segundo plano cuando cambian los fragmentos (al terminar una indexación, al borrar o reindexar un documento) y se vuelve a precargar tras la muerte del proceso.

**Why this priority**: es el criterio 2 de F14 y lo que pide la constitución IV con las cifras de D-052. Sin él, la búsqueda vectorial sigue en unos 52 ms con 10k fragmentos y crece con la biblioteca.

**Independent Test**:
1. Con una biblioteca indexada, comprobar que la búsqueda usa INT8 y da los mismos documentos que F32 dentro de la regla de Recall@20.
2. Importar, borrar y reindexar documentos, y comprobar que la búsqueda nunca usa un índice INT8 viejo.
3. Matar el proceso y comprobar que la primera búsqueda responde con la exacta y que INT8 vuelve a estar listo en segundo plano.

**Acceptance Scenarios**:

1. **Given** una biblioteca con el índice INT8 al día y precargado, **When** se busca, **Then** la parte vectorial usa INT8, y su p95 con 10k fragmentos queda por debajo de 20 ms en el teléfono de referencia.
2. **Given** una indexación que termina, o un documento que se borra, se reindexa o pasa a otro modo de índice, **When** cambian los fragmentos, **Then** el índice INT8 queda marcado como desfasado, las búsquedas usan la exacta y el índice se reconstruye en segundo plano sin bloquear la búsqueda ni la indexación.
3. **Given** que el proceso muere, **When** la app vuelve a arrancar, **Then** la primera búsqueda funciona con la exacta, y el índice INT8, si estaba al día, se precarga en segundo plano.
4. **Given** una reconstrucción interrumpida (muerte del proceso, falta de espacio o un error), **When** se vuelve a abrir el índice, **Then** sigue marcado como desfasado y la búsqueda sigue con la exacta: nunca usa un índice INT8 a medias.
5. **Given** que el Recall@20 de INT8 frente a F32 queda por debajo de 0,97, **When** se decide, **Then** la app usa INT8 para preseleccionar candidatos y los reordena con la exacta, o se queda en F32, y la decisión queda registrada con las cifras.
6. **Given** una búsqueda acotada por ámbito (F17) o con documentos excluidos, **When** se usa INT8, **Then** respeta el ámbito y las exclusiones igual que la exacta.

---

### User Story 3 - El primer token, medido de verdad (Priority: P1)

Quien desarrolla mide la generación en el teléfono con prompts reales:
- el primer token con 4, 6 y 8 bloques;
- el prefill y la decodificación en tokens por segundo;
- de extremo a extremo: de enviar la pregunta al primer token y a la respuesta completa;
- la memoria (pico nativo y heap), el tamaño de la base de datos, la batería y la temperatura.

Repite la medida en una sesión sostenida de 20 a 30 minutos, con el teléfono en reposo entre preguntas y sin reposo.

**Why this priority**: el primer token es la latencia que nota quien usa la app, y lleva desde F11 por encima de 5 s como excepción (D-057). D-057 pide medir en sesión sostenida, porque el calor sube el primer token más de 1 s.

**Independent Test**: con Gemma cargada y un índice de prueba, ejecutar el banco del primer token y la sesión sostenida, y obtener sus cifras por pregunta y por minuto.

**Acceptance Scenarios**:

1. **Given** prompts con 4, 6 y 8 bloques reales, **When** se ejecutan, **Then** se informan, para cada número de bloques, los tokens del prompt (reales y estimados), el primer token, el prefill, la decodificación y el pico de memoria.
2. **Given** la sesión sostenida sin reposo, **When** pasan 20 a 30 minutos de preguntas seguidas, **Then** se informan el primer token y el estado térmico de cada pregunta, el p50 y el p95 por tramos de 5 minutos, y el consumo de batería por cada 10 minutos.
3. **Given** la sesión sostenida con reposo entre preguntas, **When** se ejecuta, **Then** se informan las mismas cifras, para separar el efecto del calor del de la carga.
4. **Given** que el teléfono entra en un estado térmico grave, **When** ocurre, **Then** la sesión lo registra y se detiene, y las cifras hasta ese momento quedan informadas.
5. **Given** el primer token de extremo a extremo, **When** se descompone, **Then** se informan por separado el embedding de la consulta, la búsqueda, la construcción del prompt y el prefill, para saber qué palanca ataca cada parte.

---

### User Story 4 - Palancas para bajar el primer token (Priority: P2)

Quien desarrolla evalúa las palancas de D-026 y D-057 con una regla de adopción fijada antes de medir:
- reutilizar el prefijo fijo del prompt (las reglas, que no cambian) para no repetir su prefill;
- una plantilla más corta;
- la decodificación especulativa, si el runtime la ofrece para el modelo;
- el número de bloques del contexto.

La versión nueva del runtime no es una palanca: se prueba al empezar (clarify, FR-019).

Cada palanca que cumple la regla se adopta en la app; las demás quedan registradas con sus cifras.

**Why this priority**: es el camino para cumplir el criterio 3 (primer token ≤ 5 s en sesión sostenida) sin perder la calidad de `v4`. Depende de las cifras de la historia 3.

**Independent Test**: para cada palanca, medir el primer token en el teléfono con y sin ella sobre las mismas preguntas y, si cambia el prompt o el contexto, la calidad en el harness del escritorio.

**Acceptance Scenarios**:

1. **Given** una palanca que el runtime no ofrece en la versión usada, **When** se evalúa, **Then** queda como «no disponible», con la versión y el motivo.
2. **Given** una palanca que no cambia el texto del prompt (reutilizar el prefijo, la decodificación especulativa), **When** se mide, **Then** se informa el primer token con y sin ella sobre las mismas preguntas, y se adopta si baja el p95 sostenido al menos 0,3 s y las respuestas no cambian de forma apreciable.
3. **Given** una palanca que cambia el prompt o el contexto (plantilla, bloques), **When** se evalúa, **Then** pasa por el harness del escritorio con la regla fijada antes de medir, y se informan su calidad y su primer token proyectado y medido.
4. **Given** las palancas adoptadas, **When** se repite la sesión sostenida, **Then** se informa el primer token resultante frente a los 5 s, y se decide si la excepción D-057 se cierra o se actualiza.

---

### User Story 5 - Cifras fiables: fp16 y estimación de tokens (Priority: P2)

Quien desarrolla mide en el teléfono cuántas respuestas deforman cifras con las activaciones por defecto de la GPU frente a FP32, y cuánto cuesta FP32 en el primer token. Mide también cuánto se desvía la estimación de tokens de D-034 de la cuenta real, y la corrige si se queda corta.

**Why this priority**: una respuesta con una cifra cambiada («20000» por 2020) es un error de veracidad que el usuario no puede detectar. Una estimación corta deja pasar prompts más largos que el límite, y sube el primer token sin que se vea.

**Independent Test**:
1. Con un conjunto de preguntas sobre contextos llenos de cifras, generar en el teléfono con las dos precisiones y contar las respuestas con cifras deformadas.
2. Con los fragmentos del corpus, comparar la estimación con la cuenta real de tokens.

**Acceptance Scenarios**:

1. **Given** el conjunto de preguntas con cifras, **When** se genera con las activaciones por defecto y con FP32, **Then** se informan las respuestas con alguna cifra deformada en cada caso y el primer token de cada precisión.
2. **Given** esas cifras, **When** se decide, **Then** queda registrada una salida: FP32, las activaciones por defecto con el riesgo documentado, u otra medida (por ejemplo, en el prompt), con su coste.
3. **Given** los fragmentos del corpus de evaluación y textos llenos de cifras, **When** se comparan la estimación y la cuenta real, **Then** se informan el error medio y el peor por tipo de texto.
4. **Given** que la estimación se queda corta, **When** se corrige, **Then** el límite de 2.000 tokens del prompt se cumple con la cuenta real, y los cambios en qué bloques entran pasan por el harness del escritorio.

---

### User Story 6 - El modo multimodal, medido y ajustado (Priority: P2)

Con el modo «Multimodal» activo, quien desarrolla mide:
- la búsqueda de páginas con vectores F32 y con INT8;
- el embedding de la consulta con la 440M;
- el primer token con cada detalle de página, en sesión sostenida;
- la recarga de Gemma con visión y el pico de memoria, y las palancas para reducirlos.

Revisa también la regla de seguimiento con páginas: una pregunta corta sobre otro documento no debe arrastrar la página de la pregunta anterior.

**Why this priority**: F20 dejó estas cifras como informativas (D-086, D-087). La regla de seguimiento hace que una pregunta corta responda con la página equivocada, y la recarga de 14,2 s es la espera más larga de la app.

**Independent Test**:
1. Con el modo activo y un banco de páginas, ejecutar el banco de búsqueda de páginas y el del primer token con páginas.
2. En el escritorio, evaluar las variantes de la regla de seguimiento con pares de preguntas que cambian de documento.

**Acceptance Scenarios**:

1. **Given** bancos de páginas de distintos tamaños, **When** se busca, **Then** se informan los percentiles de la búsqueda de páginas en F32 y en INT8, y se decide si las páginas usan INT8 con el mismo criterio de 20 ms.
2. **Given** la 440M en CPU, **When** se mide la consulta, **Then** se informan el p50 y el p95 frente al límite de 150 ms y frente a la 270M.
3. **Given** una respuesta con página después de otra sin página, **When** se mide la recarga, **Then** se informa su espera y se evalúa al menos una palanca para reducirla (por ejemplo, cargar Gemma con visión desde el principio cuando el ámbito tiene páginas), con su coste en memoria.
4. **Given** una pregunta corta sobre otro documento después de una pregunta con página, **When** se buscan las páginas, **Then** la variante adoptada devuelve la página del documento nuevo en el conjunto de pares de prueba, sin empeorar la búsqueda de texto de los seguimientos (Recall@20 de seguimientos de D-034).
5. **Given** el modo apagado, **When** se usa el chat, **Then** nada de esta historia cambia su comportamiento.

---

### User Story 7 - E4B frente a E2B en el teléfono (Priority: P3)

Quien desarrolla mide Gemma 4 E4B en el teléfono: su carga, el primer token con el contexto de D-034, el prefill, la decodificación y el pico de memoria, junto al embedder cargado. Con esas cifras y la calidad medida en F05 (+6,5 puntos con la regla automática, D-035), deja una recomendación sobre el perfil de modelo.

**Why this priority**: lo piden D-026 y D-035. Un cambio de modelo sería una feature aparte (D-035), así que F14 solo mide y recomienda.

**Independent Test**: con E4B copiado y verificado en el teléfono (aprobado en el clarify), ejecutar el banco del primer token con E4B.

**Acceptance Scenarios**:

1. **Given** E4B verificado en el teléfono, **When** se ejecuta el banco del primer token, **Then** se informan la carga en frío y con caché, el primer token, el prefill, la decodificación y el pico de memoria, con el mismo método que E2B.
2. **Given** que E4B no cabe en memoria o no carga en GPU, **When** se intenta, **Then** queda como «no cabe» o «no carga», con el error, y el teléfono sigue usable.
3. **Given** las cifras, **When** se decide el perfil, **Then** queda registrada la recomendación (E2B, E4B u opcional), y E4B se borra del teléfono al terminar.

---

### User Story 8 - Palancas de calidad baratas (Priority: P3)

Quien desarrolla vuelve a evaluar, con las cifras de F14, dos palancas de calidad que quedaron anotadas:
- la reordenación de los 20 primeros de la fusión con α = 0,5 (D-051);
- la búsqueda entre idiomas (D-062): normalizar la búsqueda por palabras según el idioma del documento y dar más peso a la búsqueda por significado cuando los idiomas no coinciden.

**Why this priority**: son mejoras de calidad, no de rendimiento. Se evalúan aquí porque su coste en el teléfono se mide con los mismos bancos. La búsqueda entre idiomas solo se evalúa: implementarla necesita el idioma de cada documento y otro índice por palabras, y va en otra feature si pasa la regla (clarify).

**Independent Test**: en el escritorio, ejecutar `order-review` y `crosslang-review` con cada variante y la regla fijada antes; en el teléfono, medir el coste de la reordenación en la búsqueda.

**Acceptance Scenarios**:

1. **Given** la reordenación α = 0,5, **When** se evalúa con la regla, **Then** se adopta o no, con sus cifras y su coste en milisegundos en el teléfono.
2. **Given** las variantes de la búsqueda entre idiomas, **When** se evalúan con `crosslang-review`, **Then** se informan las respuestas correctas de ES·EN y EN·ES frente a su referencia y si quedan a no más de 10 puntos (la regla de D-062). El resultado queda registrado; la implementación, si pasa, va en otra feature.
3. **Given** la reordenación adoptada, **When** se usa con documentos y preguntas en español, **Then** las cifras de `ablations` no empeoran.

---

### Edge Cases

- **Índice del usuario:** los bancos de prueba viven aparte y se retiran al terminar. Ninguna medida abre, vuelca ni modifica los documentos, el índice ni la conversación del usuario en el teléfono.
- **Sin espacio:** si no cabe un banco de 100k o un modelo, se mide hasta donde cabe y se documenta el espacio libre y el necesario.
- **Reconstrucción de INT8 durante una búsqueda:** la búsqueda usa la exacta hasta que el índice nuevo está completo; nunca mezcla un índice viejo con fragmentos nuevos.
- **Reconstrucción durante una indexación:** espera a que termine la tanda; no compite con la escritura ni con el embedder.
- **Muerte del proceso durante la reconstrucción o la precarga:** el índice queda desfasado y se repite en el siguiente arranque, en segundo plano.
- **Biblioteca vacía o con todo excluido:** no se construye el índice INT8; la búsqueda devuelve lo mismo que hoy.
- **Biblioteca muy pequeña:** si la exacta ya cumple el criterio, el coste de construir INT8 no compensa; la regla de cuándo construirlo se decide con las cifras.
- **Estado térmico:** cada medida registra el estado térmico; las de frío y caliente se informan por separado, y una medida en estado grave se descarta y se repite tras enfriar.
- **Batería y carga:** las medidas registran el nivel de batería y si el teléfono está cargando, porque la carga calienta el teléfono.
- **Palanca no disponible en el runtime:** queda registrada como «no disponible» con la versión; no bloquea el resto.
- **Actualización del runtime:** si una versión nueva cambia los vectores o las respuestas, no se adopta: falla el `embedding_golden` o las goldens de respuesta, se vuelve a la versión actual y se mide con ella.
- **E4B y la 270M:** no están en el teléfono. Se copian con `adb` a una carpeta de pruebas solo para medir (aprobado en el clarify) y se borran al terminar; nunca sustituyen a los modelos de la app ni cambian el modo.
- **Gemma con visión y sin ella:** nunca dos engines en GPU; la visión sigue en CPU (constitución IV).
- **El teléfono se bloquea durante una batería larga:** la medida se invalida y se repite con la pantalla despierta.

## Requirements *(mandatory)*

### Functional Requirements

**Bancos y método**

- **FR-001**: Los bancos de prueba de 10k, 50k y 100k fragmentos MUST tener la forma del índice real: vectores de 256 dimensiones normalizados, textos con la longitud del troceado de D-034, la entrada léxica con el normalizador de la app, documentos y carpetas para los ámbitos, y una parte de los documentos excluidos de las búsquedas.
- **FR-002**: Los bancos MUST construirse en el escritorio con textos y vectores verosímiles (derivados del corpus real, no aleatorios) y cargarse en el teléfono solo para medir. Son tooling de desarrollo: no van en la app de release ni se distribuyen (D-002).
- **FR-003**: Los bancos MUST vivir aparte del índice del usuario y retirarse al terminar. Ninguna medida modifica los documentos, el índice ni las conversaciones del usuario.
- **FR-004**: Cada medida MUST registrar el dispositivo, la versión de Android, la versión de la app y de los runtimes, los modelos y su backend, el estado térmico, la batería y si está cargando.
- **FR-005**: Las medidas MUST seguir un protocolo común: calentamiento descartado, repeticiones suficientes para el p95 y el p99, y las mismas preguntas en cada variante (las de la muestra de F10, ampliadas si hace falta para el p99).

**Búsqueda e INT8**

- **FR-006**: Para cada tamaño de banco, el sistema MUST medir el embedding de la consulta (solo y dentro de la búsqueda), la vectorial F32, la vectorial INT8 (precargada y sin precargar), la léxica, la híbrida total y la acotada por un ámbito pequeño, con p50, p95 y p99.
- **FR-007**: Para cada tamaño, el sistema MUST medir el tiempo de construir y de precargar el índice INT8, la memoria de la precarga y el tamaño de la base de datos, y el Recall@20 de INT8 frente a F32.
- **FR-008**: Si el p95 vectorial F32 con 10k fragmentos pasa de 20 ms (constitución IV), la app MUST usar INT8 con precarga para la búsqueda vectorial de fragmentos, con un Recall@20 frente a F32 ≥ 0,97. Si no se alcanza, MUST usar INT8 para preseleccionar candidatos y reordenarlos con la exacta, o quedarse en F32 con la decisión documentada.
- **FR-009**: El índice INT8 MUST marcarse como desfasado cuando cambian los fragmentos (fin de una indexación, borrado, reindexado o cambio de modo de índice) y MUST reconstruirse en segundo plano, sin bloquear la búsqueda ni la indexación.
- **FR-010**: Mientras el índice INT8 esté desfasado, se esté reconstruyendo o no esté precargado, la búsqueda MUST usar la exacta. Nunca se usa un índice INT8 parcial o viejo (constitución V).
- **FR-011**: Tras la muerte del proceso, el índice INT8 al día MUST precargarse en segundo plano en el siguiente arranque, sin retrasar la primera búsqueda.
- **FR-012**: La búsqueda con INT8 MUST respetar los ámbitos de F17 y las exclusiones igual que la exacta.
- **FR-013**: El total de la recuperación híbrida con 10k fragmentos MUST medirse otra vez frente a 200 ms con INT8. Si sigue por encima, MUST quedar una decisión con el desglose por partes.

**Primer token**

- **FR-014**: El sistema MUST medir, con prompts reales de 4, 6 y 8 bloques, el primer token, el prefill, la decodificación, los tokens del prompt (reales y estimados) y el pico de memoria.
- **FR-015**: El sistema MUST medir de extremo a extremo (de enviar la pregunta al primer token y a la respuesta completa), con el desglose por embedding, búsqueda, construcción del prompt y prefill.
- **FR-016**: El sistema MUST medir una sesión sostenida de 20 a 30 minutos sin reposo y otra con reposo entre preguntas, con el primer token y el estado térmico por pregunta, los percentiles por tramos de 5 minutos y la batería por cada 10 minutos.
- **FR-017**: Las palancas del primer token (prefijo reutilizado, plantilla más corta, decodificación especulativa y número de bloques) MUST evaluarse con esta regla, fijada antes de medir: se adopta la que baja el p95 del primer token en la sesión sostenida al menos 0,3 s. Si cambia el prompt o el contexto, MUST pasar además por el harness del escritorio con las respuestas correctas a no más de 1 punto de `v4` (81,1 %) y las negativas y las citas en los umbrales del informe.
- **FR-018**: Las palancas adoptadas MUST implementarse en la app y medirse otra vez en sesión sostenida. El resultado MUST cerrar o actualizar la excepción D-057 frente a los 5 s (constitución IV).
- **FR-019**: Las versiones nuevas de los runtimes (LiteRT-LM 0.19.1 y SQLite-Vector 1.2.0) MUST probarse al empezar, antes de las medidas. Se adoptan si pasan el `embedding_golden`, las goldens de respuesta y la batería de pruebas, y entonces todas las medidas de F14 se hacen con ellas. Si no, se quedan las actuales y el motivo queda en DECISIONS.

**Fiabilidad de las cifras**

- **FR-020**: El sistema MUST medir en el teléfono, con un conjunto de preguntas sobre contextos llenos de cifras, cuántas respuestas deforman alguna cifra con las activaciones por defecto y con FP32, y el primer token de cada precisión.
- **FR-021**: Con esas cifras MUST registrarse una salida para el riesgo de fp16. FP32 se adopta si el p95 del primer token en la sesión sostenida sube ≤ 0,5 s. Si no, se quedan las activaciones por defecto con una instrucción en el prompt para copiar las cifras tal cual, evaluada en el harness con la regla de FR-017 y medida otra vez en el teléfono; el riesgo que quede se documenta.
- **FR-022**: El sistema MUST comparar la estimación de tokens con la cuenta real sobre el corpus de evaluación y sobre textos llenos de cifras. Si se queda corta, la estimación MUST corregirse para que el límite de 2.000 tokens del prompt se cumpla con la cuenta real, y el cambio MUST pasar por el harness si altera qué bloques entran.

**Modo multimodal**

- **FR-023**: Con el modo activo, el sistema MUST medir la búsqueda de páginas en F32 y en INT8 con bancos de páginas de distintos tamaños, y aplicar a las páginas el mismo criterio de 20 ms que a los fragmentos.
- **FR-024**: El sistema MUST medir la consulta con la 440M (p50 y p95) frente a los 150 ms y frente a la 270M.
- **FR-025**: El sistema MUST medir el primer token con cada detalle de página en sesión sostenida, la recarga de Gemma con visión y el pico de memoria, y MUST evaluar al menos una palanca para la recarga con su coste en memoria.
- **FR-026**: La regla de seguimiento MUST revisarse para las páginas: una pregunta corta sobre otro documento no debe arrastrar la página de la anterior. La variante adoptada MUST evaluarse con pares de preguntas que cambian de documento y MUST conservar el Recall@20 de los seguimientos de texto de D-034.
- **FR-027**: Con el modo apagado, nada de lo que añade esta sección MUST cambiar el comportamiento de la app.

**Perfil de modelo**

- **FR-028**: El sistema MUST medir Gemma 4 E4B en el teléfono con el mismo método que E2B (carga, primer token, prefill, decodificación y pico de memoria, con el embedder cargado). El fichero se copia con `adb` desde el escritorio a una carpeta de pruebas, MUST verificarse por SHA-256 y borrarse al terminar. La 270M sigue el mismo camino para FR-024.
- **FR-029**: MUST quedar registrada una recomendación de perfil de modelo con esas cifras y la calidad de F05. Un cambio de modelo en la app queda fuera de F14 (D-035).

**Palancas de calidad**

- **FR-030**: La reordenación α = 0,5 (D-051) MUST evaluarse otra vez con su regla de adopción y su coste medido en el teléfono.
- **FR-031**: La búsqueda entre idiomas MUST evaluarse en el escritorio con `crosslang-review`, con al menos las variantes de D-062 (palabras vacías y raíces según el idioma del documento, y más peso a la búsqueda por significado cuando los idiomas no coinciden), y su resultado MUST quedar en DECISIONS frente a la regla de 10 puntos. F14 no la implementa en la app ni cambia la tarjeta «Búsqueda multilingüe»: si pasa la regla, va en otra feature.
- **FR-032**: La reordenación, si se adopta, MUST conservar las cifras de `ablations` con documentos y preguntas en español.

**Entregables y límites**

- **FR-033**: `specs/014-ondevice-performance/benchmarks.md` MUST recoger las cifras reales de todos los bloques de la tabla «Benchmarks en el dispositivo» del informe, con dispositivo y versiones. Cada celda que no se mida MUST decir por qué.
- **FR-034**: El informe del RAG móvil MUST remitir a esas cifras donde hoy da presupuestos (latencia del motor vectorial y objetivo del primer token).
- **FR-035**: Cada decisión (INT8, bloques, perfil de modelo, primer token, fp16, estimación de tokens, regla de seguimiento con páginas, recarga con visión, cada palanca) MUST quedar en DECISIONS con sus cifras.
- **FR-036**: Los diagnósticos y bancos MUST existir solo en debug y en las pruebas instrumentadas. La app de release no los incluye, no usa la red y no escribe en los logs prompts, respuestas, textos de documentos ni imágenes.
- **FR-037**: Ninguna medida MUST cargar dos engines en GPU a la vez (constitución IV).
- **FR-038**: Con el modo apagado y sin palancas adoptadas, `run_eval.py ablations` MUST dar las mismas cifras que en F20.

### Key Entities

- **Banco de prueba**: un índice de N fragmentos (10k, 50k o 100k), y en su caso de páginas, con la forma del real y su procedencia. Vive aparte del índice del usuario y se retira al terminar.
- **Medida**: una ejecución de un banco con sus condiciones (dispositivo, versiones, modelos, backend, estado térmico, batería, carga) y sus percentiles por parte.
- **Índice INT8**: la copia cuantizada de los vectores, reconstruible, con su estado (al día, desfasado, reconstruyéndose, precargado) guardado en los metadatos del índice.
- **Palanca**: un cambio que se evalúa (de rendimiento o de calidad), con su regla de adopción escrita antes de medir, sus cifras y su resultado (adoptada, descartada o no disponible).
- **Sesión sostenida**: una serie de preguntas de 20 a 30 minutos, con o sin reposo, con el primer token y el estado térmico por pregunta.
- **Decisión**: una entrada de DECISIONS con las cifras que la justifican y lo que afecta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `benchmarks.md` tiene cifras medidas en el teléfono de referencia para todos los bloques del informe (arranque, embedding, búsqueda a 10k, 50k y 100k, generación con 4, 6 y 8 bloques, extremo a extremo, recursos y sesión sostenida), con dispositivo y versiones. Ninguna celda queda como presupuesto sin explicación.
- **SC-002**: Con INT8 adoptado, la búsqueda vectorial de fragmentos con 10k da un p95 ≤ 20 ms en el teléfono de referencia y un Recall@20 frente a F32 ≥ 0,97; o la decisión de quedarse en F32 queda documentada con las cifras.
- **SC-003**: La recuperación híbrida completa con 10k fragmentos da un p95 ≤ 200 ms en el teléfono de referencia, o queda una decisión con el desglose por partes.
- **SC-004**: En la sesión sostenida de 20 a 30 minutos, el primer token de extremo a extremo del chat de texto da un p95 ≤ 5 s, o la excepción D-057 queda actualizada con las cifras nuevas y la decisión del usuario.
- **SC-005**: Tras matar el proceso, la primera búsqueda responde igual que antes (con la exacta), y el índice INT8 al día vuelve a estar precargado en segundo plano; ninguna búsqueda usa un índice INT8 desfasado en las pruebas de indexación, borrado y reindexado.
- **SC-006**: Cada palanca evaluada (rendimiento y calidad) tiene su regla escrita antes de medir, sus cifras y su resultado en DECISIONS. Las adoptadas cumplen su regla de calidad en el harness del escritorio.
- **SC-007**: Con el modo multimodal activo, en el conjunto de pares que cambian de documento, la variante adoptada de la regla de seguimiento cita la página del documento nuevo en al menos el 90 % de los pares, y el Recall@20 de los seguimientos de texto no baja de 0,98 (D-034).
- **SC-008**: La tasa de respuestas con cifras deformadas, con cada precisión, y la estimación de tokens frente a la cuenta real quedan medidas, con una decisión registrada para cada una.
- **SC-009**: Con el modo apagado y sin palancas adoptadas, `run_eval.py ablations` da las mismas cifras que en F20, y la batería de pruebas pasa.
- **SC-010**: Ningún banco ni diagnóstico existe en la app de release, y al terminar el teléfono no conserva bancos, modelos de prueba ni datos de prueba; el documento, el índice y la conversación del usuario siguen intactos.

## Assumptions

- **Teléfono:** las medidas se hacen en el A063, con el modo «Multimodal» activado (440M). Los vectores de texto de la 440M son idénticos a los de la 270M (D-078), así que las cifras de búsqueda y de generación de texto valen para los dos modos; solo cambian el embedding de la consulta y la memoria, que se informan aparte.
- **Modelos de prueba:** la 270M (157 MB) y Gemma 4 E4B (3,66 GB) no están en el teléfono. Se copian con `adb` desde el escritorio a una carpeta de pruebas de la app de depuración, se verifican por SHA-256 y se borran al terminar (clarify). No pasan por la red ni sustituyen a los modelos de la app.
- **Bancos:** se construyen en el escritorio a partir del corpus real de F05 (textos y vectores reales, replicados y variados hasta el tamaño pedido), y se cargan con `adb` en el almacenamiento privado de la app de depuración, aparte del índice del usuario. El banco de 100k ocupa del orden de 0,5 GB.
- **Calidad:** se evalúa en el harness de `offline/` con los mismos ficheros de modelo. Las variantes que deciden se revisan a mano por el asistente (D-033), como en D-035 y D-057, y la regla de cada palanca se fija antes de medir.
- **Pantallas:** F14 no añade pantallas de producto ni cambia textos. Si una palanca adoptada cambiara una espera que la app anuncia (por ejemplo, las del detalle de las páginas, D-086), el texto se ajusta a la cifra nueva.
- **Runtimes:** LiteRT-LM 0.19.1 y SQLite-Vector 1.2.0 se prueban al empezar (clarify). Lo que no necesita el teléfono (compilación, JVM, lint y emuladores) se comprueba en la implementación; las goldens en el A063 abren la fase del teléfono, antes de medir. Si fallan allí, se vuelve a las versiones actuales y se mide con ellas.
- **Fuera de alcance:** cambiar de modelo en la app (D-035); implementar la búsqueda entre idiomas (clarify); un índice aproximado (ANN o USearch), que el informe reserva para más de 300k vectores; el pulido, R8 y la release (F15).
- **Fase del teléfono:** se pregunta al usuario antes de empezarla, como en cada feature, y se aplican las reglas de siempre (ajustes anotados y restaurados, nada del usuario se abre ni se captura, volcados en `/data/local/tmp`, pantalla despierta en baterías largas).

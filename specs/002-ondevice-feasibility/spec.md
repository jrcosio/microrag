# Especificación de feature: Viabilidad on-device (F02, fase 0)

**Feature Branch**: `002-ondevice-feasibility`

**Created**: 2026-10-25

**Status**: Draft

**Input**: Descripción del usuario: «F02 — Viabilidad on-device (fase 0 del informe RAG). Feature técnica sin UI de producto: diagnóstico interno solo en depuración para demostrar que Gemma 4 E2B y EmbeddingGemma 2 funcionan en el teléfono de referencia y medir su rendimiento real antes de construir descarga, base de datos o ingesta.» Ver `docs/roadmap/FEATURES.md` (F02).

**Fuentes**:
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - Resumen y Configuración base;
  - Distribución de modelos;
  - Embeddings con EmbeddingGemma 2 (Prefijos, Truncación y normalización, API, Backend);
  - Generación;
  - Memoria y ciclo de vida;
  - Evaluación → Benchmarks en el dispositivo;
  - Plan del POC, fase 0;
  - Puntos a verificar.
- Constitución, principios I, II, IV, V, VI y VIII.
- DECISIONS D-011 y D-012.
- INVENTARIO G35 y G36.

**Teléfono de referencia** (detectado por adb el 2026-10-25):

| Campo | Valor |
|---|---|
| Modelo | Nothing Phone (1), A063 (`Spacewar`) |
| SoC | Qualcomm Snapdragon 778G+ (SM7325, `lahaina`) |
| GPU | Adreno 642L con OpenCL |
| RAM | 12 GB (11,5 GB visibles) |
| Android | 15 (API 35), arm64-v8a |
| Libre en `/data` | 111 GB |

## Clarifications

### Session 2026-10-25

- Q: ¿Se descargan y prueban los cinco ficheros de modelo o solo una parte? → A: los cinco (Gemma 4 E2B genérico y GPU; EmbeddingGemma 2 de 740M, 440M y 270M), ≈ 5,7 GB en el ordenador y en el teléfono.
- Q: ¿Se mide en F02 la compatibilidad CPU↔GPU de los embeddings? → A: sí. Para cada variante se embeben ≈ 20 textos de prueba (consultas y fragmentos de documento) en CPU y en GPU, y se informan el coseno mínimo y el medio entre ambos backends, con umbral ≥ 0,99 (informe, «Condición para mezclar vectores»).
- Q: ¿Se mide Gemma 4 E2B también en CPU? → A: solo como plan B. Gemma se mide en GPU; si un fichero no carga o falla en GPU, ese fichero se mide en CPU.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Modelos verificados en el teléfono antes de usarlos (Priority: P1)

Quien desarrolla copia los ficheros de modelo desde su ordenador al almacenamiento privado de la app siguiendo un procedimiento documentado. La herramienta de diagnóstico muestra cada fichero esperado con su estado: ausente, verificando, verificado (huella correcta) o corrupto o distinto (huella incorrecta). Solo un fichero verificado puede cargarse.

**Why this priority**: es el principio V de la constitución («nunca listo sin verificación») y el requisito previo de cualquier medición. Un modelo distinto o corrupto invalidaría todas las cifras.

**Independent Test**:
1. Copiar los ficheros según el quickstart.
2. Abrir el diagnóstico y comprobar que cada fichero aparece como verificado.
3. Copiar un fichero alterado y comprobar que aparece como incorrecto y que no se puede cargar.

**Acceptance Scenarios**:

1. **Given** un fichero de modelo copiado íntegro, **When** se abre el diagnóstico, **Then** su huella SHA-256 coincide con la publicada y aparece como «verificado», con nombre, tamaño y huella abreviada.
2. **Given** un fichero ausente, **When** se abre el diagnóstico, **Then** aparece como «ausente», con la instrucción para copiarlo, y sus pruebas quedan deshabilitadas.
3. **Given** un fichero con contenido distinto al publicado, **When** se verifica, **Then** aparece como «huella incorrecta» y ninguna prueba puede cargarlo.

---

### User Story 2 - Medir el embedding de consultas en el teléfono (Priority: P1)

Quien desarrolla lanza, para cada variante de EmbeddingGemma 2 (740M, 440M y 270M) y cada backend (CPU y GPU), la prueba de embedding de consulta. Obtiene:
- el tiempo de inicialización en frío;
- la latencia p50 y p95 de embeber una consulta corta;
- la confirmación de que el vector tiene 256 dimensiones y norma unitaria;
- la longitud máxima de entrada declarada por el modelo.

**Why this priority**: es el criterio de salida de la fase 0 (consulta < 150 ms p95 en CPU) y alimenta la elección de variante (G36) para la evaluación de F05.

**Independent Test**: con las tres variantes verificadas, ejecutar la prueba de embeddings y obtener las cifras de las seis combinaciones, o el motivo de fallo de alguna.

**Acceptance Scenarios**:

1. **Given** una variante verificada, **When** se ejecuta su prueba en CPU, **Then** se informan:
   - el tiempo de inicialización;
   - p50 y p95 sobre las repeticiones medidas, tras un calentamiento descartado;
   - la dimensión (256);
   - la norma L2 (1 ± 0,00001);
   - la longitud máxima de entrada.
2. **Given** la misma variante, **When** se ejecuta en GPU, **Then** se informan las mismas métricas o, si la GPU no está disponible para ese modelo, el error exacto, sin detener las demás combinaciones.
3. **Given** las tres variantes medidas, **When** se consulta el resumen, **Then** se indica si al menos una cumple la consulta < 150 ms p95 en CPU (criterio de salida).
4. **Given** una variante que funciona en CPU y en GPU, **When** se ejecuta la prueba de compatibilidad, **Then** se informan el coseno mínimo y el medio entre los vectores de ambos backends para el mismo conjunto de textos, y si cumple ≥ 0,99.

---

### User Story 3 - Medir la generación de respuestas de Gemma 4 E2B (Priority: P1)

Quien desarrolla carga Gemma 4 E2B con cada uno de sus dos ficheros (genérico y específico de GPU) y lanza una generación a partir de un prompt de unos 2.000 tokens con el formato del informe (fuentes numeradas y pregunta). Obtiene:
- el tiempo de inicialización en frío;
- el tiempo hasta el primer token (TTFT);
- la velocidad de prefill y de decodificación (tokens/s);
- la memoria pico;
- el texto generado, para comprobar que es coherente.

**Why this priority**: la latencia percibida la domina el prefill del LLM (informe, Resumen). Si el TTFT es inaceptable en el teléfono de referencia, cambia el diseño del resto del roadmap.

**Independent Test**: con los dos ficheros de Gemma verificados, ejecutar la prueba de generación de cada uno y obtener sus cifras y una respuesta legible.

**Acceptance Scenarios**:

1. **Given** un fichero de Gemma verificado, **When** se ejecuta la prueba de generación, **Then** se informan:
   - el número real de tokens del prompt;
   - el tiempo de inicialización;
   - el TTFT;
   - el prefill y el decode en tokens/s;
   - la memoria pico;
   - el texto generado.
2. **Given** que la prueba está en curso, **When** se pulsa «Cancelar», **Then** la generación se detiene en menos de 1 segundo, la interfaz sigue respondiendo y la prueba queda marcada como cancelada.
3. **Given** un fallo de carga (memoria insuficiente, backend no disponible, fichero incompatible), **When** ocurre, **Then** se muestra el error concreto y la app no se cierra.

---

### User Story 4 - Informe de viabilidad y decisiones (Priority: P2)

Al terminar las pruebas, quien desarrolla obtiene un informe con todas las cifras, el dispositivo, las versiones (app, runtime y huella de cada modelo) y el estado térmico al inicio y al final. El informe se puede recuperar desde el ordenador. Con él se toman y registran las decisiones de la fase 0:
- qué fichero de Gemma usar (G35);
- qué backend usar para el embedding de consultas;
- qué variantes de EmbeddingGemma 2 pasan a la evaluación de F05 (G36).

**Why this priority**: las cifras sustituyen a los presupuestos del informe (constitución VI) y condicionan F03, F05 y F10.

**Independent Test**: tras una sesión de pruebas, recuperar el informe desde el ordenador y comprobar que contiene todas las combinaciones, los metadatos del dispositivo y las versiones.

**Acceptance Scenarios**:

1. **Given** una sesión de pruebas terminada, **When** se exporta el informe, **Then** queda guardado en el almacenamiento privado de la app con fecha y hora, y se puede copiar al ordenador con el procedimiento del quickstart.
2. **Given** el informe, **When** se revisa, **Then** incluye:
   - por combinación: modelo, fichero, huella, backend, repeticiones, p50/p95, inicialización, TTFT, prefill/decode y memoria;
   - del dispositivo: modelo, Android, SoC, RAM, estado térmico inicial y final, y si estaba cargando.

---

### User Story 5 - Uso seguro de memoria y GPU (Priority: P2)

Mientras se ejecutan las pruebas, nunca hay dos motores ocupando la GPU a la vez. Los motores se liberan al cambiar de prueba, al salir del diagnóstico o cuando la app pasa a segundo plano. Ningún trabajo pesado bloquea la interfaz.

**Why this priority**: principio IV de la constitución. Los motores de varios GB en un móvil de 12 GB pueden agotar la memoria si no se gestionan.

**Independent Test**:
1. Lanzar una prueba de embeddings en GPU y a continuación una de Gemma en GPU. El primer motor debe liberarse antes de cargar el segundo.
2. Durante cualquier prueba, la interfaz responde y se puede cancelar.

**Acceptance Scenarios**:

1. **Given** un motor de embeddings cargado en GPU, **When** se carga Gemma en GPU, **Then** el motor de embeddings se libera antes.
2. **Given** pruebas en curso, **When** la app pasa a segundo plano, **Then** la prueba se cancela, los motores se liberan y la cancelación queda registrada.

### Edge Cases

- **Fichero ausente, incompleto o con huella distinta:** se informa y no se carga (US1).
- **GPU no disponible para un modelo** (delegado OpenCL ausente o modelo no compatible): la combinación se registra como «no disponible», con el motivo, y se siguen midiendo las demás.
- **Memoria insuficiente al cargar Gemma:** el error se captura, la app no se cierra y la combinación queda registrada como fallida.
- **Prompt que no da exactamente 2.000 tokens:** se informa el número real de tokens. La comparación se hace sobre esa cifra.
- **Estrangulamiento térmico:** se registra el estado térmico al inicio y al final de cada prueba. Si es «severo» o peor al empezar, la prueba avisa y espera a confirmar antes de medir.
- **Cancelación en mitad de la inicialización:** la operación termina de forma limpia y el motor no queda a medio cargar.
- **App en segundo plano o proceso eliminado durante una prueba:** se pierde esa medición (no se mide en segundo plano). Al volver, los resultados ya guardados siguen disponibles.
- **Almacenamiento insuficiente al copiar modelos:** el procedimiento del quickstart indica el espacio necesario (≈ 5,6 GB para los cinco ficheros más el pico de una copia) y cómo comprobarlo. La importación comprueba el espacio libre antes de copiar y, si falta, marca el fichero con error.

## Requirements *(mandatory)*

### Functional Requirements

**Disponibilidad y aprovisionamiento**

- **FR-001**: La herramienta de diagnóstico MUST existir solo en la variante de depuración y MUST NOT incluirse en la build de publicación.
- **FR-002**: La herramienta MUST conocer los cinco ficheros de modelo esperados:
  - Gemma 4 E2B: genérico y GPU;
  - EmbeddingGemma 2: 740M, 440M y 270M.

  De cada uno conoce el nombre, el tamaño y la huella SHA-256 publicados, fijados a una revisión concreta del repositorio de origen (D-011).
- **FR-003**: Los modelos MUST llegar al teléfono copiándose desde el ordenador de desarrollo mediante un procedimiento documentado y reproducible. Primero se copian a la carpeta de desarrollo de la app. Después la herramienta los importa con verificación al almacenamiento privado sin copia de seguridad (`noBackupFilesDir/models`, research R7).
- **FR-004**: La app MUST NOT descargar modelos ni hacer ninguna llamada de red en esta feature, y MUST seguir sin declarar permisos de red (constitución I, invariante P4 de F01).

**Verificación**

- **FR-005**: Antes de cargar un fichero, la herramienta MUST calcular su SHA-256 y compararlo con el publicado. Solo los ficheros verificados se pueden cargar (constitución V).
- **FR-006**: El estado de cada fichero MUST mostrarse sin depender solo del color: ausente, pendiente de importar, verificando (con progreso real), verificado, huella incorrecta o error (con su motivo).

**Pruebas de embeddings**

- **FR-007**: Para cada variante verificada de EmbeddingGemma 2 y cada backend (CPU y GPU), la herramienta MUST medir:
  - la inicialización en frío;
  - la latencia del embedding de una consulta corta, con calentamiento descartado y percentiles p50 y p95 sobre las repeticiones medidas.
- **FR-007b**: Para cada variante y backend, la herramienta MUST medir también la latencia (p50 y p95) de embeber un fragmento de documento de ≈ 350 tokens con el prefijo de documento. Anticipa el ritmo de ingesta de F07 (añadido en el plan, research R3).
- **FR-008**: Cada embedding MUST:
  - usar el prefijo de consulta documentado por LiteRT-LM;
  - truncarse a 256 dimensiones y renormalizarse con L2.

  La herramienta MUST comprobar que el resultado tiene 256 dimensiones y norma 1 ± 0,00001.
- **FR-009**: La herramienta MUST leer y mostrar la longitud máxima de entrada declarada por cada modelo de embeddings.
- **FR-009b**: Para cada variante que funcione en ambos backends, la herramienta MUST embeber el mismo conjunto de ≈ 20 textos de prueba (consultas y fragmentos de documento, con sus prefijos respectivos) en CPU y en GPU. MUST informar el coseno mínimo y el medio entre ambos backends y marcar la variante como compatible solo si el mínimo es ≥ 0,99. El resultado decide si consultas e ingesta pueden usar backends distintos.

**Pruebas de generación**

- **FR-010**: Para cada fichero verificado de Gemma 4 E2B, la herramienta MUST:
  - cargar el modelo en GPU y, solo si la carga o la generación en GPU fallan, repetir la prueba de ese fichero en CPU como plan B, registrando el motivo del fallo en GPU;
  - generar a partir de un prompt de ≈ 2.000 tokens con el formato del informe (fuentes numeradas y pregunta);
  - medir la inicialización en frío, el TTFT, el prefill y el decode (tokens/s), la memoria pico y el número real de tokens del prompt;
  - mostrar el texto generado.
- **FR-011**: Toda prueba en curso MUST poder cancelarse, y la cancelación MUST hacerse efectiva en menos de 1 segundo.

**Recursos**

- **FR-012**: Nunca MUST haber más de un motor cargado en GPU a la vez. Cargar uno MUST liberar antes el anterior.
- **FR-013**: Los motores MUST liberarse al salir del diagnóstico y cuando la app pase a segundo plano. Una prueba en curso se cancela en ese momento.
- **FR-014**: Toda inicialización e inferencia MUST ejecutarse fuera del hilo de interfaz, en las colas dedicadas de F01 (LLM y embeddings). La interfaz MUST seguir respondiendo durante las pruebas.
- **FR-015**: Cada prueba MUST registrar el estado térmico del dispositivo al inicio y al final, y si estaba cargando. Si el estado al empezar es «severo» o peor, MUST avisar antes de medir.

**Resultados y decisiones**

- **FR-016**: La herramienta MUST mostrar los resultados en pantalla y MUST guardarlos como informe con fecha y hora en el almacenamiento privado, recuperable desde el ordenador. El informe incluye:
  - cifras por combinación;
  - dispositivo, versiones de la app y del runtime;
  - huellas de los modelos;
  - estado térmico.
- **FR-017**: La herramienta MUST resumir los criterios de salida de la fase 0:
  - TTFT medido;
  - si alguna variante cumple la consulta < 150 ms p95 en CPU.
- **FR-018**: Las decisiones de la fase 0 MUST registrarse en `docs/roadmap/DECISIONS.md`, con las cifras que las justifican:
  - fichero de Gemma (G35);
  - backend de embeddings de consulta;
  - variantes candidatas para F05 (G36).
- **FR-019**: La herramienta MUST NOT escribir en los registros del sistema el texto de los prompts ni de las respuestas (constitución I). El prompt de prueba es un texto sintético del proyecto, no un dato de usuario.
- **FR-020**: La interfaz del diagnóstico MUST construirse con los componentes del design system (`Lisdata*`) y los textos MUST estar en recursos. Al ser una herramienta interna, MUST NOT presentarse como UI de producto.

### Key Entities *(include if data involved)*

- **Fichero de modelo esperado**: nombre, modelo (Gemma 4 E2B o EmbeddingGemma 2 con su variante), tamaño, huella publicada y revisión de origen. Su estado local es ausente, verificando, verificado o incorrecto.
- **Combinación de prueba**: modelo × fichero × backend (CPU o GPU) × tipo de prueba (embedding o generación).
- **Resultado de prueba**: la combinación, las métricas (inicialización, p50/p95, TTFT, prefill/decode, memoria pico, dimensiones y norma, tokens del prompt), su estado (completada, cancelada, fallida con motivo), el estado térmico inicial y final, y fecha y hora.
- **Informe de viabilidad**: el conjunto de resultados de una sesión, con los metadatos del dispositivo y las versiones.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los ficheros se verifican por huella antes de cargarse. En las pruebas, un fichero con huella incorrecta no se carga nunca.
- **SC-002**: El informe contiene resultados de las 6 combinaciones de embeddings (3 variantes × 2 backends) y de los 2 ficheros de Gemma en GPU (más CPU como plan B para un fichero que falle en GPU), o el motivo documentado de las que no puedan ejecutarse.
- **SC-003**: Se conoce el TTFT de Gemma 4 E2B con un prompt de ≈ 2.000 tokens en el teléfono de referencia (criterio de salida de la fase 0).
- **SC-004**: Al menos una variante de EmbeddingGemma 2 embebe una consulta en menos de 150 ms (p95, CPU) en el teléfono de referencia. Si ninguna lo consigue, queda una decisión documentada y aprobada por el usuario.
- **SC-005**: En ningún momento hay dos motores cargados en GPU a la vez (verificado por prueba automática y por los registros de la sesión).
- **SC-006**: Durante cualquier prueba la interfaz responde: cancelar surte efecto en menos de 1 segundo y no hay avisos de «la aplicación no responde».
- **SC-007**: La build de publicación no contiene la herramienta de diagnóstico ni permisos de red.
- **SC-008**: Las decisiones de G35 y G36 y del backend de consulta quedan registradas en DECISIONS con sus cifras antes de cerrar la feature.
- **SC-009**: Para cada variante ejecutable en ambos backends se conoce el coseno mínimo CPU↔GPU sobre el conjunto de prueba, y por tanto si consultas e ingesta pueden usar backends distintos (umbral ≥ 0,99).

## Assumptions

- **Origen y versión de los modelos:** Hugging Face `litert-community`, fijado al commit y a las huellas de research R2.
- **Descarga al ordenador:** se descargan los cinco ficheros (≈ 5,6 GB, confirmado en clarify) en el ordenador de desarrollo, fuera del repositorio (`*.litertlm` está en `.gitignore`). Es tooling de desarrollo, no tráfico de la app.
- **Protocolo de medición por defecto:**
  - embeddings: 3 repeticiones de calentamiento y 30 medidas por combinación;
  - generación: 1 de calentamiento y 3 medidas por fichero, generando 128 tokens.
  - compatibilidad CPU↔GPU: 20 textos (10 consultas y 10 fragmentos) por variante;
  - antes de cada bloque se espera a estado térmico «ninguno» o «leve», con un tope de 5 minutos (fijado en el plan, research R6).
- **Prompt de prueba:** texto sintético en español con el formato de la plantilla del informe, construido a partir de la documentación del propio proyecto (no son datos de usuario). Mide ≈ 2.000 tokens según el tokenizador del modelo.
- **Gemma en GPU con plan B en CPU** (confirmado en clarify): se mide en GPU, el backend previsto por el informe. CPU solo se mide para un fichero cuyo uso en GPU falle.
- **Release:** la herramienta solo existe en debug, siguiendo el mismo patrón de source set que F01 (R7).
- **Dependencias:** F01 (design system, colas dedicadas y lanzador de debug). No depende de F03–F05.
- **Fuera de alcance:**
  - descarga de modelos en la app (F03);
  - base de datos y SQLite-Vector (F04);
  - harness offline y compatibilidad de embeddings frente a golden (F04/F05);
  - recuperación y RAG completo;
  - UI de producto;
  - benchmarks de búsqueda (F14).

# Especificación de feature: Harness offline de evaluación (F05)

**Feature Branch**: `005-offline-eval-harness`

**Created**: 2026-10-26

**Status**: Draft

**Input**: Descripción del usuario: «F05 — Harness offline de evaluación. Feature técnica en el escritorio, sin UI: fijar con medidas sobre un corpus representativo los parámetros de recuperación y contexto antes de indexar en el móvil, siguiendo el informe: la calidad se ajusta en escritorio, la latencia se mide en el móvil.» Ver `docs/roadmap/FEATURES.md` (F05).

**Fuentes**:
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - Decisión previa: de dónde sale el corpus;
  - Embeddings (truncación);
  - Parsing y chunking;
  - Búsqueda léxica en español;
  - Recuperación y selección de contexto;
  - Generación (plantilla de prompt);
  - Pipeline offline (Python);
  - Evaluación: conjunto de prueba, métricas y umbrales, ablaciones.
- Constitución v1.1.0, principios III (no negociable: «los parámetros solo cambian con una evaluación documentada y una entrada en DECISIONS»), IV (primer token ≤ 5 s con ≈ 2.000 tokens), VI y VIII.
- DECISIONS:
  - D-002: ingesta en el dispositivo; Python solo evalúa;
  - D-012: `litert-lm-api` en el escritorio;
  - D-023: Gemma 4 E2B, fichero genérico;
  - D-025: EmbeddingGemma 2 270M;
  - D-026: presupuesto de primer token;
  - D-030 y D-031.
- INVENTARIO G37 (corpus de evaluación).
- Lo construido en F04: `schema.sql`, normalizador en Python idéntico al de la app, construcción de entradas de embedding y truncado, y los golden de compatibilidad.

**Verificado al redactar** (2026-10-26):
- Gemma 4 E4B está publicada en `litert-community/gemma-4-E4B-it-litert-lm`: fichero genérico de 3,66 GB, Apache-2.0, sin acceso restringido.
- El Mac del proyecto es un M5 Pro con 48 GB.
- El Python del entorno offline trae SQLite 3.50 con FTS5 y carga de extensiones.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **Quien desarrolla:** ejecuta las evaluaciones y decide los parámetros.
- **El usuario del proyecto:** aporta el corpus y revisa preguntas y resultados.
- **Las features siguientes:** F06/F07 (troceado e indexación), F10 (recuperación) y F11 (contexto y prompt) heredan los parámetros decididos aquí.

### User Story 1 - Índice de evaluación fiel a la app (Priority: P1)

A partir de un corpus de documentos en español, el pipeline de escritorio:
- extrae el texto;
- lo trocea con el mismo algoritmo que usará la app;
- lo indexa con el mismo esquema, normalizador, prefijos, cabecera contextual y truncado que el móvil.

Guarda además los vectores completos para poder probar otras dimensiones sin volver a embeber. Repetir la construcción con el mismo corpus y la misma configuración da exactamente el mismo índice.

**Why this priority**: si el índice de escritorio no representa lo que hará el móvil, las medidas no sirven para decidir (constitución III).

**Independent Test**:
1. Construir el índice con el corpus.
2. Comprobar que sus metadatos coinciden con los de la app y que los golden de F04 siguen pasando.
3. Reconstruirlo y comprobar que sale idéntico.

**Acceptance Scenarios**:

1. **Given** un corpus en una carpeta local, **When** se construye el índice de evaluación, **Then** cada documento con texto queda troceado, normalizado, embebido e indexado. Se informa de cuántos documentos y fragmentos hay, y de qué documentos se han descartado y por qué.
2. **Given** el índice construido, **When** se comparan sus metadatos del espacio vectorial con la configuración de la app, **Then** coinciden: modelo, dimensiones, prefijos y versión del normalizador.
3. **Given** el mismo corpus y la misma configuración, **When** se reconstruye, **Then** los fragmentos, sus identificadores `(huella del documento, ordinal)` y sus vectores son idénticos.
4. **Given** un tamaño de fragmento distinto (250, 350 o 500 tokens), **When** se construye el índice, **Then** se obtiene un índice independiente para esa variante, sin mezclarse con los demás.

---

### User Story 2 - Conjunto de prueba con relevantes trazables (Priority: P1)

Existe un conjunto de prueba sobre el corpus:
- unas 300 preguntas con respuesta;
- unas 50 sin respuesta en el corpus;
- unos 40 pares de seguimiento («¿y para empresas?»).

Cada pregunta con respuesta tiene su respuesta esperada y su evidencia, localizada en el documento. Al menos 100 están revisadas a mano.

**Why this priority**: sin un conjunto de prueba fiable no se puede medir nada; es el bloqueo principal (G37).

**Independent Test**:
1. Validar el conjunto contra el índice.
2. Cada evidencia se encuentra en su documento.
3. Los recuentos por tipo cumplen los mínimos.
4. La revisión manual está registrada.

**Acceptance Scenarios**:

1. **Given** el conjunto de prueba, **When** se valida, **Then** cada pregunta con respuesta tiene al menos un fragmento relevante identificado por `(huella del documento, ordinal)` en la configuración evaluada, y su evidencia aparece literalmente en el documento.
2. **Given** un tamaño de fragmento distinto, **When** se recalculan los relevantes, **Then** se derivan de la evidencia: son relevantes los fragmentos que la contienen. No hace falta volver a etiquetar.
3. **Given** las preguntas sin respuesta, **When** se validan, **Then** ninguna tiene relevantes y todas están marcadas como tales.
4. **Given** los pares de seguimiento, **When** se validan, **Then** cada uno referencia su pregunta anterior, que existe en el conjunto.
5. **Given** la revisión manual, **When** se consulta el conjunto, **Then** se sabe qué preguntas se han revisado (al menos 100) y cuáles se han corregido o descartado.

---

### User Story 3 - Métricas y ablaciones de recuperación (Priority: P1)

Para la configuración de referencia y cada variante de las ablaciones se miden:
- Recall@20 y MRR de la recuperación vectorial, la léxica y la híbrida;
- nDCG@10 por dimensión;
- Recall@6 tras la selección de contexto.

Todo con el mismo procedimiento que usará la app: RRF, regla de seguimiento, límite por documento, vecinos y presupuesto.

**Why this priority**: es lo que fija dimensión, normalización, cabecera y tamaño de fragmento antes de que F06/F07 indexen en el móvil.

**Independent Test**: ejecutar la evaluación de recuperación y obtener una tabla con todas las métricas por variante, comparadas con los umbrales del informe.

**Acceptance Scenarios**:

1. **Given** el índice y el conjunto, **When** se evalúa la recuperación, **Then** se informan Recall@20 y MRR de vector, FTS e híbrido, y Recall@6 tras la selección, frente a los umbrales del informe (Recall@20 híbrido ≥ 0,90; Recall@6 ≥ 0,80; MRR híbrido ≥ vector y ≥ FTS).
2. **Given** las dimensiones 128, 256, 512 y 768, **When** se evalúan, **Then** se informa de nDCG@10 de cada una y de la menor que no pierde más de un 1 % frente a 768.
3. **Given** las variantes de normalización léxica (sin normalizar, solo palabras vacías, palabras vacías y stemmer), **When** se evalúan, **Then** se informa de su efecto en la búsqueda léxica y en la híbrida.
4. **Given** las variantes con y sin cabecera contextual y los tamaños de 250, 350 y 500 tokens, **When** se evalúan, **Then** se informa de cada una en las mismas métricas.
5. **Given** los pares de seguimiento, **When** se evalúan con y sin la regla de seguimiento, **Then** se informa de la diferencia de Recall@20 en ese subconjunto.

---

### User Story 4 - Calidad de respuesta de extremo a extremo (Priority: P2)

Con la recuperación elegida, el escritorio genera las respuestas con el mismo modelo y la misma plantilla de prompt que usará el móvil. Se mide:
- si responde bien;
- si se niega cuando no hay información;
- si sus citas apuntan a fuentes que contienen lo afirmado.

Se compara 4, 6 y 8 bloques, con y sin vecinos, dentro del presupuesto de primer token, y Gemma 4 E2B frente a E4B.

**Why this priority**: fija el número de bloques (constitución IV) y comprueba la promesa de citas reales y negativa explícita (constitución III). Depende de US1–US3.

**Independent Test**: generar las respuestas del conjunto con la configuración elegida y obtener los porcentajes de respuestas correctas, negativas correctas y citas correctas.

**Acceptance Scenarios**:

1. **Given** la configuración elegida, **When** se generan las respuestas, **Then** se informa del porcentaje de respuestas correctas sobre las preguntas con respuesta (umbral ≥ 80 %), de negativas correctas sobre las preguntas sin respuesta (≥ 85 %) y de citas correctas (≥ 90 %).
2. **Given** 4, 6 y 8 bloques, con y sin vecinos, **When** se construyen los prompts, **Then** se informa del tamaño real del prompt en tokens (mediana y p95) y de si cabe en ≈ 2.000 tokens (constitución IV). Una variante que no cabe se descarta para el móvil aunque responda mejor.
3. **Given** una respuesta que no sigue el formato (sin citas, o con citas a fuentes inexistentes), **When** se evalúa, **Then** cuenta como cita incorrecta y queda en el informe.
4. **Given** E2B y E4B, **When** se comparan en la misma configuración, **Then** se informa de la diferencia en los tres porcentajes y del tiempo de generación en el escritorio.

---

### User Story 5 - Decisiones registradas y propagadas a la app (Priority: P2)

Con las cifras, se elige la configuración y se registra en DECISIONS: dimensión, tamaño de fragmento, cabecera, normalización, bloques, vecinos y modelo. Esos valores pasan a las constantes compartidas de la app, que hasta ahora eran provisionales. Si cambia la dimensión, cambian también los metadatos del índice y el golden de embeddings.

**Why this priority**: la constitución III exige que los parámetros solo cambien con una evaluación documentada y una entrada en DECISIONS.

**Independent Test**:
1. Comprobar que cada parámetro elegido tiene su entrada en DECISIONS, con las cifras que lo justifican.
2. Comprobar que las constantes de la app coinciden.
3. Comprobar que las pruebas de la app siguen en verde.

**Acceptance Scenarios**:

1. **Given** el informe de evaluación, **When** se toman las decisiones, **Then** cada parámetro tiene su valor, la cifra que lo justifica y las alternativas descartadas en DECISIONS.
2. **Given** los parámetros decididos, **When** se actualizan las constantes de la app, **Then** la app y el pipeline offline usan los mismos valores y sus pruebas pasan.
3. **Given** una dimensión distinta de 256, **When** se propaga, **Then** se actualizan los metadatos esperados del índice y el golden de embeddings, y la prueba de compatibilidad vuelve a pasar en el teléfono.

---

### Edge Cases

- **Documento sin texto extraíble** (PDF escaneado): se descarta con el motivo en el informe de construcción. El OCR es del backlog.
- **Documento duplicado** (misma huella de contenido): se indexa una sola vez.
- **Fragmento cuya entrada de embedding supera el máximo del modelo** (fragmento grande más prefijo y cabecera): se cuenta y se informa por variante. Una variante con fragmentos truncados no puede elegirse sin decirlo.
- **Evidencia que queda partida entre dos fragmentos al cambiar el tamaño:** son relevantes los fragmentos que la contienen total o parcialmente; la regla se aplica igual a todas las variantes.
- **Respuesta del modelo con la negativa ligeramente distinta** (mayúsculas, puntuación, espacios): cuenta como negativa si coincide con la frase fijada tras normalizar esas diferencias. Una negativa parcial seguida de una respuesta cuenta como respuesta.
- **Pregunta de seguimiento cuya pregunta anterior no tiene respuesta:** se mantiene; mide si la regla de seguimiento empeora el caso.
- **Corpus pequeño** (menos fragmentos de los necesarios para 300 preguntas): se generan menos preguntas, se informa de los recuentos reales y los mínimos se ajustan con el usuario.
- **Evaluación interrumpida** (cierre o error a mitad de la generación): se puede reanudar sin repetir las preguntas ya respondidas con la misma configuración.

## Requirements *(mandatory)*

### Functional Requirements

**Índice de evaluación**

- **FR-001**: El pipeline MUST construir el índice de evaluación a partir de una carpeta de documentos, con:
  - el esquema compartido de la app;
  - el normalizador idéntico al de la app (golden de F04);
  - la misma construcción de entradas de embedding (prefijos y cabecera contextual) y el mismo truncado con L2;
  - el mismo fichero de modelo de embeddings, verificado por su huella.
- **FR-002**: El troceado MUST seguir el algoritmo del informe:
  - por párrafos, sin cortar frases;
  - tamaño objetivo y máximo en caracteres derivados del tamaño en tokens de la variante;
  - solape del último párrafo;
  - un cambio de encabezado abre fragmento nuevo;
  - ordinal consecutivo.

  F06 lo implementará en la app con los parámetros que se decidan aquí.
- **FR-003**: El índice MUST guardar, además de los vectores truncados a la dimensión de la variante, los vectores completos, para evaluar otras dimensiones sin volver a embeber.
- **FR-004**: La construcción MUST ser reproducible: el mismo corpus y la misma configuración dan los mismos fragmentos, identificadores y vectores. El entorno MUST tener las versiones fijadas.
- **FR-005**: La construcción MUST informar de los documentos indexados y descartados (con el motivo), el número de fragmentos, la distribución de tamaños y los fragmentos que superan la entrada máxima del modelo.

**Conjunto de prueba**

- **FR-006**: El conjunto MUST contener al menos 300 preguntas con respuesta, 50 sin respuesta y 40 de seguimiento, con al menos 100 revisadas a mano, salvo que el tamaño del corpus obligue a ajustarlo con el usuario.
- **FR-007**: Cada pregunta con respuesta MUST tener:
  - respuesta esperada;
  - evidencia: el texto literal del documento que la sostiene, con la huella del documento;
  - fragmentos relevantes `(huella del documento, ordinal)` en la configuración de referencia.

  En otras variantes de troceado, los relevantes se derivan de la evidencia.
- **FR-008**: Las preguntas se distribuyen entre documentos y tipos de contenido (texto, listas, tablas o datos numéricos, procedimientos) y no copian frases literales del texto.
- **FR-009**: El conjunto MUST validarse automáticamente:
  - las evidencias aparecen en sus documentos;
  - los seguimientos apuntan a preguntas existentes;
  - las preguntas sin respuesta no tienen relevantes;
  - los recuentos por tipo cumplen el mínimo.

**Recuperación y métricas**

- **FR-010**: La recuperación MUST reproducir la prevista para la app:
  - vectorial exacta con 50 candidatos;
  - léxica con BM25 (peso 3 para título y ruta, 1 para el cuerpo), con 50 candidatos;
  - fusión RRF con k = 60;
  - regla de seguimiento sin LLM: pregunta corta o que empieza por un conector, más la pregunta anterior;
  - selección de contexto del informe: como mucho 3 por documento, vecino siguiente para los 2 mejores, fusión de consecutivos, presupuesto de tokens y orden por documento y ordinal.
- **FR-011**: MUST calcularse Recall@20 y MRR (vector, FTS e híbrido), nDCG@10 y Recall@6 tras la selección, y compararse con los umbrales del informe.
- **FR-012**: MUST ejecutarse las ablaciones:
  - modo de recuperación: solo vector, solo FTS, híbrido;
  - normalización léxica: sin normalizar, solo palabras vacías, palabras vacías y stemmer;
  - dimensión: 128, 256, 512 y 768;
  - cabecera contextual: con y sin;
  - tamaño de fragmento: 250, 350 y 500 tokens;
  - regla de seguimiento: con y sin;
  - contexto final: 4, 6 y 8 bloques, con y sin vecinos.

  Cada variante cambia un solo parámetro respecto a la referencia.

**Respuesta de extremo a extremo**

- **FR-013**: La generación MUST usar el mismo fichero de Gemma 4 E2B que la app (D-023) y la plantilla de prompt del informe: fuentes numeradas, citas `[n]` y la negativa exacta «No encuentro esa información en los documentos disponibles.».
- **FR-014**: MUST medirse:
  - el porcentaje de respuestas correctas sobre las preguntas con respuesta;
  - el de negativas correctas sobre las preguntas sin respuesta;
  - el de citas correctas: la fuente citada contiene lo afirmado;
  - el tamaño real del prompt en tokens (mediana y p95) por variante de bloques.
- **FR-015**: MUST compararse Gemma 4 E2B con E4B en la configuración elegida, con el mismo conjunto y la misma plantilla. Se informa de la calidad y del tiempo de generación en el escritorio.
- **FR-016**: La generación MUST poder reanudarse tras una interrupción sin repetir las respuestas ya obtenidas con la misma configuración, y guardar cada respuesta con su prompt, sus bloques y su juicio.

**Informe y decisiones**

- **FR-017**: MUST producirse un informe de evaluación con:
  - el corpus (recuentos, sin contenido privado si el corpus lo es);
  - el conjunto de prueba;
  - la configuración de referencia;
  - todas las ablaciones con sus métricas frente a los umbrales;
  - los tiempos de ejecución;
  - la configuración elegida con su justificación.
- **FR-018**: Las decisiones MUST registrarse en DECISIONS (constitución III) y propagarse a las constantes compartidas de la app y del pipeline offline. Si cambia la dimensión, MUST actualizarse también los metadatos esperados del índice y el golden de embeddings, y volver a validarse en el teléfono.
- **FR-019**: Una ejecución MUST quedar identificada por su configuración (parámetros, huellas de modelos, versión del normalizador y del conjunto de prueba), para que dos resultados sean comparables o se sepa por qué no lo son.

### Key Entities *(include if feature involves data)*

- **Documento del corpus:** nombre, tipo, huella del contenido, texto extraído con encabezados y páginas, y estado (indexado o descartado con motivo).
- **Fragmento de evaluación:** documento, ordinal, ruta de encabezados, página, texto, estimación de tokens, vector completo y vector truncado.
- **Pregunta de prueba:** identificador y tipo (con respuesta, sin respuesta o seguimiento). Además:
  - texto y pregunta anterior, si es un seguimiento;
  - respuesta esperada y evidencia (huella del documento y texto literal);
  - relevantes en la configuración de referencia;
  - estado de revisión: revisada, corregida o descartada.
- **Configuración de evaluación:** los parámetros de la variante (dimensión, tamaño de fragmento, cabecera, normalización, modo de recuperación, regla de seguimiento, bloques, vecinos y modelo), más las huellas y versiones.
- **Resultado por pregunta:**
  - candidatos y posiciones de cada lista, y bloques seleccionados;
  - prompt y sus tokens;
  - respuesta, citas y juicio.
- **Informe de evaluación:** las métricas agregadas por configuración y las decisiones derivadas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Reconstruir el índice con el mismo corpus y la misma configuración da un resultado idéntico en el 100 % de los fragmentos e identificadores, y las métricas de recuperación se repiten exactamente.
- **SC-002**: El índice de evaluación describe el mismo espacio vectorial que la app: sus metadatos coinciden con los que la app espera y los golden de F04 siguen pasando.
- **SC-003**: El conjunto de prueba tiene al menos 300 preguntas con respuesta, 50 sin respuesta y 40 de seguimiento, con al menos 100 revisadas a mano. El 100 % de las evidencias se encuentra en su documento.
- **SC-004**: El informe recoge las métricas de la referencia y de cada valor de cada ablación (FR-012), comparadas con los umbrales del informe:
  - Recall@20 híbrido ≥ 0,90;
  - Recall@6 ≥ 0,80;
  - MRR híbrido ≥ vector y ≥ FTS;
  - respuestas correctas ≥ 80 %;
  - negativa correcta ≥ 85 %;
  - citas correctas ≥ 90 %.
- **SC-005**: La configuración elegida tiene un prompt de ≈ 2.000 tokens o menos en el p95, el presupuesto de primer token de la constitución IV.
- **SC-006**: Una iteración de evaluación de recuperación (una variante, con los índices ya construidos) termina en menos de 10 minutos en el Mac del proyecto, para que ajustar sea cuestión de minutos, como plantea el informe.
- **SC-007**: Cada parámetro elegido tiene una entrada en DECISIONS con su cifra justificativa, y las constantes de la app coinciden con ella con las pruebas de la app en verde.

## Assumptions

- **Corpus:** documentos en español representativos del uso previsto, en los formatos del MVP (PDF con texto, TXT y Markdown). Su origen, si se versiona y quién lo aporta se decide en el clarify (G37).
- **Generación de preguntas y juicio:** el informe habla de «un modelo grande» y de revisión manual. Qué modelo, si se usan servicios externos con los documentos y cómo se juzgan respuestas y citas se decide en el clarify. La constitución I se refiere a la app; el pipeline de escritorio no forma parte del producto, pero la privacidad del corpus se respeta igual.
- **Extracción de texto:** se usa la mejor disponible en el escritorio para los formatos del MVP. El texto que obtenga la app en F06 puede diferir; F06 lo compara.
- **Tokens:** los tamaños en tokens se traducen a caracteres con la relación del informe (≈ 4 caracteres por token en español). El tamaño real de los prompts se mide con el tokenizador del propio modelo cuando sea posible.
- **Generación en el escritorio:** usa los mismos ficheros de modelo y la misma plantilla que el móvil. Las diferencias numéricas entre el escritorio y el teléfono no afectan a la calidad de forma apreciable (F04 midió un coseno ≥ 0,99 en embeddings). La latencia no se mide aquí, sino en F14.
- **Configuración de referencia:** la del informe y F04, es decir 256 dimensiones, fragmentos de unos 350 tokens, cabecera contextual, normalizador v1, híbrido con RRF, regla de seguimiento y 6 bloques con vecinos. Las ablaciones cambian un parámetro cada vez respecto a ella.
- **Índice de evaluación:** solo existe en el escritorio y no se distribuye (D-002). Los documentos llevan `source = 'user'`, como en la app.
- **Fuera de alcance:**
  - la ingesta y la recuperación de producto (F06, F07, F10 y F11);
  - las medidas de latencia en el teléfono (F14);
  - la cuantización INT8 (F14);
  - la variante de EmbeddingGemma (D-025);
  - FTS con trigramas, salvo que la evaluación lo justifique;
  - el OCR de escaneados (backlog).

# Especificación de feature: Recuperación híbrida (F10)

**Feature Branch**: `010-hybrid-retrieval`

**Created**: 2026-10-29

**Status**: Draft

**Input**: Descripción del usuario: «F10 — Recuperación híbrida. Feature técnica: sin UI de producto, con diagnóstico de búsqueda interno solo en debug. Dada una pregunta, y la anterior si la hay, devolver los bloques de contexto que F11 pondrá en el prompt, con la recuperación evaluada en F05 (D-034) sobre el índice real de F04/F07.» Ver `docs/roadmap/FEATURES.md` (F10).

**Fuentes**:
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - tabla de configuración base (L60–79);
  - Motor vectorial: latencia y criterio INT8 (L111–143);
  - Búsqueda léxica en español (L268–334);
  - Capa de datos en Kotlin (L421–593);
  - Recuperación y selección de contexto: Flujo, Preguntas de seguimiento y Selección de contexto (L595–727);
  - Evaluación: conjunto de prueba, umbrales y criterio de la fase 2 (L1018–1029, L1127).
- Constitución v1.1.1 (enmendada en el clarify de F10):
  - I (privacidad);
  - III (RAG: L95 contexto, L101 cambio de parámetros);
  - IV (rendimiento: L113–114).
- DECISIONS:
  - D-024: embedding de consulta en CPU;
  - D-026: primer token ≤ 5 s;
  - D-030: INT8 aplazado a F14; el diagnóstico de búsqueda llega en F10;
  - D-034: parámetros de recuperación y contexto;
  - D-035: revisión del orden dentro del presupuesto;
  - D-040: búsquedas filtradas con top-(*k* + *m*);
  - D-042: búsqueda durante la indexación;
  - D-044: los documentos sombra cuentan como fragmentos ocultos.
- INVENTARIO G25 (FTS solo en español) y G28 (exclusiones; resuelto en F07, F10 lo hereda).
- Referencia de escritorio (F05):
  - `offline/eval/retrieval.py` (regla de seguimiento, RRF, recuperación);
  - `offline/eval/selector.py` (selección de contexto);
  - `offline/eval/pipeline.py` (flujo y métricas);
  - sus pruebas en `offline/tests/test_retrieval.py` y `test_selector.py`;
  - el conjunto de preguntas `offline/testset/questions.jsonl`;
  - el informe de evaluación `offline/reports/eval-report.md`.
- Lo ya construido:
  - F03: modelos verificados y preparados;
  - F04: búsqueda vectorial exacta, búsqueda léxica, carga de fragmentos y de vecinos, y validación de los metadatos del índice;
  - F07: búsquedas filtradas a documentos indexados e incluidos, y sesiones del motor de embeddings durante la tanda.

**Verificado al redactar** (2026-10-29):
- `RagParameters` (app) y `offline/config/rag_parameters.json` (escritorio) coinciden con D-034:
  - 50 candidatos por lista y RRF con k = 60;
  - regla de seguimiento activa;
  - 4 bloques, como mucho 3 por documento;
  - presupuesto de 1.719 tokens estimados;
  - sin vecinos.
- La ficha de F10 en FEATURES y la constitución (L95) conservaban los valores del informe: 6 bloques, ≈ 2.200 tokens y vecinos de los dos primeros. La constitución (L101) permite cambiarlos con una evaluación documentada y una entrada en DECISIONS, que es D-034. En clarify se confirmó D-034 (ver Clarifications).
- Desktop, configuración de D-034 sobre 370 preguntas:
  - Recall@bloques 0,889;
  - Recall@20 híbrido 0,984;
  - MRR 0,810.
- Teléfono de referencia, 10k fragmentos sintéticos:
  - F04: búsqueda vectorial exacta p95 23,1 ms y léxica p95 26,5 ms;
  - F07: con 4.300 fragmentos ocultos, la vectorial sube a ≈ 45 ms (D-042).

## Clarifications

### Session 2026-10-29

- Q: ¿Con qué reglas se elige el contexto: las de la evaluación de F05 (D-034) o las del informe original (6 bloques, ≈ 2.200 tokens, vecinos de los dos primeros)? → A: las de D-034: 4 bloques, sin vecinos, como mucho 3 por documento y 1.719 tokens. Se corrige la ficha de F10 (objetivo y CA5) y se enmienda la constitución L95 (PATCH 1.1.0 → 1.1.1) para que remita a D-034.
- Q: ¿Hasta dónde llega F10 en la revisión del orden de los candidatos que pidió D-035? → A: se evalúa en el harness de escritorio:
  - variantes: trigramas como tercera lista de la fusión y una reordenación de los primeros candidatos;
  - regla de adopción: subir el Recall@bloques al menos 2 puntos sobre las 370 preguntas sin pasar el presupuesto del prompt;
  - si una variante la cumple, va a DECISIONS y F10 la lleva al móvil; si no, se queda D-034 y se anota.
- Q: ¿Cómo se comprueba que la búsqueda del móvil encuentra lo mismo que la del escritorio? → A: con una prueba instrumentada en el teléfono de referencia, con los modelos reales, que se lanza bajo demanda:
  - indexa los 21 documentos del corpus de evaluación del repositorio en un índice temporal, nunca en la biblioteca del usuario;
  - usa los títulos del corpus;
  - un bloque acierta si contiene la evidencia literal de la pregunta;
  - usa una muestra fija de 50 preguntas, respondibles y de seguimiento, con la cifra de escritorio sobre esas mismas 50;
  - las preguntas cuya evidencia no se extrae en el móvil se cuentan aparte.
- Q: ¿Qué tiempo máximo se pide a una recuperación completa en el teléfono, con 10.000 fragmentos (p95)? → A: ≤ 200 ms con el motor de consulta cargado, sumando embedding, búsquedas, fusión y selección; durante una tanda de indexación, ≤ 1 s.

## User Scenarios & Testing *(mandatory)*

Los actores son:
- **el chat (F11):** pide el contexto de cada pregunta y cita sus bloques;
- **quien desarrolla:** usa el diagnóstico de búsqueda en la app de depuración y mide la calidad y la latencia en el teléfono.

El usuario final no ve nada nuevo en esta feature.

### User Story 1 - Contexto para una pregunta (Priority: P1)

Dada una pregunta, la recuperación devuelve los bloques de contexto que el chat pondrá en el prompt. Para elegirlos:
- busca por significado y por palabras;
- combina las dos listas;
- elige los mejores fragmentos dentro del límite por documento y del presupuesto de tokens;
- los entrega ordenados como se leen en el documento.

Cada bloque dice de qué documento sale, qué fragmentos cubre, su ruta de encabezados, su página y su texto, para que el chat pueda citarlo.

**Why this priority**: es el núcleo del RAG. Sin bloques no hay respuesta con fuentes (constitución III), y F11 depende de ello.

**Independent Test**:
1. Indexar documentos de prueba en un índice temporal.
2. Hacer una pregunta cuya respuesta está en un fragmento conocido. Ese fragmento aparece en un bloque.
3. Los bloques respetan el máximo por documento y el presupuesto, y van ordenados por documento y posición.
4. Cada bloque se puede trazar a fragmentos reales del índice.

**Acceptance Scenarios**:

1. **Given** un índice con documentos indexados e incluidos, **When** se recupera el contexto de una pregunta, **Then** se buscan los 50 mejores fragmentos por significado y los 50 mejores por palabras, se combinan con la fusión por rangos (RRF, k = 60) y los 30 primeros de la fusión pasan a la selección.
2. **Given** los candidatos fusionados, **When** se seleccionan, **Then**:
   - se recorren en el orden de la fusión;
   - se acepta un fragmento solo si su documento no tiene ya el máximo y si cabe en el presupuesto;
   - un fragmento que no cabe no detiene el recorrido.
3. **Given** dos fragmentos consecutivos del mismo documento entre los elegidos, **When** se forman los bloques, **Then** se unen en un solo bloque sin repetir el párrafo que el troceado arrastra de uno al siguiente.
4. **Given** los bloques elegidos, **When** se devuelven, **Then** van ordenados por documento y por posición dentro del documento, no por puntuación, y no superan el máximo de bloques.
5. **Given** dos candidatos con la misma puntuación de fusión, **When** se ordenan, **Then** va primero el de mejor rango individual y, si persiste el empate, el de identificador menor, como en el escritorio.
6. **Given** una pregunta formada solo por palabras vacías («de la que»), **When** se recupera, **Then** la búsqueda por palabras no da resultados, la búsqueda por significado sí, y no hay error.
7. **Given** un índice sin documentos buscables, **When** se recupera, **Then** el resultado es «sin resultados», sin error.

---

### User Story 2 - Preguntas de seguimiento (Priority: P1)

Una pregunta corta o que empieza por un conector («¿Y para empresas?») no recupera nada útil por sí sola. Si quien llama aporta la pregunta anterior, la búsqueda usa las dos juntas, sin pedir nada al LLM.

**Why this priority**: con la regla, el Recall@20 de los seguimientos pasa de 0,79 a 0,98 en el escritorio (D-034). El chat la necesita desde su primera versión.

**Independent Test**: con la pregunta anterior «¿Cada cuánto se revisa la bomba?» y la actual «¿Y el aceite?», el texto de búsqueda es «¿Cada cuánto se revisa la bomba? ¿Y el aceite?» para las dos búsquedas. Una pregunta larga sin conector se busca sola.

**Acceptance Scenarios**:

1. **Given** una pregunta con menos de 8 términos normalizados (sin palabras vacías ni términos de una letra), **When** hay pregunta anterior, **Then** es un seguimiento.
2. **Given** una pregunta que empieza por «y », «¿y », «pero », «entonces», «¿y si» o «y si » (sin distinguir mayúsculas), **When** hay pregunta anterior, **Then** es un seguimiento aunque sea larga.
3. **Given** un seguimiento, **When** se busca, **Then** el texto de búsqueda es la pregunta anterior, un espacio y la pregunta actual, tanto para el significado como para las palabras.
4. **Given** una pregunta de 8 términos o más sin conector inicial, **When** se busca, **Then** el texto de búsqueda es solo la pregunta actual.
5. **Given** un seguimiento sin pregunta anterior, **When** se busca, **Then** se busca solo la pregunta actual.

---

### User Story 3 - Nunca contenido que no se debe usar (Priority: P1)

La recuperación solo devuelve fragmentos de documentos indexados por completo e incluidos en las consultas. Nunca devuelve:
- documentos excluidos por el usuario;
- documentos a medias, con error, en pausa o fuera del índice;
- copias en curso de un reindexado (documentos sombra).

Tampoco busca si el índice no es compatible con el modelo instalado o si el modelo de embeddings no está listo.

**Why this priority**: las exclusiones son una promesa al usuario (F08) y la trazabilidad de las citas es no negociable (constitución III). Un índice incompatible daría resultados sin sentido.

**Independent Test**:
1. En un índice temporal con documentos en cada estado, recuperar con preguntas que coinciden con todos ellos. Solo aparecen los indexados e incluidos.
2. Excluir uno y repetir: desaparece.
3. Con los metadatos del índice alterados, la recuperación responde «índice no disponible» sin buscar.
4. Sin el modelo de embeddings verificado y preparado, responde «modelos no listos» sin cargar ningún motor.

**Acceptance Scenarios**:

1. **Given** documentos indexados e incluidos junto a otros excluidos, pendientes, con error, en pausa, fuera del índice o en reindexado, **When** se recupera con una pregunta que coincide con todos, **Then** ningún bloque contiene fragmentos de los segundos.
2. **Given** muchos fragmentos ocultos (por ejemplo, 4.300 de un documento a medias), **When** se recupera, **Then** cada búsqueda sigue dando hasta 50 candidatos buscables.
3. **Given** un documento excluido o borrado entre la búsqueda y la carga de los bloques, **When** se forman los bloques, **Then** sus fragmentos no aparecen.
4. **Given** un índice incompatible o ilegible, **When** se recupera, **Then** el resultado es «índice no disponible», con el motivo, y no se carga el modelo ni se busca.
5. **Given** el modelo de embeddings ausente, alterado o sin preparar, **When** se recupera, **Then** el resultado es «modelos no listos» y no se busca.
6. **Given** un fallo del motor de embeddings al calcular la consulta, **When** se recupera, **Then** el resultado es «fallo del embedding», sin bloques y sin cerrar la app.

---

### User Story 4 - Diagnóstico de búsqueda en depuración (Priority: P2)

En la app de depuración, una herramienta permite escribir una pregunta, y opcionalmente la anterior, y ver qué hace la recuperación sobre la biblioteca real del teléfono:
- si se aplicó la regla de seguimiento y con qué texto se buscó;
- los bloques elegidos, con sus puntuaciones;
- las latencias de cada paso.

**Why this priority**: es la herramienta para entender por qué el chat responde lo que responde y para medir en el teléfono (FEATURES F10, CA3). No afecta al usuario final.

**Independent Test**: en la app de depuración del teléfono, con documentos indexados, abrir el diagnóstico, escribir una pregunta y ver los bloques con sus puntuaciones y las latencias de embedding, búsqueda vectorial, búsqueda léxica, selección y total. La build de release no contiene la herramienta.

**Acceptance Scenarios**:

1. **Given** la app de depuración con documentos indexados, **When** se busca una pregunta, **Then** se ven:
   - el texto de búsqueda y si fue un seguimiento;
   - cada bloque con su documento, ruta de encabezados, página, fragmentos que cubre y tokens;
   - por cada fragmento elegido, su puntuación de fusión, su rango y distancia en la búsqueda vectorial y su rango en la léxica, o que no apareció en esa lista;
   - las latencias de embedding, búsqueda vectorial, búsqueda léxica, selección y total.
2. **Given** un estado sin búsqueda (índice no disponible, modelos no listos, pregunta vacía), **When** se busca, **Then** el diagnóstico muestra ese estado.
3. **Given** la build de release, **When** se inspecciona, **Then** no incluye el diagnóstico ni su acceso.

---

### User Story 5 - Calidad y latencia medidas en el teléfono (Priority: P2)

Quien desarrolla mide en el teléfono de referencia:
- la latencia de cada paso con 10.000 fragmentos, con y sin fragmentos ocultos;
- la calidad de la recuperación frente al escritorio, sobre 50 preguntas del conjunto de evaluación.

**Why this priority**: confirma que lo evaluado en el escritorio se mantiene en el móvil (informe, criterio de la fase 2) y decide si F14 necesita INT8 (constitución IV).

**Independent Test**:
1. Ejecutar la prueba de latencia en el teléfono: da p50 y p95 de embedding, búsqueda vectorial, búsqueda léxica y total.
2. Ejecutar la prueba de calidad: da el Recall@bloques del teléfono y el del escritorio sobre las mismas 50 preguntas.

**Acceptance Scenarios**:

1. **Given** 10.000 fragmentos en un índice temporal del teléfono de referencia, **When** se mide, **Then** se informan p50 y p95 de cada paso y del total, con el motor de consulta ya cargado y también la primera búsqueda con el motor sin cargar.
2. **Given** el p95 de la búsqueda vectorial por encima de 20 ms, **When** se cierra la feature, **Then** queda registrada en DECISIONS la decisión sobre INT8 para F14.
3. **Given** las 50 preguntas de la muestra, **When** se recuperan en el teléfono y en el escritorio, **Then** el Recall@bloques del teléfono queda a ±5 puntos del de escritorio.

---

### User Story 6 - Revisión del orden dentro del presupuesto (Priority: P3)

En el escritorio, 36 de las 80 respuestas no correctas no tenían el fragmento relevante en el contexto, aunque estaba entre los 20 primeros candidatos (D-035). D-035 encarga a F10 revisar el orden de los primeros puestos: los trigramas como tercera lista de la fusión y una reordenación.

**Why this priority**: es la palanca de calidad con más margen, pero la recuperación de D-034 ya cumple el umbral del informe (Recall@bloques ≥ 0,80). Por eso se evalúa en el escritorio y solo llega al móvil si mejora (ver Clarifications).

**Independent Test**: el informe de evaluación del escritorio compara D-034 con cada variante en Recall@bloques sobre las 370 preguntas. La decisión de adoptarla o no queda registrada.

**Acceptance Scenarios**:

1. **Given** el harness de escritorio, **When** se evalúan las variantes, **Then** cada una tiene su Recall@bloques, su Recall@20 y su MRR junto a los de D-034, con la misma muestra y el mismo presupuesto.
2. **Given** una variante que cumple la regla de adopción, **When** se adopta, **Then** se registra en DECISIONS, se lleva a los parámetros del escritorio y de la app, y la app la implementa. Si ninguna la cumple, se mantiene D-034 y se anota.

---

### Edge Cases

- **Pregunta vacía o solo espacios:** «pregunta vacía», sin cargar el motor ni buscar.
- **Pregunta solo con palabras vacías:** sin resultados léxicos; la búsqueda por significado decide.
- **Texto de búsqueda más largo que la entrada del modelo de embeddings** (pregunta muy larga o seguimiento de una anterior larga): se recorta para que quepa, conservando la pregunta actual y recortando antes la anterior. La búsqueda léxica usa como mucho sus 16 primeros términos (F04).
- **Seguimiento de un seguimiento:** se usa solo la pregunta anterior que aporta quien llama, sin encadenar más.
- **Todos los candidatos del mismo documento:** como mucho 3 fragmentos; puede haber menos bloques que el máximo.
- **Un fragmento que no cabe en el presupuesto restante:** se salta y se sigue con el siguiente candidato.
- **Muchos fragmentos ocultos** (documento grande a medias, exclusiones, sombras): las búsquedas piden más candidatos para seguir dando hasta 50 buscables (D-040). La latencia crece y se mide (D-042).
- **Búsqueda durante una tanda de indexación:** no espera a que termine la tanda. Si la tanda usa el mismo motor de embeddings, la consulta se intercala entre fragmentos y espera como mucho al fragmento en curso.
- **Presión de memoria o app en segundo plano:** el motor de consulta se libera. La siguiente búsqueda lo vuelve a cargar y tarda más; esa primera búsqueda se mide.
- **Dos búsquedas seguidas o simultáneas:** cada resultado corresponde a su pregunta y ninguna se pierde.
- **Documento borrado o excluido durante la búsqueda:** sus fragmentos no llegan a los bloques.
- **Reindexado publicado durante la búsqueda:** los fragmentos que se cargan son los publicados, nunca los de la sombra.
- **Pregunta en otro idioma:** la búsqueda léxica es solo española (G25). La búsqueda por significado sí funciona, y la fusión usa lo que haya.
- **Índice que pasa a incompatible tras la última apertura:** «índice no disponible».

## Requirements *(mandatory)*

### Functional Requirements

**Recuperación**

- **FR-001**: La recuperación MUST calcular el vector de la consulta en CPU con el prefijo de consulta del índice, truncado y normalizado a la dimensión del índice (D-024, F02).
- **FR-002**: MUST hacer una búsqueda vectorial exacta y una búsqueda léxica (título con peso 3 y cuerpo con peso 1, normalizador español v1), de hasta 50 candidatos cada una. Las dos solo devuelven fragmentos de documentos indexados e incluidos (D-040).
- **FR-003**: MUST fusionar las dos listas con RRF, k = 60, con rangos que empiezan en 1. Un candidato que solo aparece en una lista puntúa solo por esa lista.
- **FR-004**: Los empates de la fusión MUST resolverse por el mejor rango individual y después por el identificador menor, como el escritorio.
- **FR-005**: Los 30 primeros candidatos de la fusión MUST pasar a la selección.
- **FR-006**: Los valores numéricos (candidatos, k, bloques, máximo por documento, presupuesto, vecinos) MUST salir de los parámetros compartidos con el escritorio, nunca de constantes sueltas.

**Seguimiento**

- **FR-007**: Una pregunta MUST considerarse seguimiento si tiene menos de 8 términos normalizados o si, en minúsculas y sin espacios iniciales, empieza por «y », «¿y », «pero », «entonces», «¿y si» o «y si ».
- **FR-008**: Si la regla de seguimiento está activa, hay pregunta anterior y la actual es un seguimiento, el texto de búsqueda MUST ser la pregunta anterior, un espacio y la actual, para las dos búsquedas. En otro caso, solo la actual.
- **FR-009**: La pregunta anterior MUST aportarla quien llama. La recuperación no guarda conversaciones.

**Selección de contexto**

- **FR-010**: La selección MUST recorrer los candidatos en el orden de la fusión. Acepta un fragmento si su documento no tiene ya el máximo de fragmentos elegidos y si su estimación de tokens cabe en el presupuesto restante. Un fragmento rechazado no detiene el recorrido.
- **FR-011**: Si los parámetros piden vecinos para los *n* primeros, MUST añadir el fragmento siguiente de cada uno si cabe en el presupuesto y en el máximo por documento, uniéndolo si ya estaba elegido. Con D-034 *n* = 0, pero el comportamiento MUST existir y probarse.
- **FR-012**: El recorrido MUST parar al llegar al máximo de bloques más los vecinos previstos.
- **FR-013**: Los elegidos MUST ordenarse por documento y posición. Los consecutivos de un mismo documento se unen en un bloque sin el párrafo repetido por el troceado, y sus tokens se suman. Después se conserva como mucho el máximo de bloques.
- **FR-014**: Cada bloque MUST incluir:
  - el identificador del documento y su nombre;
  - la ruta de encabezados y la página del primer fragmento;
  - el primer y el último ordinal;
  - los identificadores de los fragmentos que cubre;
  - el texto original, sin cabecera contextual;
  - sus tokens estimados.
- **FR-015**: Los bloques MUST contener solo fragmentos que, al cargarlos, sigan perteneciendo a documentos indexados e incluidos.

**Resultados y estados**

- **FR-016**: La recuperación MUST devolver un resultado tipado, sin lanzar errores a quien llama:
  - **bloques:** al menos uno;
  - **sin resultados:** ningún candidato;
  - **pregunta vacía**;
  - **índice no disponible:** con el motivo de compatibilidad;
  - **modelos no listos**;
  - **fallo del embedding**.
- **FR-017**: Con el índice no compatible (metadatos distintos de modelo, dimensión, prefijos o normalizador, o esquema no admitido), MUST devolver «índice no disponible» sin cargar el motor ni buscar.
- **FR-018**: Sin el modelo de embeddings verificado y preparado (regla de F03), MUST devolver «modelos no listos» sin cargar el motor.
- **FR-019**: Cada resultado MUST llevar el texto de búsqueda, si fue un seguimiento y las latencias de embedding, búsqueda vectorial, búsqueda léxica, selección y total. Para el diagnóstico, también la puntuación de fusión y los rangos de cada fragmento elegido.

**Concurrencia y memoria**

- **FR-020**: Una recuperación durante una tanda de indexación MUST terminar sin esperar al final de la tanda.
- **FR-021**: El motor de consulta MUST cargarse bajo demanda y liberarse con la presión de memoria o con la app oculta, como el resto de motores. MUST NOT haber dos motores en GPU a la vez (constitución IV).
- **FR-022**: Las recuperaciones simultáneas MUST ser seguras: cada resultado corresponde a su pregunta.

**Diagnóstico (solo depuración)**

- **FR-023**: La app de depuración MUST ofrecer una herramienta de diagnóstico de búsqueda con:
  - pregunta y pregunta anterior opcional;
  - el texto de búsqueda y si fue un seguimiento;
  - los bloques con su origen y sus puntuaciones;
  - las latencias del FR-019;
  - el estado cuando no hay búsqueda.
- **FR-024**: La build de release MUST NOT contener el diagnóstico ni su acceso.

**Medición y paridad**

- **FR-025**: Una prueba en el teléfono de referencia MUST medir p50 y p95 de embedding, búsqueda vectorial, búsqueda léxica y total con 10.000 fragmentos, con y sin fragmentos ocultos, y la primera búsqueda con el motor sin cargar.
- **FR-026**: Si el p95 de la búsqueda vectorial supera 20 ms, MUST registrarse en DECISIONS la decisión sobre INT8 para F14 (la implementación es de F14).
- **FR-027**: Una prueba instrumentada en el teléfono de referencia, lanzada bajo demanda, MUST comparar el Recall@bloques del teléfono con el del escritorio sobre la misma muestra fija de 50 preguntas del conjunto de evaluación. Indexa el corpus de evaluación en un índice temporal con los títulos del corpus, cuenta como acierto el bloque que contiene la evidencia literal (con la regla del escritorio: si ningún fragmento la contiene entera, el que contiene su frase más larga) e informa aparte de las preguntas cuya evidencia no se extrae en el móvil. MUST NOT tocar la biblioteca del usuario.
- **FR-028**: La revisión del orden dentro del presupuesto (D-035) MUST evaluarse en el harness de escritorio y documentarse con su decisión.

**Privacidad**

- **FR-029**: La recuperación y el diagnóstico MUST NOT usar la red.
- **FR-030**: Los registros MUST NOT contener el texto de la pregunta, el texto de búsqueda ni el de los fragmentos. Solo identificadores, recuentos y tiempos.

### Key Entities *(include if feature involves data)*

- **Petición de recuperación:** la pregunta actual y, opcionalmente, la anterior.
- **Texto de búsqueda:** la pregunta actual, o la anterior más la actual si es un seguimiento.
- **Candidato:** un fragmento con su rango y distancia en la búsqueda vectorial, su rango en la léxica (si aparece en ellas) y su puntuación de fusión.
- **Bloque de contexto:** uno o varios fragmentos consecutivos de un documento, con:
  - documento, nombre, ruta de encabezados y página;
  - primer y último ordinal;
  - identificadores de fragmentos;
  - texto y tokens.
- **Resultado de recuperación:** bloques o un estado sin bloques (sin resultados, pregunta vacía, índice no disponible, modelos no listos, fallo del embedding), con el texto de búsqueda, la marca de seguimiento y las latencias.
- **Parámetros de recuperación:** los de D-034, compartidos con el escritorio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Las pruebas de la fusión, de la regla de seguimiento y de la selección reproducen el 100 % de los casos de las pruebas del escritorio con el mismo resultado, además de los suyos propios:
  - fusión: cálculo a mano, empates, regla de seguimiento y texto de búsqueda;
  - selección: máximo por documento, vecinos y fusión, párrafo repetido, presupuesto y límite, orden.
- **SC-002**: En el 100 % de las pruebas instrumentadas con documentos en todos los estados, ningún bloque contiene fragmentos de documentos excluidos, no indexados del todo o sombra.
- **SC-003**: En el teléfono de referencia, con 10.000 fragmentos y el motor de consulta cargado, el p95 del total de una recuperación (embedding, búsquedas, fusión y selección) es ≤ 200 ms, y el del embedding de consulta < 150 ms (constitución IV).
- **SC-004**: La latencia de la búsqueda vectorial queda medida (p50 y p95) con y sin fragmentos ocultos. Si su p95 supera 20 ms, la decisión sobre INT8 queda registrada para F14.
- **SC-005**: El Recall@bloques del teléfono sobre la muestra de 50 preguntas queda a ±5 puntos del de escritorio sobre la misma muestra, sin contar las preguntas cuya evidencia no se extrae en el móvil, que se informan aparte.
- **SC-006**: Con el índice no compatible o sin modelos listos, el 100 % de las recuperaciones devuelve el estado correspondiente sin buscar y sin errores no controlados.
- **SC-007**: Una recuperación durante una tanda de indexación termina con un p95 ≤ 1 s en el teléfono de referencia, sin esperar al final de la tanda.
- **SC-008**: Los registros de las pruebas de recuperación y del diagnóstico contienen 0 apariciones del texto de la pregunta o de los fragmentos.
- **SC-009**: La build de release no contiene el diagnóstico, comprobado por una prueba.
- **SC-010**: La revisión del orden (D-035) queda documentada con sus cifras y su decisión en el informe de evaluación y en DECISIONS.

## Assumptions

Sin marcas `[NEEDS CLARIFICATION]`. Las decisiones de más impacto se confirmaron en clarify (ver Clarifications):

- **Parámetros de contexto** (confirmado en clarify): los de D-034, es decir 4 bloques, sin vecinos, como mucho 3 por documento y 1.719 tokens. La ficha de F10 y la constitución (L95) se corrigen para que remitan a D-034.
- **Métrica de paridad:** el «Recall@6» de la ficha es el Recall@bloques del escritorio con los 4 bloques de D-034 (0,889 sobre las 370 preguntas).
- **Método de paridad** (confirmado en clarify): una prueba instrumentada en el teléfono con los modelos reales, que se lanza bajo demanda:
  - indexa los documentos del corpus de evaluación del repositorio en un índice temporal, nunca en la biblioteca del usuario;
  - usa los títulos del corpus, como el escritorio, porque la app usa el nombre del fichero y eso cambia la cabecera y la búsqueda léxica;
  - marca como relevantes los fragmentos que contienen la evidencia literal de cada pregunta, porque la extracción PDF del móvil mueve los ordinales. Se usa la misma regla que dio la cifra de escritorio: si ningún fragmento contiene la evidencia entera (porque cae entre dos), cuenta el que contiene su frase más larga;
  - usa una muestra fija de 50 preguntas, respondibles y de seguimiento, con la cifra de escritorio sobre esas mismas 50;
  - las preguntas cuya evidencia no se extrae en el móvil se cuentan aparte.
- **Revisión del orden (D-035)** (confirmado en clarify):
  - se evalúan en el escritorio los trigramas como tercera lista de la fusión y una reordenación de los primeros candidatos, sin modelos nuevos ni red;
  - una variante se adopta solo si mejora el Recall@bloques en ≥ 2 puntos sobre las 370 preguntas sin pasar el presupuesto del prompt;
  - si se adopta, F10 la implementa en el móvil y la paridad (SC-005) se mide con ella.
- **Objetivos de latencia** (SC-003, SC-007; confirmados en clarify): el informe y la constitución fijan el embedding de consulta (< 150 ms) y el criterio vectorial (20 ms), pero no el total.
  - 200 ms cubre el embedding medido en F02 (p95 120 ms) y las dos búsquedas medidas en F04.
  - 1 s durante una tanda deja margen para esperar al fragmento en curso.
- **Fallo del embedding:** se devuelve el fallo, sin degradar en silencio a solo búsqueda léxica, para que el chat (F11) lo explique.
- **Cobertura parcial durante la indexación:** la recuperación no la informa. F11 la obtiene del estado de la indexación (F07).
- **El diagnóstico usa la biblioteca real del teléfono:** es una herramienta de depuración. Muestra textos en pantalla, nunca en los registros.
- **Fuera de alcance:**
  - el chat, el prompt y la generación (F11);
  - INT8, índices aproximados y cambio de modelo (F14);
  - el historial (F13);
  - cualquier UI de producto.
- **Dependencias:**
  - F03 (modelos verificados y preparados);
  - F04 (índice, búsquedas y metadatos);
  - F05 (parámetros, harness y conjunto de preguntas);
  - F07 (búsquedas filtradas y motor de embeddings compartido con la ingesta).

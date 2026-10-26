---

description: "Lista de tareas de F05 — Harness offline de evaluación"
---

# Tasks: Harness offline de evaluación (F05)

**Input**: Documentos de diseño en `/specs/005-offline-eval-harness/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **Obligatorios** por la constitución (VI) y por la spec (SC-001 a SC-007). Las pruebas unitarias de cada módulo de `offline/eval/` van **antes** de su implementación.

**Organización**: por historia de usuario de la spec:
- US1 — Índice de evaluación fiel a la app (P1);
- US2 — Conjunto de prueba con relevantes trazables (P1);
- US3 — Métricas y ablaciones de recuperación (P1);
- US4 — Calidad de respuesta de extremo a extremo (P2);
- US5 — Decisiones registradas y propagadas a la app (P2).

**Entorno**:
- Python: siempre `offline/.venv/bin/python -I`, abreviado `$E -I` (quickstart §0).
- Gradle: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Modelos en `$HOME/MicroRAG-models`, enlazados en `offline/models/`.

**Commits**: atómicos, en inglés (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US5).
- Las rutas son relativas a la raíz del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependencias verificadas, verificación temprana del entorno y esqueleto del paquete.

- [X] T001 Actualizar `offline/requirements.txt`:
  - dependencias directas `litert-lm-api==0.18.1`, `sqliteai-vector==1.1.2` y `pypdf==6.20.1` (research R1);
  - todas las transitivas fijadas con `pip freeze` desde un entorno limpio.

  Reinstalar `offline/.venv` desde cero con quickstart §0 y comprobar que las pruebas de F04 (`$E -I -m unittest discover -s offline/tests`) siguen en verde.
- [X] T002 **Verificación temprana** (plan, riesgo 1), con el entorno real y sin añadir código al repositorio (scripts en el scratchpad):
  - `sqliteai-vector` carga con `entrypoint="sqlite3_vector_init"`, `vector_version()` devuelve `1.1.2` y `vector_full_scan` coincide con la fuerza bruta;
  - Gemma 4 E2B genera en la GPU del Mac con un prompt de ≈ 2.000 tokens y el sampler determinista.

  Si algo difiere de research R1/R7, parar y avisar.
- [X] T003 [P] Crear el esqueleto:
  - `offline/eval/__init__.py`;
  - `offline/run_eval.py`, la CLI con los subcomandos de contracts/eval-cli.md, que de momento salen con «not implemented»;
  - las carpetas `offline/corpus/docs/`, `offline/testset/`, `offline/config/` y `offline/reports/`.

  Comprobar que `.gitignore` cubre `offline/build/` y `offline/models/`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: valores esperados de la app, modelos verificados y configuración de ejecución. Lo usan todas las historias.

**⚠️ CRITICAL**: ninguna historia empieza antes de terminar esta fase.

### Tests (escribir primero)

- [X] T004 [P] `offline/tests/test_appconfig.py`: `appconfig` lee del código Kotlin el SHA-256 de EG2 270M y de Gemma 4 E2B, `DIMENSIONS`, los dos prefijos, `SpanishText.VERSION` y `RagIndexConfig.SCHEMA_VERSION`, y `expected_meta()` da las 7 claves de F04 data-model §2.
- [X] T005 [P] `offline/tests/test_runs.py`:
  - la huella de `EvalConfig` es estable entre ejecuciones y cambia con cualquier parámetro, con la huella del índice, con la del conjunto o con la de los modelos;
  - la serialización JSON es canónica.

### Implementación

- [X] T006 [P] `offline/eval/appconfig.py` (research R5).
- [X] T007 [P] `offline/eval/models.py`:
  - rutas de EG2, E2B y E4B en `offline/models/`;
  - verificación del SHA-256 contra `appconfig` (EG2, E2B) o contra research R7 (E4B), con caché por tamaño y fecha de modificación para no recalcular en cada ejecución.
- [X] T008 `offline/eval/runs.py`: `EvalConfig` (data-model §5), la referencia, la huella y las rutas de `offline/build/runs/<huella>/`. Depende de T006.

**Checkpoint**: pruebas Python en verde.

---

## Phase 3: User Story 1 — Índice de evaluación fiel a la app (Priority: P1) 🎯 MVP

**Goal**: corpus público versionado e índices de evaluación reproducibles con los mismos artefactos que la app.

**Independent Test**: construir `ref` dos veces y obtener el mismo resultado; `rag_meta` igual al que espera la app.

### Tests (escribir primero)

- [X] T009 [P] [US1] `offline/tests/test_corpus.py` y `offline/tests/test_extract.py`:
  - corpus (FR-020): cada entrada del manifest tiene todos sus campos y una licencia permitida; una huella distinta hace fallar `fetch-corpus`; `SOURCES.md` se genera desde el manifest;
  - Markdown: los encabezados `#`, `##` y `###` forman la ruta y los párrafos se separan por línea en blanco;
  - TXT: sin ruta;
  - PDF: un PDF pequeño generado en la prueba con `pypdf` da páginas y párrafos con la heurística de research R3;
  - BOE: un XML mínimo con la forma de la API pasa a Markdown con títulos, capítulos y artículos; usa la última versión de cada bloque y omite `nota_pie`.
- [X] T010 [P] [US1] `offline/tests/test_chunker.py`. Port del `Chunker` del informe:
  - un cambio de ruta abre fragmento;
  - el objetivo y el máximo se respetan;
  - el solape del último párrafo solo se aplica si mide menos de `target/3`;
  - un párrafo largo se divide por frases;
  - los ordinales son consecutivos;
  - `start` y `end` localizan el contenido en el texto extraído;
  - es determinista.
- [X] T011 [P] [US1] `offline/tests/test_index.py`, con un corpus de 3 documentos sintéticos y un embedder simulado (sin modelo):
  - el esquema es el de `schema.sql`;
  - `rag_meta` es igual a `expected_meta()`;
  - `title_norm` y `body_norm` se calculan como en la app;
  - `eval_full_embeddings` tiene 768 componentes;
  - las variantes `dim*` reescriben `embedding` con truncado y L2;
  - las variantes `fts_*` cambian el FTS y la consulta;
  - dos construcciones son idénticas byte a byte en `chunks` y `eval_full_embeddings`.

### Implementación

- [X] T012 [US1] **Selección del corpus** (research R2, D-032): elegir entre 25 y 40 documentos en español de BOE (legislación consolidada de dominios variados), la Agencia Tributaria (guías en PDF con texto) y Wikilibros (manuales). Por cada documento:
  - comprobar el aviso legal;
  - anotar en `offline/corpus/manifest.json` el título, el formato, el tipo, la URL, el editor, la licencia, la atribución, la fecha del original y las transformaciones.

  Objetivo: 1.500–3.000 fragmentos de 350 tokens y un corpus de 30 MB como mucho.
- [X] T013 [P] [US1] `offline/eval/boe.py`: XML consolidado del BOE → Markdown (research R3).
- [X] T014 [P] [US1] `offline/eval/extract.py`: MD, TXT y PDF → `RawBlock` (research R3).
- [X] T015 [P] [US1] `offline/eval/chunker.py`:
  - port literal del `Chunker` del informe, que además devuelve `start` y `end`;
  - `chunk_params(tokens, chars_per_token)` según research R4.
- [X] T016 [US1] `offline/eval/corpus.py` y los comandos `fetch-corpus` y `sources`:
  - descarga verificada por huella;
  - conversión de los documentos del BOE;
  - `SOURCES.md` generado con atribuciones y licencias.

  Ejecutarlos y versionar `offline/corpus/`. Depende de T012 y T013.
- [X] T017 [US1] `offline/eval/fts_variants.py`:
  - `fts_raw`: minúsculas sin quitar nada;
  - `fts_stop`: palabras vacías sin stemmer.

  Son solo de evaluación; `normalizer.py` v1 no cambia.
- [X] T018 [US1] `offline/eval/index.py` y el comando `build <variante>` (data-model §3, contracts/eval-cli.md E1–E4):
  - `ref`, `chunk250`, `chunk500` y `nohdr` desde el corpus;
  - `dim128`, `dim512`, `dim768`, `fts_raw` y `fts_stop` derivadas de `ref`;
  - medición de `chars_per_token` con `Engine.tokenize` de E2B;
  - informe de construcción (E4).
- [X] T019 [US1] Construir `ref` dos veces y comprobar que es idéntico (SC-001). Comprobar `rag_meta` frente a `expected_meta()` (SC-002) y que las pruebas de los golden de F04 siguen pasando. Anotar los recuentos para el informe.

**Checkpoint**: índice de referencia reproducible.

---

## Phase 4: User Story 2 — Conjunto de prueba con relevantes trazables (Priority: P1)

**Goal**: ≈ 300 preguntas con respuesta, 50 sin respuesta y 40 de seguimiento, con evidencia literal y al menos 100 revisadas por el usuario.

**Independent Test**: `validate-testset` sin errores y con los recuentos cumplidos.

### Tests (escribir primero)

- [X] T020 [P] [US2] `offline/tests/test_testset.py`:
  - cada regla T1–T9 de contracts/testset.md con un caso válido y uno inválido;
  - derivación de relevantes: evidencia completa en un fragmento, evidencia partida (frase más larga) y cambio de variante;
  - `review-sheet` (≥ 100, estratificado, con semilla, sin repetir);
  - `review-apply` (`ok`, `descartar`, `corregir`).

### Implementación

- [X] T021 [US2] `offline/eval/testset.py` y los comandos `validate-testset`, `review-sheet` y `review-apply`.
- [X] T022 [US2] **Muestreo:** comando interno que elige ≈ 300 fragmentos de `ref` estratificados por documento y tipo de contenido, con semilla fija, y los vuelca para la redacción (en `offline/build/`, sin versionar).
- [X] T023 [US2] **Redacción del conjunto** (D-032), en `offline/testset/questions.jsonl`:
  - por fragmento muestreado, una pregunta realista sin frases copiadas, con la respuesta esperada, de 1 a 4 datos clave y la evidencia literal;
  - 50 preguntas sin respuesta en el corpus, del mismo dominio, comprobadas con una búsqueda;
  - 40 seguimientos («¿y…?») encadenados a preguntas con respuesta, con su propia evidencia.
- [X] T024 [US2] `validate-testset` hasta 0 errores. Generar `offline/testset/review.csv` con `review-sheet` y **pedir la revisión al usuario**, que es un bloqueo externo. Mientras tanto se avanza en US3.
- [X] T025 [US2] Aplicar la revisión con `review-apply`, rehacer las preguntas marcadas como «corregir» y repetir hasta tener ≥ 100 revisadas. Volver a validar (SC-003).

**Checkpoint**: conjunto de prueba validado y revisado.

---

## Phase 5: User Story 3 — Métricas y ablaciones de recuperación (Priority: P1)

**Goal**: métricas de recuperación de la referencia y de cada ablación frente a los umbrales del informe.

**Independent Test**: `retrieve` sobre `ref` da Recall@20, MRR (vector, FTS e híbrido), nDCG@10 y Recall@bloques.

### Tests (escribir primero)

- [ ] T026 [P] [US3] `offline/tests/test_retrieval.py`:
  - RRF con k = 60 y posiciones desde 1, con un caso calculado a mano;
  - la regla de seguimiento con los conectores del informe y el umbral de 8 tokens;
  - modos solo vector, solo FTS e híbrido;
  - el SQL es el de E5, sobre un índice pequeño en memoria con la extensión.
- [ ] T027 [P] [US3] `offline/tests/test_selector.py`. `ContextSelector` del informe:
  - como mucho 3 por documento;
  - vecino `ordinal + 1` para los 2 mejores;
  - fusión de consecutivos sin el párrafo solapado;
  - presupuesto de tokens;
  - límite de bloques;
  - orden final por documento y ordinal.
- [ ] T028 [P] [US3] `offline/tests/test_metrics.py`: Recall@k, MRR, nDCG@10 y Recall@bloques con casos calculados a mano (research R10). Las preguntas sin respuesta se excluyen.

### Implementación

- [ ] T029 [P] [US3] `offline/eval/retrieval.py` (research R6, E5).
- [ ] T030 [P] [US3] `offline/eval/selector.py` (research R6).
- [ ] T031 [P] [US3] `offline/eval/metrics.py` (research R10).
- [ ] T032 [US3] Comando `retrieve <config>`: escribe `retrieval.jsonl` y `metrics.json` en la carpeta de su huella. Medir su tiempo sobre `ref` (SC-006). Depende de T029–T031 y T025.
- [ ] T033 [US3] Comando `ablations`, parte de recuperación. Ejecutar:
  - modo: vector, FTS e híbrido;
  - normalización: `fts_raw`, `fts_stop` y v1;
  - dimensión: 128, 256, 512 y 768;
  - cabecera: con y sin;
  - tamaño: 250, 350 y 500;
  - regla de seguimiento: con y sin;
  - bloques: 4, 6 y 8, con y sin vecinos (Recall@bloques), cada uno con `budget_tokens` ajustado a un prompt de 2.000 tokens (research R6).

  Anotar la menor dimensión dentro del 1 % de nDCG@10 de 768.

**Checkpoint**: parámetros de recuperación con cifras.

---

## Phase 6: User Story 4 — Calidad de respuesta de extremo a extremo (Priority: P2)

**Goal**: respuestas correctas, negativas correctas y citas correctas con E2B, por número de bloques y vecinos, y comparación con E4B.

**Independent Test**: `generate` sobre la configuración elegida da los tres porcentajes y el tamaño del prompt.

### Tests (escribir primero)

- [ ] T034 [P] [US4] `offline/tests/test_prompt.py`: la plantilla del informe al carácter (reglas, fuentes numeradas «documento › ruta (p. N)», pregunta), y el bloque de conversación previa en los seguimientos, truncado a 300 y 500 caracteres.
- [ ] T035 [P] [US4] `offline/tests/test_generate.py`, con un motor simulado: una conversación por pregunta, sampler determinista, `max_output_tokens`, reanudación sin regenerar lo guardado (FR-016, E7) y huella de configuración en la ruta.

  `offline/tests/test_judge.py` (research R9):
  - normalización de datos clave, incluidas las cifras con separadores;
  - negativa exacta frente a negativa seguida de texto;
  - las formas de cita `[n]`, `[n, m]` y `[n][m]`;
  - cita a un bloque inexistente;
  - respuesta sin citas;
  - métrica de citas.

### Implementación

- [ ] T036 [P] [US4] `offline/eval/prompt.py` (research R6).
- [ ] T037 [P] [US4] `offline/eval/judge.py` (research R9).
- [ ] T038 [US4] `offline/eval/generate.py` y el comando `generate <config>` (E6, E7):
  - Gemma 4 en la GPU del Mac;
  - una conversación por pregunta;
  - tamaño real del prompt con `Engine.tokenize`;
  - `answers.jsonl` incremental y reanudable;
  - juicio automático;
  - muestra de 50 repetida en CPU.
- [ ] T039 [US4] Generación con E2B para 4, 6 y 8 bloques, con y sin vecinos, sobre la recuperación elegida en US3 (spec FR-012). Registrar la mediana y el p95 de los tokens de prompt de cada variante y descartar las que superen 2.000 en el p95 (SC-005). Elegir la configuración.
- [ ] T040 [US4] Descargar `gemma-4-E4B-it.litertlm` de la revisión fijada en research R7 a `$HOME/MicroRAG-models`, verificar su SHA-256 y enlazarlo. Generar el conjunto con E4B en la configuración elegida.
- [ ] T041 [US4] **Juicio manual** de la configuración elegida con E2B (D-032):
  - juzgo cada respuesta (correcta, incorrecta o parcial) y cada cita (sostiene o no lo afirmado) en `offline/reports/manual-judgement.csv`;
  - preparo una muestra de 50 para el usuario en la misma hoja y **pido su revisión**;
  - calculo el acuerdo entre las reglas y el juicio manual.

**Checkpoint**: calidad de respuesta medida.

---

## Phase 7: User Story 5 — Decisiones registradas y propagadas a la app (Priority: P2)

**Goal**: informe, decisiones en DECISIONS y constantes compartidas con la app, comprobadas por pruebas.

**Independent Test**: `RagParametersTest` en verde con el JSON decidido; cada parámetro con su entrada en DECISIONS.

### Tests (escribir primero)

- [ ] T042 [P] [US5] `app/src/test/java/com/lisdatasolutions/microrag/domain/rag/RagParametersTest.kt` (P2): lee `../offline/config/rag_parameters.json` y exige igualdad campo a campo con `RagParameters`, más `dims == EmbeddingMath.DIMENSIONS` y `normalizer_version == SpanishText.VERSION`.

### Implementación

- [ ] T043 [US5] `offline/eval/report.py` y el comando `report` (E8). Genera `offline/reports/eval-report.md` con:
  - el corpus (documentos, tipos y licencias);
  - el conjunto de prueba;
  - la referencia y todas las ablaciones frente a los umbrales;
  - los tiempos;
  - el acuerdo entre reglas y juicio manual;
  - la comparación de E2B y E4B;
  - la configuración elegida.
- [ ] T044 [US5] **Umbrales** (FR-022):
  - si la configuración elegida cumple SC-004, sigue con T045;
  - si no, probar las palancas (trigramas como tercera lista de RRF, troceado, cabecera, candidatos, plantilla), documentar su efecto en el informe y, si aun así falta algún umbral, **parar y consultar al usuario** con las cifras y las opciones.
- [ ] T045 [US5] Registrar en `docs/roadmap/DECISIONS.md` una entrada por parámetro decidido (P1), con la cifra y las alternativas descartadas. Crear `offline/config/rag_parameters.json` (contracts/rag-parameters.md).
- [ ] T046 [US5] Crear `app/src/main/java/com/lisdatasolutions/microrag/domain/rag/RagParameters.kt` con las constantes decididas y su KDoc (P2). Si `dims` ≠ 256, aplicar P3:
  - cambiar `EmbeddingMath.DIMENSIONS`;
  - regenerar el golden de embeddings;
  - ejecutar `EmbeddingGoldenTest` en el teléfono.

  Si `contextual_header` es `false`, anotar P4 para F07.
- [ ] T047 [US5] Comando `chunker-golden` → `offline/golden/chunker_golden.jsonl`, con los parámetros decididos, casos sintéticos (fronteras, solape, párrafos largos) y 20 documentos reales del corpus recortados. Añadir una prueba Python que lo regenera y compara.

**Checkpoint**: parámetros decididos y propagados.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T048 [P] Ampliar `offline/README.md` con la sección «Evaluación»: comandos, corpus y licencias, revisión del conjunto, reanudación y cómo repetir el informe.
- [ ] T049 Puertas de calidad:
  - pruebas Python (`$E -I -m unittest discover -s offline/tests`);
  - `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest`.

  Si cambió `dims`, también la suite instrumentada en el teléfono. Todo en verde.
- [ ] T050 Documentación de cierre:
  - `specs/005-offline-eval-harness/report.md`, con resumen, cifras, decisiones, diferencias frente al plan y pendientes;
  - estado de F05 en `docs/roadmap/FEATURES.md`;
  - INVENTARIO si procede;
  - tareas marcadas `[X]`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias. **T002 decide si se sigue.**
- **Foundational (fase 2)**: depende de la fase 1. Bloquea todas las historias.
- **US1 (fase 3)**: depende de la fase 2.
- **US2 (fase 4)**: depende de US1, porque necesita el índice `ref` para muestrear y derivar relevantes.
- **US3 (fase 5)**: su código depende solo de la fase 2. Sus ejecuciones (T032, T033) necesitan US1 y US2.
- **US4 (fase 6)**: depende de US3 (configuración de recuperación) y del conjunto revisado.
- **US5 (fase 7)**: depende de US3 y US4.
- **Polish (fase 8)**: depende de todas.

### Bloqueos externos

- **T024 y T025:** revisión del conjunto por el usuario.
- **T041:** revisión de la muestra del juicio manual.
- **T044:** consulta si no se alcanzan los umbrales.

Mientras se espera, se avanza en el código de las historias siguientes.

### Parallel Opportunities

- **Fase 2:** T004–T005, y después T006–T007.
- **US1:** T009–T011 (tests); T013, T014 y T015 en paralelo; T012 (selección del corpus) en paralelo con el código.
- **US3 ∥ US2:** T026–T031 se pueden escribir mientras se redacta y revisa el conjunto.
- **US4:** T034–T037 en paralelo.

---

## Implementation Strategy

### MVP primero (US1)

1. Fases 1 y 2, con la verificación temprana T002.
2. US1: corpus versionado e índice `ref` reproducible.

### Entrega incremental

1. **US2:** conjunto de prueba redactado; pedir la revisión cuanto antes.
2. **US3:** métricas y ablaciones de recuperación.
3. **US4:** generación, juicio, E4B y juicio manual.
4. **US5:** informe, decisiones y propagación a la app.
5. **Polish:** README, puertas y cierre.

## Notes

- **Red:** solo `fetch-corpus` (T016) y la descarga de E4B (T040), en el Mac. La app no cambia.
- **Licencias:** ningún documento entra sin aviso legal comprobado. Las atribuciones van en `SOURCES.md`.
- **Parada:** tras `/speckit-analyze` se espera la revisión del usuario antes de implementar.

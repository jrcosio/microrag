# Research — F05 Harness offline de evaluación

**Fecha:** 2026-10-26.
- Paquetes: verificados en PyPI.
- API: comprobada en los paquetes instalados.
- Licencias: comprobadas en los avisos legales de cada fuente.
- Experimentos: en el Mac del proyecto (M5 Pro, 48 GB, macOS ARM64, Python 3.12).

## R1 — Dependencias de Python

| Paquete | Versión | Verificación | Uso |
|---|---|---|---|
| `litert-lm-api` | **0.18.1** | Ya fijada en F04 (D-012). Expone `EmbeddingEngine`, `Engine`, `Conversation`, `Engine.tokenize` y `get_benchmark_info` | Embeddings, generación con Gemma 4 y recuento de tokens |
| `sqliteai-vector` | **1.1.2** | PyPI (2026-09-11), Apache-2.0, rueda `macosx_11_0_arm64` con `sqlite_vector/binaries/vector.dylib`. Es la misma versión que el AAR de Android | La misma búsqueda vectorial que la app (`vector_init`, `vector_as_f32`, `vector_full_scan`) |
| `pypdf` | **6.20.1** | PyPI (2026-10-12), BSD-3-Clause, sin dependencias en Python ≥ 3.11 | Extracción de texto de los PDF del corpus |

**Comprobado con `sqliteai-vector`** (experimento del 2026-10-26):
- Se carga con `sqlite3.Connection.load_extension(ruta, entrypoint="sqlite3_vector_init")`, tras `enable_load_extension(True)`.
- `vector_version()` devuelve `1.1.2`.
- Crea `_sqliteai_vector` al cargarse, igual que en Android (F04 research R3).
- `vector_full_scan` da exactamente las mismas distancias y el mismo orden que un coseno por fuerza bruta sobre 200 vectores unitarios.

**El SQLite del Python 3.12** del proyecto es la versión 3.50.4, con FTS5 y carga de extensiones.

**Versión de `pypdf`:** la última es la 6.21.2 (2026-10-21), pero la línea 6.21 tiene menos de dos semanas. Se fija la 6.20.1, con el mismo criterio que en F03 (DataStore 1.2.1 frente a una 1.3.0 recién publicada).

**Descartados:**
- `pdfminer.six` 20260107 (MIT): mejor análisis de maquetación, pero arrastra `cryptography`, `charset-normalizer` y una cadena de dependencias binarias.
- `numpy`: no hace falta. La búsqueda vectorial va con SQLite-Vector y las métricas son sumas pequeñas. Se mantiene el criterio de F04 de dependencias mínimas.
- `sqlite-vector` 0.0.1a6: es otro proyecto, de 2023.

**Entorno:** `requirements.txt` fija las tres dependencias directas y las transitivas, con `pip freeze`. La instalación es la de F04: `python3.12 -m venv` y `pip install -r`.

## R2 — Corpus público (D-032)

**Criterio de selección:**
- documentos en español;
- licencia que permita copiar, redistribuir y transformar con atribución;
- tipos parecidos a lo que importarán los usuarios: normativa, guías y manuales;
- los formatos del MVP: PDF con texto, Markdown y TXT.

**Tamaño objetivo:** entre 25 y 40 documentos, que den entre 1.500 y 3.000 fragmentos de unos 350 tokens. Es suficiente para muestrear 300 preguntas por documento y tipo, y cabe en el repositorio (≤ 30 MB).

| Fuente | Formato | Condiciones verificadas |
|---|---|---|
| **BOE, legislación consolidada** (API de datos abiertos: `/datosabiertos/api/legislacion-consolidada/id/{id}/texto`) | XML estructurado → Markdown | Aviso legal del BOE: reutilización comercial y no comercial; copiar, distribuir y crear obras derivadas. Obligaciones: citar la fuente («Fuente de los datos: Agencia Estatal Boletín Oficial del Estado», o «Basado en datos de…» si hay transformación), identificar las modificaciones, indicar que el texto consolidado tiene carácter informativo, mantener la fecha de actualización y no sugerir patrocinio |
| **Agencia Tributaria** (guías y folletos en PDF) | PDF con texto | Reutilización en la modalidad básica del RD 1495/2011, sin condiciones específicas. Obligaciones: citar la fuente y el organismo, no desvirtuar el contenido, indicar la fecha de la última actualización y no alterar sus metadatos |
| **Wikilibros en español** (manuales) | Markdown o TXT | CC BY-SA 4.0: atribución y compartir igual. El fichero versionado mantiene la licencia y la autoría (enlace al historial) |

**Comprobado:** la API del BOE devuelve, por ejemplo para la LAU (`BOE-A-1994-26003`), bloques `preambulo`, `encabezado` (títulos y capítulos) y `precepto` (artículos) con sus versiones. El texto vigente es la última versión de cada bloque. Se descartan las notas al pie de modificaciones (`nota_pie`).

**Registro:** `offline/corpus/SOURCES.md` y `manifest.json` guardan, por documento:
- título, organismo, URL y fecha de descarga;
- licencia y frase de atribución;
- fecha de actualización del original;
- huella SHA-256;
- formato y tipo;
- las transformaciones hechas (por ejemplo, «XML del BOE → Markdown; se omiten las notas de modificación»).

**La elección concreta de documentos** es la primera tarea de la implementación. Cada documento se añade solo tras comprobar su aviso legal; un documento con condiciones distintas de las de su fuente no entra.

## R3 — Extracción de texto

Todos los formatos producen la misma estructura que la app (informe §Parsing): bloques `(ruta de encabezados, texto, página)`.

| Formato | Escritorio (F05) | App (F06) |
|---|---|---|
| Markdown | Encabezados `#` → ruta; párrafos separados por línea en blanco | Igual (parser propio) |
| TXT | Sin encabezados; párrafos por línea en blanco | Igual |
| PDF | `pypdf` por página. Se unen las líneas y se cortan párrafos en líneas en blanco o en una línea que acaba en `.`, `:` o `;` seguida de mayúscula o viñeta. Sin ruta de encabezados; con página | Lo decide F06 (G38) |

**La conversión del BOE a Markdown** es una transformación del corpus, no del parser:
- `TÍTULO` y `CAPÍTULO` pasan a ser `#` y `##`;
- los artículos, `###`;
- los párrafos se mantienen;
- las tablas, una fila por línea.

**Limitación aceptada:** el texto de los PDF en el escritorio no será idéntico al que obtenga la app. F06 compara su extracción con la de F05 en los mismos PDF.

## R4 — Troceado y tokens

**Algoritmo:** port literal del `Chunker` del informe en `offline/eval/chunker.py`:
- `targetChars` y `maxChars`;
- solape del último párrafo si es menor que `targetChars / 3`;
- un cambio de ruta abre fragmento nuevo;
- división por frases de los párrafos mayores que `maxChars`.

**Medido** con el tokenizador de Gemma 4 E2B (`Engine.tokenize`) sobre un prompt en español de 9.059 caracteres: 2.005 tokens, es decir **4,52 caracteres por token**. El informe suponía ≈ 4.

**Decisión:**
- **Relación caracteres/token:** se recalcula sobre el corpus al construir (mediana de los fragmentos) y se registra.
- **Tamaño objetivo:** `targetChars = round(tokens × ratio)`, para 250, 350 y 500 tokens.
- **Tamaño máximo:** `maxChars = min(round(targetChars × 10 / 7), maxEntrada)`. El 10/7 reproduce la relación 1.400/2.000 del informe. `maxEntrada` son los caracteres que caben en 512 tokens de entrada del modelo de embeddings, descontando el prefijo y una cabecera de 40 tokens.
- **Variante de 500 tokens:** su objetivo choca con el máximo, así que sus fragmentos quedarán pegados al límite de entrada. Se informa (caso límite de la spec).
- **Estimación de tokens de un fragmento** (`token_estimate`, que usan el presupuesto de contexto y F10): `ceil(caracteres / ratio)`. La relación elegida se decide en F05 y se propaga (R11).

**Limitación:** EmbeddingGemma 2 no expone su tokenizador en Python. El límite de 512 se calcula con el de Gemma 4, que pertenece a la misma familia. Si un fragmento supera el límite, `InputOverflowStrategy` decidiría qué pasa; con el máximo elegido no debería ocurrir, y se comprueba al construir.

**Golden del troceado para F06:** `offline/golden/chunker_golden.jsonl`, con bloques de entrada y fragmentos de salida para los parámetros elegidos. Hace con el troceado lo que el golden del normalizador hizo con `SpanishText`: F06 lo usará para comprobar que su `Chunker` en Kotlin es idéntico.

## R5 — Índice de evaluación

- **Un fichero SQLite por variante de construcción** (tamaño de fragmento × cabecera) en `offline/build/index/`, con:
  - el `schema.sql` de la app sin cambios;
  - `rag_meta` con los valores que espera la app;
  - `source = 'user'`;
  - la misma normalización (`title_norm` = nombre › ruta, `body_norm` = contenido);
  - `vector_as_f32` sobre BLOB float32 LE.
- **Metadatos esperados:** `offline/eval/appconfig.py` los lee del código Kotlin con expresiones regulares (`ModelCatalog`, `EmbeddingMath.DIMENSIONS`, `EmbeddingPrefixes`, `SpanishText.VERSION` y `RagIndexConfig.SCHEMA_VERSION`), como ya hacen las pruebas de F04. Así no pueden divergir sin que falle una prueba.
- **Vectores completos:** tabla aparte, solo de evaluación, `eval_full_embeddings(chunk_id, v768)`, en el mismo fichero y fuera de `schema.sql`.
- **Ablación de dimensión:** copia del índice de referencia en la que se reescribe `embedding` con el truncado a *d* y L2 a partir de `v768`, más `vector_init(dimension=d)`. No se vuelve a embeber.
- **Ablación de normalización léxica:** copia en la que se recalculan `title_norm` y `body_norm` con la variante, con el FTS reconstruido por los triggers. La consulta usa la misma variante.
- **Reproducibilidad:** `build/` está fuera de git, y los índices se reconstruyen con un comando. La construcción registra las huellas de los ficheros de entrada y de los modelos.

## R6 — Recuperación, selección y prompt

Ports literales del informe, que serán la referencia de F10 y F11:
- **`retrieval.py`:**
  - `vector_full_scan` top-50;
  - FTS top-50 con `bm25(chunks_fts, 3.0, 1.0)` y `fts_query` de `normalizer.py`;
  - RRF con k = 60 (posiciones desde 1);
  - `isFollowUp`: menos de 8 tokens normalizados, o empieza por «y », «¿y », «pero », «entonces», «¿y si» o «y si ».
- **`selector.py`:** `ContextSelector`, con `maxBlocks`, `maxPerDoc = 3`, presupuesto de 2.200 tokens, `expandTop = 2` y fusión sin el solape. El orden final es por documento y ordinal.
- **`prompt.py`:** `PromptBuilder` con la plantilla del informe. El seguimiento lleva la pregunta y la respuesta anteriores, truncadas a 300 y 500 caracteres.

El presupuesto de 2.200 tokens del selector usa `token_estimate`. El tamaño real del prompt se mide con `Engine.tokenize` y se compara con ≈ 2.000 (constitución IV).

## R7 — Generación en el escritorio

**Medido en el Mac** (2026-10-26) con Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, D-023), un prompt de 2.005 tokens con la plantilla del informe, `max_num_tokens = 4096` y sampler determinista como el de la app (`top_k = 1`, `top_p = 1`, `temperature = 0`, `seed = 0`):

| Backend | Inicialización | Respuesta (2.ª ejecución) | TTFT | Prefill | Decode |
|---|---|---|---|---|---|
| GPU | 2,4 s | 0,6 s | 0,44 s | 4.628 tok/s | 134 tok/s |
| CPU | 0,8 s | 3,4 s | 2,73 s | 746 tok/s | 33,5 tok/s |

Las dos respuestas son idénticas: «El motor de la bomba hidráulica debe revisarse cada 500 horas de funcionamiento [1].»

**Decisiones:**
- GPU del Mac para la generación: unas 390 preguntas por variante en ≈ 7 minutos.
- Una conversación nueva por pregunta, como en el informe.
- `max_output_tokens = 512`.
- Una muestra de 50 preguntas se repite en CPU para comprobar que el backend no cambia las respuestas, y se informa.

**Gemma 4 E4B** (D-032):
- `litert-community/gemma-4-E4B-it-litert-lm`, revisión `e6a3892a8c22cf87ff99583996149cf3cb32452b`;
- `gemma-4-E4B-it.litertlm`: 3.659.530.240 bytes, SHA-256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`;
- Apache-2.0, sin acceso restringido.

Se descarga una vez a `$HOME/MicroRAG-models` y se verifica antes de usarlo. Es una herramienta de escritorio, no una descarga de la app.

**Reanudación:** cada respuesta se guarda en `build/runs/<huella de la configuración>/answers.jsonl` al obtenerla. Al reanudar se saltan los identificadores ya presentes.

## R8 — Conjunto de prueba (D-032)

**Redacción:** la hace Claude, el asistente de desarrollo, en la implementación:
1. un muestreo estratificado (documento × tipo de contenido) de unos 300 fragmentos del índice de referencia, con semilla fija;
2. por fragmento, una pregunta realista sin frases copiadas, la respuesta esperada, sus datos clave y la evidencia: una frase o un tramo contiguo copiado literalmente del documento;
3. unas 50 preguntas del mismo dominio sin respuesta en el corpus;
4. unos 40 seguimientos («¿y…?») encadenados a preguntas con respuesta, con su propia evidencia.

**Formato:** `offline/testset/questions.jsonl`, versionado. Contrato en `contracts/testset.md`.

**Revisión por el usuario:** `offline/testset/review.csv`, que se abre en una hoja de cálculo, con las columnas `id`, documento, pregunta, respuesta esperada, evidencia, `veredicto` (`ok`, `corregir` o `descartar`) y comentario. Al menos 100 filas elegidas al azar con semilla y estratificadas. Un comando aplica los veredictos al conjunto: las preguntas a corregir las rehace el asistente según el comentario y vuelven a revisión.

**Relevantes:**
- La evidencia se busca en el texto extraído del documento, normalizando solo los espacios.
- Un fragmento es relevante si contiene la evidencia completa.
- Si ninguno la contiene entera, porque la parte la división por frases, son relevantes los que contienen su frase más larga.
- El conjunto guarda los relevantes `(content_hash, ordinal)` de la configuración de referencia, y las demás variantes los derivan.

## R9 — Juicio automático (D-032)

- **Datos clave:** de 1 a 4 por pregunta (cifras, plazos, términos), escritos al redactarla.
  - **Coincidencia:** respuesta y dato se normalizan igual (minúsculas, sin tildes ni puntuación, cifras sin separadores de miles: «1.000» = «1000»).
  - **Respuesta correcta:** contiene todos sus datos clave y no es una negativa.
- **Negativa:** la respuesta, normalizada igual, es la frase fijada. Si empieza por ella y sigue con más texto, es respuesta, no negativa.
- **Citas:**
  - se reconocen `[n]`, `[n, m]` y `[n][m]`;
  - una cita es correcta si su bloque contiene un fragmento relevante de la pregunta;
  - una cita a un número inexistente es incorrecta;
  - una respuesta que no es negativa y no cita nada cuenta como una cita incorrecta.
  - **Métrica:** citas correctas sobre el total de citas, en las preguntas con respuesta.
- **Juicio manual en la configuración elegida:** el asistente juzga cada respuesta (correcta, incorrecta o parcial; cita que sostiene o no la afirmación) y el usuario revisa una muestra de 50 en otra hoja. El informe da el acuerdo entre reglas y juicio manual. Las cifras finales usan el manual (FR-021).

## R10 — Métricas

Se calculan solo sobre las preguntas con respuesta (los seguimientos con la regla aplicada):

| Métrica | Definición |
|---|---|
| Recall@k | Media por pregunta de \|relevantes ∩ top-k\| / \|relevantes\| |
| MRR | Media de 1/posición del primer relevante en la lista (0 si no aparece entre los candidatos) |
| nDCG@10 | Relevancia binaria, ganancia `1/log2(pos + 1)`, normalizada por el ideal |
| Recall@bloques | Proporción de preguntas en las que algún bloque seleccionado contiene un relevante (los bloques cubren rangos de ordinales). Con 6 bloques es el «Recall@6» del informe |
| Respuestas correctas | Correctas / preguntas con respuesta |
| Negativa correcta | Negativas / preguntas sin respuesta |
| Citas correctas | R9 |
| Prompt | Tokens reales (mediana y p95) |

**Comparación de dimensiones:** la menor *d* con nDCG@10(*d*) ≥ 0,99 × nDCG@10(768).

## R11 — Decisiones y propagación a la app

**Fuente única:** `offline/config/rag_parameters.json`, versionado, con:
- `dims`;
- `chunk_target_chars`, `chunk_max_chars` y `chars_per_token`;
- `contextual_header`;
- `normalizer_version`;
- `candidates`, `rrf_k`, `follow_up_rule`;
- `max_blocks`, `max_per_document`, `budget_tokens`, `expand_top`;
- `llm_model`, `max_output_tokens`.

**En Kotlin:** nuevo `domain/rag/RagParameters.kt`, un objeto con las mismas constantes para F06, F10 y F11. `RagParametersTest` (JVM) lee el JSON y exige igualdad, igual que las pruebas de golden de F04. `EmbeddingMath.DIMENSIONS` y la cabecera de `EmbeddingPrefixes` se comprueban contra el JSON en la misma prueba.

**Si la dimensión elegida no es 256:**
- se cambia `EmbeddingMath.DIMENSIONS`;
- se regenera `embedding_golden.jsonl`;
- se vuelve a ejecutar `EmbeddingGoldenTest` en el teléfono;
- `RagIndexConfig` cambia de espacio vectorial, así que un índice anterior quedaría incompatible. Todavía no hay índices reales: F07 es la primera feature que indexa.

## R12 — Tiempos previstos en el Mac

| Paso | Estimación | Base |
|---|---|---|
| Construir un índice de referencia (≈ 2.000 fragmentos) | 1–2 min | ≈ 40 ms por embedding en CPU (F04: 50 textos en ≈ 2 s con la inicialización) |
| 6 variantes de construcción (3 tamaños × cabecera) | ≈ 10 min | — |
| Dimensión y normalización (sin embeber) | < 1 min por variante | — |
| Evaluación de recuperación por variante | < 1 min | SQL local; SC-006 pide < 10 min |
| Generación por variante (≈ 390 preguntas, GPU) | ≈ 7 min | R7 |
| Generación con E4B | ≈ 15 min (estimado ×2) | Se mide |

## R13 — Riesgos

1. **Extracción de PDF pobre** (columnas, tablas): se descartan los PDF ilegibles y se prefiere Markdown del BOE para la estructura. F06 vuelve a medir con su extractor.
2. **Preguntas demasiado fáciles** (las redacta quien ve el fragmento): se prohíben las frases copiadas, se incluyen preguntas con otras palabras y la revisión del usuario las filtra. El informe sigue el mismo método («generar con un modelo grande»).
3. **Juicio por datos clave:** puede ser demasiado estricto o laxo. Se calibra con el juicio manual en la configuración elegida (R9).
4. **Umbrales no alcanzados:** palancas de FR-022 y consulta al usuario (D-032).
5. **Licencias:** cada documento se registra con su atribución y modificaciones. Una duda de licencia lo excluye.
6. **Tamaño del repositorio:** el corpus no debería pasar de 30 MB. Los índices y las ejecuciones van en `build/`, fuera de git.
